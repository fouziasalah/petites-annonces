# syntax=docker/dockerfile:1
# =============================================================================
# Image Docker du front Angular.
#
# Construction en 2 étapes (« multi-stage ») :
#   1. build   : une image Node compile l'application Angular en fichiers statiques
#                (HTML, JS, CSS) ;
#   2. runtime : une image Nginx légère sert ces fichiers. Node, le code source et les
#                node_modules ne sont PAS dans l'image finale (plus petite, plus sûre).
#
# Variables d'environnement (lues au DÉMARRAGE du conteneur, pas à la construction) :
#   KEYCLOAK_URL        URL de Keycloak vue par le navigateur  (défaut : http://localhost:8180)
#   KEYCLOAK_REALM      realm Keycloak                         (défaut : annonces)
#   KEYCLOAK_CLIENT_ID  client OIDC du front                   (défaut : annonces-front)
#   GATEWAY_URL         URL de l'API vue par le navigateur     (défaut : http://localhost:8080)
# Elles sont écrites dans /config.json, que l'application lit au chargement de la page.
# Une même image sert donc en local et en production : seules les variables changent.
#
# Commandes (dans le dossier frontend) :
#   docker build -t petitesannonces-frontend .
#   docker run -p 4200:8080 -e GATEWAY_URL=http://localhost:8080 petitesannonces-frontend
#   → application sur http://localhost:4200
# =============================================================================

# ----------------------------------------------------------------------------
# Étape 1 : compilation Angular
# ----------------------------------------------------------------------------
# FROM … AS build : image de départ (Node 24 sur Alpine Linux), nommée « build ».
FROM node:24-alpine AS build
# WORKDIR : dossier de travail dans l'image (créé s'il n'existe pas).
WORKDIR /app

# On copie d'abord uniquement la liste des dépendances : tant que package*.json ne change
# pas, Docker réutilise sa couche en cache et ne réinstalle pas les dépendances.
COPY package.json package-lock.json ./
# npm ci : installe exactement les versions du package-lock.json.
# --mount=type=cache : garde le cache npm entre deux constructions (plus rapide).
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund

# Puis le reste du code source (filtré par .dockerignore), et la compilation de production.
COPY . .
RUN npx ng build --configuration production
# Résultat : /app/dist/frontend/browser (index.html, main-XXXX.js, styles-XXXX.css…).

# ----------------------------------------------------------------------------
# Étape 2 : image finale servie par Nginx
# ----------------------------------------------------------------------------
# nginx-unprivileged : Nginx tourne avec un utilisateur sans droits root et écoute sur 8080
# (un port inférieur à 1024 exigerait root).
FROM nginxinc/nginx-unprivileged:1.31.7-alpine AS runtime

# Valeurs par défaut des variables d'environnement (remplaçables avec docker run -e … ou
# la section « environment » de docker-compose).
ENV KEYCLOAK_URL=http://localhost:8180 \
    KEYCLOAK_REALM=annonces \
    KEYCLOAK_CLIENT_ID=annonces-front \
    GATEWAY_URL=http://localhost:8080

# Configuration Nginx du site (remplace celle fournie par défaut dans l'image).
COPY docker/nginx.conf /etc/nginx/conf.d/default.conf
COPY docker/security-headers.conf /etc/nginx/snippets/security-headers.conf
# Modèle de config.json, rempli au démarrage avec les variables d'environnement.
COPY docker/config.json.template /etc/app/config.json.template
# Script exécuté automatiquement au démarrage : l'image Nginx lance tous les scripts du
# dossier /docker-entrypoint.d/ avant Nginx. --chmod=755 : le rend exécutable.
COPY --chmod=755 docker/40-generer-config.sh /docker-entrypoint.d/40-generer-config.sh
# --from=build : récupère les fichiers compilés de l'étape 1.
COPY --from=build /app/dist/frontend/browser /usr/share/nginx/html

# EXPOSE : documente le port d'écoute du conteneur (à publier avec -p 4200:8080).
EXPOSE 8080
# Pas de CMD : celle de l'image Nginx (démarrer nginx) est conservée.
