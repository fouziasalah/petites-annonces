# syntax=docker/dockerfile:1
# =============================================================================
# Image du front — build multi-stage
#   1. build   : Node, npm ci, build Angular de production ;
#   2. runtime : Nginx non-root, uniquement les fichiers statiques produits.
# Les URLs (Keycloak, gateway) ne sont PAS dans le bundle : config.json est généré
# au démarrage → la même image sert en local et sur la VM.
# =============================================================================

FROM node:24-alpine AS build
WORKDIR /app
# Dépendances d'abord : la couche npm ci n'est refaite que si le lockfile change.
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund
COPY . .
RUN npx ng build --configuration production

FROM nginxinc/nginx-unprivileged:1.31.7-alpine AS runtime
# Image « unprivileged » : Nginx tourne avec l'utilisateur nginx et écoute sur 8080.
COPY --chmod=755 docker/30-config-json.sh /docker-entrypoint.d/30-config-json.sh
COPY docker/default.conf.template docker/security-headers.inc.template /etc/nginx/templates/
COPY --from=build /app/dist/frontend/browser /usr/share/nginx/html
EXPOSE 8080
