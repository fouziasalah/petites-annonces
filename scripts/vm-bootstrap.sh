#!/usr/bin/env bash
# -----------------------------------------------------------------------------
# Prépare la VM avant chaque déploiement. Exécuté PAR LE CD via SSH :
#     ssh vm "bash -s" < scripts/vm-bootstrap.sh
# Idempotent : sur une VM déjà prête, il ne fait que vérifier.
#
#   1. installe Docker Engine + plugin Compose (dépôt officiel Docker) s'ils manquent ;
#   2. crée le dossier de déploiement ;
#   3. ouvre 80/443 dans ufw SI ufw est actif (Let's Encrypt doit joindre le port 80) ;
#   4. refuse de continuer si 80/443 sont occupés par un autre service que notre Caddy.
#
# Nécessite root, ou un utilisateur avec sudo sans mot de passe.
# -----------------------------------------------------------------------------
set -euo pipefail

DEPLOY_DIR="${DEPLOY_DIR:-/opt/petitesannonces}"
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

# --- 3. Pare-feu ---------------------------------------------------------------
# On n'active jamais ufw (risque de couper SSH) ; s'il est déjà actif, on autorise le web.
if command -v ufw >/dev/null 2>&1 && $SUDO ufw status | grep -q "Status: active"; then
    $SUDO ufw allow 80/tcp > /dev/null
    $SUDO ufw allow 443/tcp > /dev/null
    echo "✔ ufw actif : 80/tcp et 443/tcp autorisés"
fi

# --- 4. Ports 80 / 443 -------------------------------------------------------
# Un autre conteneur qui publie 80/443 ?
others="$(docker ps --format '{{.Names}} {{.Ports}}' | grep -E ':(80|443)->' | grep -v '^petitesannonces-caddy-' || true)"
# Un service hors Docker (nginx, apache…) qui écoute sur 80/443 ?
system="$($SUDO ss -tlnpH '( sport = :80 or sport = :443 )' | grep -v docker-proxy || true)"
if [ -n "$others$system" ]; then
    echo "✘ Les ports 80/443 sont déjà utilisés ; Caddy ne pourra pas démarrer :" >&2
    printf '%s\n%s\n' "$others" "$system" >&2
    exit 1
fi
echo "✔ ports 80/443 disponibles pour Caddy"
