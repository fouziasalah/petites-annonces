#!/bin/sh
# -----------------------------------------------------------------------------
# Génère /config.json à partir des variables d'environnement du conteneur.
#
# Quand ? Au démarrage du conteneur, avant Nginx : l'image officielle Nginx exécute
# automatiquement les scripts du dossier /docker-entrypoint.d/ (voir Dockerfile).
#
# Comment ? envsubst (outil GNU gettext, présent dans l'image) remplace ${KEYCLOAK_URL}…
# dans le modèle par la valeur des variables. Seules les 4 variables listées sont
# remplacées : un autre « $ » éventuel dans le modèle resterait intact.
#
# Où ? Dans /tmp/app-config : l'image tourne sans root, le dossier du site
# (/usr/share/nginx/html) n'est pas modifiable. nginx.conf sert ce fichier à l'adresse /config.json.
# -----------------------------------------------------------------------------

# set -e : arrête le script à la première erreur ; set -u : erreur si une variable n'existe pas.
set -eu

mkdir -p /tmp/app-config
envsubst '${KEYCLOAK_URL} ${KEYCLOAK_REALM} ${KEYCLOAK_CLIENT_ID} ${GATEWAY_URL}' \
    < /etc/app/config.json.template \
    > /tmp/app-config/config.json

# Visible dans « docker logs » : permet de vérifier la configuration utilisée.
echo "config.json généré : Keycloak=${KEYCLOAK_URL} realm=${KEYCLOAK_REALM} client=${KEYCLOAK_CLIENT_ID} API=${GATEWAY_URL}"
