#!/usr/bin/env bash
# -----------------------------------------------------------------------------
# Génère la paire de clés RSA qui signe les tokens du realm « annonces ».
#
# POURQUOI une paire FIXE ?
#   Le plugin jwt de Kong OSS ne sait pas lire le JWKS de Keycloak : la clé
#   publique doit être écrite en dur dans kong/kong.yml. Si Keycloak générait
#   lui-même sa clé (comportement par défaut), elle changerait à chaque nouvelle
#   base et Kong rejetterait tous les tokens (401).
#
# CE QUE FAIT LE SCRIPT
#   1. génère une clé privée RSA 2048 (PKCS#8) et un certificat auto-signé ;
#   2. écrit REALM_RSA_PRIVATE_KEY et REALM_RSA_CERTIFICATE (base64 sur une ligne,
#      sans en-têtes PEM) dans le fichier d'environnement ciblé — Keycloak les
#      injecte dans le realm grâce aux placeholders ${...} à l'import ;
#   3. si kong/kong.yml existe, y remplace la clé publique PEM entre les marqueurs
#      « BEGIN realm-public-key » et « END realm-public-key ».
#   Aucun fichier de clé n'est laissé sur le disque (répertoire temporaire effacé).
#
# USAGE
#   scripts/gen-keys.sh                 # cible .env (créé depuis .env.example si absent)
#   scripts/gen-keys.sh .env.prod       # cible un autre fichier (ex. production)
#   KONG_FILE=/dev/null scripts/gen-keys.sh .env.prod   # sans toucher à kong.yml
#
# ATTENTION : le realm n'est importé qu'au premier démarrage. Après avoir changé
# de clé sur une base existante : docker compose down -v (efface les données).
# -----------------------------------------------------------------------------
set -euo pipefail
source "$(dirname "$0")/lib/common.sh"

ENV_FILE="${1:-$ROOT_DIR/.env}"
KONG_FILE="${KONG_FILE:-$ROOT_DIR/kong/kong.yml}"

if [[ ! -f "$ENV_FILE" ]]; then
    cp "$ROOT_DIR/.env.example" "$ENV_FILE"
    echo "→ $ENV_FILE créé depuis .env.example"
fi

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$tmp/private.pem" 2>/dev/null
openssl req -new -x509 -key "$tmp/private.pem" -subj "/CN=annonces" -days 3650 -out "$tmp/cert.pem" 2>/dev/null
openssl pkey -in "$tmp/private.pem" -pubout -out "$tmp/public.pem"

# Corps base64 d'un PEM, sans en-têtes ni retours à la ligne (format attendu par Keycloak).
pem_body() { grep -v -- '-----' "$1" | tr -d '\r\n'; }

set_env "$ENV_FILE" REALM_RSA_PRIVATE_KEY "$(pem_body "$tmp/private.pem")"
set_env "$ENV_FILE" REALM_RSA_CERTIFICATE "$(pem_body "$tmp/cert.pem")"
echo "→ clé privée et certificat écrits dans $ENV_FILE"

if [[ -f "$KONG_FILE" ]] && grep -q 'BEGIN realm-public-key' "$KONG_FILE"; then
    # On recopie l'indentation de la ligne marqueur devant chaque ligne du PEM
    # (le PEM est un bloc YAML littéral « | » : l'indentation fait partie de la syntaxe).
    awk -v pem="$tmp/public.pem" '
        /BEGIN realm-public-key/ {
            print
            match($0, /^ */); indent = substr($0, 1, RLENGTH)
            while ((getline line < pem) > 0) print indent line
            skip = 1; next
        }
        /END realm-public-key/ { skip = 0 }
        !skip { print }
    ' "$KONG_FILE" > "$tmp/kong.yml" && cat "$tmp/kong.yml" > "$KONG_FILE"
    echo "→ clé publique mise à jour dans $KONG_FILE"
fi

echo
echo "Clé publique (à placer dans le credential jwt_secrets de Kong) :"
cat "$tmp/public.pem"
