#!/usr/bin/env bash
# -----------------------------------------------------------------------------
# Prépare la VM avant chaque déploiement. Exécuté PAR LE CD via SSH :
#     ssh vm "VAR=… bash -s" < scripts/vm-bootstrap.sh
# Idempotent : sur une VM déjà prête, il ne fait que vérifier.
#
#   1. installe Docker Engine + plugin Compose (dépôt officiel Docker) s'ils manquent ;
#   2. crée le dossier de déploiement ;
#   3. vérifie que les ports dont on a besoin sont libres (ou déjà à nous) :
#        - option A (tunnel SSH)  : FRONT/GATEWAY/KC_HOST_PORT sur 127.0.0.1 ;
#        - option B (PUBLIC_DOMAIN défini) : 80/443 pour Caddy, ouverts dans ufw s'il est actif.
#   On ne touche JAMAIS aux autres applications : en cas de conflit, on s'arrête.
#
# Nécessite root, ou un utilisateur avec sudo sans mot de passe.
# -----------------------------------------------------------------------------
set -euo pipefail

DEPLOY_DIR="${DEPLOY_DIR:-/opt/petitesannonces}"
PUBLIC_DOMAIN="${PUBLIC_DOMAIN:-}"
SUDO=""
if [ "$(id -u)" -ne 0 ]; then
    SUDO="sudo -n"
fi

# --- 1. Docker ---------------------------------------------------------------
if ! command -v docker >/dev/null 2>&1 || ! docker compose version >/dev/null 2>&1; then
    echo "→ Installation de Docker Engine et du plugin Compose (dépôt officiel)"
    export DEBIAN_FRONTEND=noninteractive
    $SUDO apt-get update -qq
    $SUDO apt-get install -y -qq ca-certificates curl
    $SUDO install -m 0755 -d /etc/apt/keyrings
    $SUDO curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
    $SUDO chmod a+r /etc/apt/keyrings/docker.asc
    echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
        | $SUDO tee /etc/apt/sources.list.d/docker.list > /dev/null
    $SUDO apt-get update -qq
    $SUDO apt-get install -y -qq docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
    $SUDO systemctl enable --now docker
fi
echo "✔ $(docker --version) / $(docker compose version)"

# --- 2. Dossier de déploiement -----------------------------------------------
$SUDO mkdir -p "$DEPLOY_DIR"
$SUDO chown "$(id -un)" "$DEPLOY_DIR"
echo "✔ dossier $DEPLOY_DIR"

# --- 3. Ports ------------------------------------------------------------------
# Un port est « à nous » s'il est publié par un conteneur du projet petitesannonces.
ports_to_check() {
    if [ -n "$PUBLIC_DOMAIN" ]; then
        echo "80 443"
    else
        echo "${FRONT_HOST_PORT:-4200} ${GATEWAY_HOST_PORT:-8080} ${KC_HOST_PORT:-8180}"
    fi
}
conflicts=""
for port in $(ports_to_check); do
    others="$(docker ps --format '{{.Names}} {{.Ports}}' | grep -E ":${port}->" | grep -v '^petitesannonces-' || true)"
    system="$($SUDO ss -tlnpH "( sport = :${port} )" | grep -v docker-proxy || true)"
    if [ -n "$others$system" ]; then
        conflicts="${conflicts}port ${port} : ${others}${system}"$'\n'
    fi
done
if [ -n "$conflicts" ]; then
    echo "✘ Ports déjà utilisés par une autre application (rien n'a été modifié) :" >&2
    printf '%s' "$conflicts" >&2
    echo "  → choisir d'autres valeurs pour *_HOST_PORT dans ENV_FILE." >&2
    exit 1
fi
echo "✔ ports libres : $(ports_to_check)"

# Option B uniquement : Let's Encrypt doit joindre le port 80.
# On n'active jamais ufw (risque de couper SSH) ; s'il est déjà actif, on autorise le web.
if [ -n "$PUBLIC_DOMAIN" ] && command -v ufw >/dev/null 2>&1 && $SUDO ufw status | grep -q "Status: active"; then
    $SUDO ufw allow 80/tcp > /dev/null
    $SUDO ufw allow 443/tcp > /dev/null
    echo "✔ ufw actif : 80/tcp et 443/tcp autorisés"
fi
