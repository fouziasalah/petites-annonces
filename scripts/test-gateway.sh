#!/usr/bin/env bash
# -----------------------------------------------------------------------------
# Tests de bout en bout de la gateway : critères GW-01..GW-10 et RL-01..RL-05.
# Prérequis : la stack tourne (docker compose up -d --wait).
#
# Tokens utilisés :
#   - VRAIS tokens Keycloak, obtenus par émulation du flux PKCE (scripts/get-token.sh) ;
#   - tokens FORGÉS avec openssl pour les cas d'attaque :
#       expiré    → signé avec la clé du realm (signature valide, seul exp est faux) ;
#       étranger  → autre émetteur, signé avec une clé jetable ;
#       usurpé    → bon émetteur mais signé avec une clé jetable ;
#       altéré    → vrai token dont la charge utile a été modifiée.
#
# Le quota est de 3 appels/minute par utilisateur ET la fenêtre est alignée sur
# la minute de l'horloge : le script attend le début d'une minute avant la partie
# rate limiting. Les appels rejetés par jwt (401) ne sont pas décomptés.
#
# Usage : scripts/test-gateway.sh        (durée ≈ 1 à 2 min)
# -----------------------------------------------------------------------------
set -uo pipefail
source "$(dirname "$0")/lib/common.sh"
load_env "${ENV_FILE:-$ROOT_DIR/.env}"

API="$GATEWAY_PUBLIC_URL"
ORIGIN="$FRONT_PUBLIC_URL"
ISSUER="$KC_PUBLIC_URL/realms/annonces"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

passed=0
failed=0
check() { # check <id> <description> <condition-évaluée>
    if eval "$3"; then
        printf '  \e[32mPASS\e[0m %-6s %s\n' "$1" "$2"; passed=$((passed + 1))
    else
        printf '  \e[31mFAIL\e[0m %-6s %s\n' "$1" "$2"; failed=$((failed + 1))
        printf '         statut=%s\n' "$STATUS"; sed 's/^/         /' "$work/headers" | head -15
    fi
}

# call <méthode> <chemin> [arguments curl…] → STATUS, $work/body, $work/headers
call() {
    local method="$1" path="$2"
    shift 2
    STATUS="$(curl -s -o "$work/body" -D "$work/headers" -w '%{http_code}' -X "$method" \
        -H "Origin: $ORIGIN" "$@" "$API$path")"
}
header() { grep -i "^$1:" "$work/headers" | head -1 | cut -d' ' -f2- | tr -d '\r'; }
body_field() { json_string "$1" < "$work/body"; }
backend_hits() { docker compose -f "$ROOT_DIR/docker-compose.yml" logs backend 2>/dev/null | grep -c "| X_USER_ID="; }

# --- JWT forgés --------------------------------------------------------------
pem_from_body() { # pem_from_body <type> <base64>
    echo "-----BEGIN $1-----"; printf '%s\n' "$2" | fold -w 64; echo "-----END $1-----"
}
sign_jwt() { # sign_jwt <payload-json> <clé-privée-pem>
    local header payload signature
    header="$(printf '%s' '{"alg":"RS256","typ":"JWT"}' | b64url_encode)"
    payload="$(printf '%s' "$1" | b64url_encode)"
    signature="$(printf '%s' "$header.$payload" | openssl dgst -sha256 -sign "$2" -binary | b64url_encode)"
    printf '%s.%s.%s' "$header" "$payload" "$signature"
}

pem_from_body "PRIVATE KEY" "$REALM_RSA_PRIVATE_KEY" > "$work/realm.pem"
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$work/foreign.pem" 2>/dev/null
now="$(date +%s)"
claims() { # claims <iss> <exp>
    printf '{"iss":"%s","sub":"pirate","azp":"annonces-front","exp":%s,"iat":%s,"realm_access":{"roles":["ADMIN"]}}' \
        "$1" "$2" "$((now - 10))"
}

echo "== Obtention des tokens (flux Authorization Code + PKCE émulé)"
ALICE="$(bash "$ROOT_DIR/scripts/get-token.sh" alice)" || exit 1
BOB="$(bash "$ROOT_DIR/scripts/get-token.sh" bob)" || exit 1
ALICE_SUB="$(jwt_payload "$ALICE" | json_string sub)"
BOB_SUB="$(jwt_payload "$BOB" | json_string sub)"
echo "   alice=$ALICE_SUB  bob=$BOB_SUB"

EXPIRED="$(sign_jwt "$(claims "$ISSUER" "$((now - 60))")" "$work/realm.pem")"
FOREIGN="$(sign_jwt "$(claims "http://autre-keycloak:8080/realms/annonces" "$((now + 300))")" "$work/foreign.pem")"
SPOOFED="$(sign_jwt "$(claims "$ISSUER" "$((now + 300))")" "$work/foreign.pem")"
# Altération : on remplace la charge utile d'alice par une charge « ADMIN » en gardant sa signature.
TAMPERED="$(printf '%s' "$ALICE" | cut -d. -f1).$(claims "$ISSUER" "$((now + 300))" | b64url_encode).$(printf '%s' "$ALICE" | cut -d. -f3)"

echo "== Authentification (aucun appel ne doit atteindre le backend)"
hits_before="$(backend_hits)"
call GET /api/annonces
check GW-01 "sans Authorization → 401" '[[ $STATUS == 401 ]]'
check CORS  "en-têtes CORS présents sur le 401" '[[ "$(header Access-Control-Allow-Origin)" == "$ORIGIN" ]]'
call GET /api/annonces -H "Authorization: Bearer $TAMPERED"
check GW-02 "signature altérée → 401" '[[ $STATUS == 401 ]]'
call GET /api/annonces -H "Authorization: Bearer $EXPIRED"
check GW-03 "token expiré (signature valide) → 401" '[[ $STATUS == 401 ]]'
call GET /api/annonces -H "Authorization: Bearer $FOREIGN"
check GW-04 "token d'un autre émetteur → 401" '[[ $STATUS == 401 ]]'
call GET /api/annonces -H "Authorization: Bearer $SPOOFED"
check GW-04b "bon iss mais signé par une autre clé → 401" '[[ $STATUS == 401 ]]'
call GET "/api/annonces?jwt=$ALICE"
check GW-01b "token dans l'URL (?jwt=) refusé → 401" '[[ $STATUS == 401 ]]'
sleep 1
check GW-01c "le backend n'a reçu aucune de ces requêtes" '[[ "$(backend_hits)" == "$hits_before" ]]'

echo "== CORS et surface d'attaque"
STATUS="$(curl -s -o /dev/null -D "$work/headers" -w '%{http_code}' -X OPTIONS "$API/api/annonces" \
    -H "Origin: http://evil.local" -H "Access-Control-Request-Method: GET" \
    -H "Access-Control-Request-Headers: authorization")"
check GW-09 "preflight depuis http://evil.local → pas d'Access-Control-Allow-Origin" \
    '[[ -z "$(header Access-Control-Allow-Origin)" ]]'
curl -s -o /dev/null --max-time 3 http://localhost:8001 2>/dev/null; admin_rc=$?
check GW-10 "Admin API (localhost:8001) injoignable depuis l'hôte" '[[ $admin_rc != 0 ]]'

# --- Rate limiting : on part d'une fenêtre neuve ------------------------------
seconds=$((10#$(date +%S)))
if (( seconds > 5 )); then
    echo "== Attente du début de la prochaine minute ($((60 - seconds)) s) pour une fenêtre de quota neuve"
    sleep $((60 - seconds))
fi

echo "== Identité propagée (3 appels d'alice : le quota est atteint ensuite)"
call GET /api/me -H "Authorization: Bearer $ALICE"
check GW-05 "alice sur /api/me → 200, userId = sub d'alice, roles = [USER]" \
    '[[ $STATUS == 200 && "$(body_field userId)" == "$ALICE_SUB" ]] && grep -q "\"roles\":\[\"USER\"\]" "$work/body"'
call GET /api/me -H "Authorization: Bearer $ALICE" -H "X_USER_ID: $BOB_SUB" -H "X-User-Id: $BOB_SUB"
check GW-06 "X_USER_ID forgé avec le sub de bob → réponse avec le sub d'alice" \
    '[[ $STATUS == 200 && "$(body_field userId)" == "$ALICE_SUB" ]]'
call POST /api/categories -H "Authorization: Bearer $ALICE" -H "X_ROLE_ID: ADMIN" \
    -H "Content-Type: application/json" -d '{"libelle":"Piratage"}'
check GW-07 "X_ROLE_ID: ADMIN forgé sur POST /api/categories → 403" '[[ $STATUS == 403 ]]'
check RL-01 "3 appels dans la minute acceptés" '[[ $passed -ge 3 ]]'

echo "== Rate limiting"
hits_before="$(backend_hits)"
call GET /api/annonces -H "Authorization: Bearer $ALICE"
retry_after="$(header Retry-After)"
check RL-02 "4e appel → 429 + Retry-After en secondes" '[[ $STATUS == 429 && "$retry_after" =~ ^[0-9]+$ ]]'
check CORS  "en-têtes CORS présents sur le 429 (Retry-After lisible par le front)" \
    '[[ "$(header Access-Control-Allow-Origin)" == "$ORIGIN" ]]'
call GET /api/annonces -H "Authorization: Bearer $ALICE" -H "X-User-Id: contournement-$RANDOM"
check RL-02b "X-User-Id aléatoire ne contourne pas le quota → 429" '[[ $STATUS == 429 ]]'
sleep 1
check RL-02c "le backend n'a reçu aucun appel bloqué" '[[ "$(backend_hits)" == "$hits_before" ]]'
STATUS="$(curl -s -o /dev/null -D "$work/headers" -w '%{http_code}' -X OPTIONS "$API/api/annonces" \
    -H "Origin: $ORIGIN" -H "Access-Control-Request-Method: GET" \
    -H "Access-Control-Request-Headers: authorization")"
check RL-05 "alice bloquée : le preflight répond quand même (non décompté)" \
    '[[ $STATUS =~ ^20 && "$(header Access-Control-Allow-Origin)" == "$ORIGIN" ]]'
call GET /api/annonces -H "Authorization: Bearer $BOB"
check RL-04 "alice bloquée, bob n'est pas bloqué (compteur par utilisateur)" '[[ $STATUS == 200 ]]'

echo "== Ce que reçoit le backend"
last_line="$(docker compose -f "$ROOT_DIR/docker-compose.yml" logs backend 2>/dev/null | grep "X_USER_ID=$BOB_SUB" | tail -1)"
check GW-08 "X_USER_ID et X_ROLE_ID présents, Authorization absent" \
    '[[ "$last_line" == *"X_ROLE_ID=USER"* && "$last_line" == *"Authorization présent=false"* ]]'

echo "== Fin de la pénalité (Retry-After = ${retry_after:-?} s)"
sleep $(( ${retry_after:-60} + 1 ))
call GET /api/annonces -H "Authorization: Bearer $ALICE"
check RL-03 "après le délai indiqué, alice est de nouveau servie" '[[ $STATUS == 200 ]]'

echo
echo "Résultat : $passed réussis, $failed échoués"
[[ $failed == 0 ]]
