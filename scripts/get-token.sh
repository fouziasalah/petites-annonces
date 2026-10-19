#!/usr/bin/env bash
# -----------------------------------------------------------------------------
# Obtient les tokens d'un utilisateur de test en ÉMULANT le flux
# Authorization Code + PKCE avec curl — exactement ce que fait le navigateur.
#
# POURQUOI ne pas utiliser grant_type=password ?
#   Le client annonces-front l'interdit (directAccessGrantsEnabled=false, KC-04) et
#   on ne l'affaiblit pas pour les tests. On rejoue donc le vrai parcours :
#     1. GET  /auth   avec code_challenge (S256)   → page de login HTML + cookies
#     2. POST du formulaire de login               → 302 vers redirect_uri?code=…
#     3. POST /token  avec code + code_verifier     → access_token, refresh_token, id_token
#
# USAGE
#   scripts/get-token.sh alice              # affiche l'access token
#   scripts/get-token.sh modo --json        # affiche la réponse complète de /token
#   scripts/get-token.sh bob 'motdepasse'   # mot de passe explicite
# Par défaut, le mot de passe est lu dans .env (REALM_PWD_<UTILISATEUR>).
# -----------------------------------------------------------------------------
set -euo pipefail
source "$(dirname "$0")/lib/common.sh"
load_env "${ENV_FILE:-$ROOT_DIR/.env}"

user="${1:?usage: $0 <utilisateur> [mot-de-passe] [--json]}"
shift
password_var="REALM_PWD_$(printf '%s' "$user" | tr '[:lower:]' '[:upper:]')"
password="${!password_var:-}"
output=access_token
for arg in "$@"; do
    case "$arg" in
        --json) output=json ;;
        *) password="$arg" ;;
    esac
done
[[ -n "$password" ]] || { echo "Mot de passe introuvable ($password_var)" >&2; exit 1; }

oidc="$KC_PUBLIC_URL/realms/annonces/protocol/openid-connect"
redirect_uri="$FRONT_PUBLIC_URL/"
jar="$(mktemp)"
trap 'rm -f "$jar"' EXIT

# --- PKCE : code_verifier aléatoire (43..128 car.), challenge = BASE64URL(SHA256(verifier))
verifier="$(openssl rand -base64 96 | tr -dc 'A-Za-z0-9' | cut -c1-64)"
challenge="$(printf '%s' "$verifier" | openssl dgst -sha256 -binary | b64url_encode)"
state="$(openssl rand -hex 16)"
nonce="$(openssl rand -hex 16)"

# --- 1. Demande d'autorisation : Keycloak renvoie la page de login
login_page="$(curl -sS -c "$jar" -b "$jar" -G "$oidc/auth" \
    --data-urlencode "client_id=annonces-front" \
    --data-urlencode "response_type=code" \
    --data-urlencode "scope=openid" \
    --data-urlencode "redirect_uri=$redirect_uri" \
    --data-urlencode "state=$state" \
    --data-urlencode "nonce=$nonce" \
    --data-urlencode "code_challenge=$challenge" \
    --data-urlencode "code_challenge_method=S256")"

action="$(printf '%s' "$login_page" \
    | grep -o 'action="[^"]*login-actions/authenticate[^"]*"' \
    | head -1 | sed -e 's/^action="//' -e 's/"$//' -e 's/&amp;/\&/g')"
[[ -n "$action" ]] || { echo "Formulaire de login introuvable (Keycloak démarré ? URL : $oidc)" >&2; exit 1; }

# --- 2. Soumission des identifiants : on NE suit PAS la redirection, on lit le code
location="$(curl -sS -c "$jar" -b "$jar" -o /dev/null -w '%{redirect_url}' "$action" \
    --data-urlencode "username=$user" \
    --data-urlencode "password=$password" \
    --data-urlencode "credentialId=")"

code="$(printf '%s' "$location" | sed -n 's/.*[?&]code=\([^&]*\).*/\1/p')"
returned_state="$(printf '%s' "$location" | sed -n 's/.*[?&]state=\([^&]*\).*/\1/p')"
[[ -n "$code" ]] || { echo "Pas de code d'autorisation (identifiants invalides ?)" >&2; exit 1; }
[[ "$returned_state" == "$state" ]] || { echo "state différent : réponse rejetée (anti-CSRF)" >&2; exit 1; }

# --- 3. Échange du code contre les tokens, en prouvant la possession du verifier
response="$(curl -sS "$oidc/token" \
    --data-urlencode "grant_type=authorization_code" \
    --data-urlencode "client_id=annonces-front" \
    --data-urlencode "code=$code" \
    --data-urlencode "redirect_uri=$redirect_uri" \
    --data-urlencode "code_verifier=$verifier")"

if [[ "$output" == json ]]; then
    printf '%s\n' "$response"
else
    token="$(printf '%s' "$response" | json_string access_token)"
    [[ -n "$token" ]] || { echo "Échec de l'échange du code : $response" >&2; exit 1; }
    printf '%s\n' "$token"
fi
