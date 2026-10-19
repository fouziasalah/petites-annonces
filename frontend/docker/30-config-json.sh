#!/bin/sh
# -----------------------------------------------------------------------------
# Exécuté par l'image Nginx au démarrage (dossier /docker-entrypoint.d/).
# Génère /config.json à partir des variables d'environnement du conteneur :
# UNE image pour tous les environnements, seules les URLs changent (CI/CD).
# Écrit dans /tmp : l'image tourne sans root, le dossier html n'est pas modifiable.
# -----------------------------------------------------------------------------
set -eu

: "${KC_PUBLIC_URL:?KC_PUBLIC_URL manquant}"
: "${GATEWAY_PUBLIC_URL:?GATEWAY_PUBLIC_URL manquant}"

mkdir -p /tmp/app-config
cat > /tmp/app-config/config.json <<EOF
{
  "keycloakUrl": "${KC_PUBLIC_URL}",
  "realm": "${KEYCLOAK_REALM:-annonces}",
  "clientId": "${KEYCLOAK_CLIENT_ID:-annonces-front}",
  "gatewayUrl": "${GATEWAY_PUBLIC_URL}"
}
EOF
echo "config.json généré (Keycloak=${KC_PUBLIC_URL}, gateway=${GATEWAY_PUBLIC_URL})"
