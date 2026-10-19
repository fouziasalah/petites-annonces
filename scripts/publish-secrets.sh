#!/usr/bin/env bash
# -----------------------------------------------------------------------------
# Envoie la configuration de déploiement dans GitHub, avec la CLI officielle « gh » :
#   - les 8 secrets à partir des fichiers de .secrets/ (scripts/prepare-secrets.sh) ;
#   - l'environnement « production » ;
#   - la variable DEPLOY_ENABLED=true (active le workflow CD).
#
# Prérequis : gh installé (winget install --id GitHub.cli) et authentifié (gh auth login).
# Les valeurs sont lues dans les fichiers et transmises chiffrées à GitHub : elles ne
# sont jamais affichées.
#
#   scripts/publish-secrets.sh            (dépôt fouziasalah/petites-annonces)
# -----------------------------------------------------------------------------
set -euo pipefail
source "$(dirname "$0")/lib/common.sh"

REPO="${REPO:-fouziasalah/petites-annonces}"
DIR="$ROOT_DIR/.secrets"
SECRETS=(DOCKERHUB_USERNAME DOCKERHUB_TOKEN VM_HOST VM_USER VM_SSH_PORT VM_SSH_KEY VM_KNOWN_HOSTS ENV_FILE)

command -v gh > /dev/null || { echo "gh absent : winget install --id GitHub.cli" >&2; exit 1; }
gh auth status > /dev/null 2>&1 || { echo "gh non authentifié : gh auth login" >&2; exit 1; }

for name in "${SECRETS[@]}"; do
    [[ -s "$DIR/$name" ]] || { echo "Fichier manquant ou vide : .secrets/$name (scripts/prepare-secrets.sh)" >&2; exit 1; }
done
if grep -q "À REMPLACER" "$DIR/DOCKERHUB_TOKEN"; then
    echo "Colle d'abord le token Docker Hub dans .secrets/DOCKERHUB_TOKEN" >&2
    exit 1
fi

for name in "${SECRETS[@]}"; do
    gh secret set "$name" --repo "$REPO" < "$DIR/$name"
    echo "✔ secret $name"
done

# L'environnement « production » (référencé par le job deploy) : créé s'il n'existe pas.
gh api --method PUT "repos/$REPO/environments/production" > /dev/null
echo "✔ environnement production"

gh variable set DEPLOY_ENABLED --repo "$REPO" --body true
echo "✔ variable DEPLOY_ENABLED=true"

echo
echo "Prochaine étape : gh workflow run cd.yml --repo $REPO   (ou Actions → CD → Run workflow)"
