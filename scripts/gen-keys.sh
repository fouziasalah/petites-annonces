#!/usr/bin/env bash
# -----------------------------------------------------------------------------
# Génère la paire de clés RSA qui signe les tokens du realm « annonces ».
#
# POURQUOI une paire FIXE ?
#   Le plugin jwt de Kong OSS ne sait pas lire le JWKS de Keycloak : la clé
#   publique doit être donnée à Kong dans sa configuration déclarative. Si Keycloak
#   générait lui-même sa clé (comportement par défaut), elle changerait à chaque
#   nouvelle base et Kong rejetterait tous les tokens (401).
#
# CE QUE FAIT LE SCRIPT
#   1. génère une clé privée RSA 2048 (PKCS#8), sa clé publique et un certificat
#      auto-signé ;
#   2. les écrit dans le fichier d'environnement ciblé, en base64 sur une ligne,
#      sans en-têtes PEM :
#        REALM_RSA_PRIVATE_KEY / REALM_RSA_CERTIFICATE → injectés dans le realm
#            Keycloak (placeholders ${...} à l'import) ;
#        REALM_RSA_PUBLIC_KEY → injectée dans kong/kong.yml au démarrage de Kong
#            (kong/render-config.sh).
#   Une seule source de vérité (.env) : la clé de Kong ne peut pas diverger de
#   celle de Keycloak. Aucun fichier de clé n'est laissé sur le disque.
#
# USAGE
#   scripts/gen-keys.sh                 # cible .env (créé depuis .env.example si absent)
#   scripts/gen-keys.sh .env.prod       # cible un autre fichier (ex. production)
#
# ATTENTION : le realm n'est importé qu'au premier démarrage. Après avoir changé
# de clé sur une base existante : docker compose down -v (efface les données).
# -----------------------------------------------------------------------------
set -euo pipefail
source "$(dirname "$0")/lib/common.sh"

ENV_FILE="${1:-$ROOT_DIR/.env}"

if [[ ! -f "$ENV_FILE" ]]; then
    cp "$ROOT_DIR/.env.example" "$ENV_FILE"
    echo "→ $ENV_FILE créé depuis .env.example"
fi

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$tmp/private.pem" 2>/dev/null
openssl req -new -x509 -key "$tmp/private.pem" -subj "/CN=annonces" -days 3650 -out "$tmp/cert.pem" 2>/dev/null
openssl pkey -in "$tmp/private.pem" -pubout -out "$tmp/public.pem"

# Corps base64 d'un PEM, sans en-têtes ni retours à la ligne.
pem_body() { grep -v -- '-----' "$1" | tr -d '\r\n'; }

set_env "$ENV_FILE" REALM_RSA_PRIVATE_KEY "$(pem_body "$tmp/private.pem")"
set_env "$ENV_FILE" REALM_RSA_CERTIFICATE "$(pem_body "$tmp/cert.pem")"
set_env "$ENV_FILE" REALM_RSA_PUBLIC_KEY "$(pem_body "$tmp/public.pem")"
echo "→ paire RSA écrite dans $ENV_FILE"
