#!/usr/bin/env bash
# -----------------------------------------------------------------------------
# Extrait la clé publique de signature du realm depuis Keycloak, au format PEM,
# et la compare à celle que Kong utilise (REALM_RSA_PUBLIC_KEY dans .env).
#
# Procédure manuelle équivalente :
#   1. ouvrir ${KC_PUBLIC_URL}/realms/annonces  → champ JSON « public_key »
#      (ou console admin : Realm settings → Keys → RS256 → Public key) ;
#   2. l'encadrer par -----BEGIN PUBLIC KEY----- / -----END PUBLIC KEY-----,
#      en lignes de 64 caractères.
#
# Si les deux clés diffèrent, Kong rejettera tous les tokens (signature invalide → 401).
# -----------------------------------------------------------------------------
set -euo pipefail
source "$(dirname "$0")/lib/common.sh"
load_env "${ENV_FILE:-$ROOT_DIR/.env}"

realm_json="$(curl -fsS "$KC_PUBLIC_URL/realms/annonces")"
public_key="$(printf '%s' "$realm_json" | json_string public_key)"
[[ -n "$public_key" ]] || { echo "Clé publique introuvable dans la réponse de Keycloak" >&2; exit 1; }

echo "-----BEGIN PUBLIC KEY-----"
printf '%s\n' "$public_key" | fold -w 64
echo "-----END PUBLIC KEY-----"

if [[ "$public_key" == "$REALM_RSA_PUBLIC_KEY" ]]; then
    echo "✔ identique à REALM_RSA_PUBLIC_KEY (clé utilisée par Kong)" >&2
else
    echo "✘ DIFFÉRENTE de REALM_RSA_PUBLIC_KEY : Kong rejettera les tokens" >&2
    exit 2
fi
