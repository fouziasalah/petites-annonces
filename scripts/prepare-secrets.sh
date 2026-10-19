#!/usr/bin/env bash
# -----------------------------------------------------------------------------
# Prépare, dans .secrets/ (ignoré par Git), un fichier par secret GitHub Actions :
# il suffit ensuite d'ouvrir chaque fichier et d'en coller le contenu dans
# Settings → Secrets and variables → Actions → New repository secret.
#
#   scripts/prepare-secrets.sh [domaine]        (défaut : 72-60-188-97.sslip.io)
#
# Ce que fait le script (sans jamais afficher de valeur secrète) :
#   - clé SSH dédiée au déploiement : ~/.ssh/petitesannonces_deploy (créée si absente) ;
#   - .env.prod : mots de passe aléatoires + nouvelle paire RSA (créé si absent) ;
#   - empreinte SSH du serveur (ssh-keyscan) pour VM_KNOWN_HOSTS ;
#   - .secrets/<NOM_DU_SECRET> pour chacun des 8 secrets.
# Seul DOCKERHUB_TOKEN reste à remplir à la main (créé sur hub.docker.com).
# -----------------------------------------------------------------------------
set -euo pipefail
source "$(dirname "$0")/lib/common.sh"

DOMAINE="${1:-72-60-188-97.sslip.io}"
VM_HOST="${VM_HOST:-72.60.188.97}"
VM_USER="${VM_USER:-root}"
VM_SSH_PORT="${VM_SSH_PORT:-22}"
DOCKERHUB_USERNAME="${DOCKERHUB_USERNAME:-fouziasalahhhh}"
DEPLOY_KEY="$HOME/.ssh/petitesannonces_deploy"
OUT="$ROOT_DIR/.secrets"

umask 077
mkdir -p "$OUT" "$HOME/.ssh"

if [[ ! -f "$DEPLOY_KEY" ]]; then
    # Sans passphrase : c'est un robot (GitHub Actions) qui l'utilise. Elle ne sert qu'à ce déploiement.
    ssh-keygen -q -t ed25519 -N "" -C "github-actions-petitesannonces" -f "$DEPLOY_KEY"
    echo "→ clé de déploiement créée : $DEPLOY_KEY"
fi

if [[ ! -f "$ROOT_DIR/.env.prod" ]]; then
    DOCKERHUB_USERNAME="$DOCKERHUB_USERNAME" bash "$ROOT_DIR/scripts/init-prod-env.sh" "$DOMAINE" > /dev/null
    echo "→ .env.prod créé (domaine $DOMAINE)"
fi

printf '%s' "$DOCKERHUB_USERNAME" > "$OUT/DOCKERHUB_USERNAME"
[[ -f "$OUT/DOCKERHUB_TOKEN" ]] || printf 'À REMPLACER par le token Docker Hub (Read & Write)' > "$OUT/DOCKERHUB_TOKEN"
printf '%s' "$VM_HOST" > "$OUT/VM_HOST"
printf '%s' "$VM_USER" > "$OUT/VM_USER"
printf '%s' "$VM_SSH_PORT" > "$OUT/VM_SSH_PORT"
cp "$DEPLOY_KEY" "$OUT/VM_SSH_KEY"
cp "$ROOT_DIR/.env.prod" "$OUT/ENV_FILE"
if ssh-keyscan -p "$VM_SSH_PORT" -T 10 "$VM_HOST" 2>/dev/null > "$OUT/VM_KNOWN_HOSTS" && [[ -s "$OUT/VM_KNOWN_HOSTS" ]]; then
    echo "→ empreinte du serveur récupérée"
else
    echo "✘ ssh-keyscan $VM_HOST a échoué : VM_KNOWN_HOSTS est vide" >&2
fi

echo
echo "Fichiers prêts dans $OUT :"
ls -1 "$OUT" | sed 's/^/   /'
echo
echo "Clé PUBLIQUE à autoriser sur la VM (elle peut circuler sans risque) :"
cat "$DEPLOY_KEY.pub"
