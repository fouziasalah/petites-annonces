#!/usr/bin/env bash
# -----------------------------------------------------------------------------
# Crée un fichier d'environnement de PRODUCTION (par défaut .env.prod, ignoré par Git) :
#   - mots de passe aléatoires (Postgres, Keycloak, utilisateurs de test) ;
#   - nouvelle paire RSA pour le realm (≠ clé de dev publique de .env.example) ;
#   - URLs publiques selon le mode de déploiement.
#
# La CD l'exécute SUR LA VM au premier déploiement (OUT=.env) : les secrets sont
# générés sur place et ne transitent jamais par GitHub. Ne jamais le relancer sur un
# .env existant : les mots de passe d'une base déjà créée ne changeraient pas.
#
# USAGE
#   scripts/init-prod-env.sh                          # option A : tunnel SSH (URLs localhost)
#   scripts/init-prod-env.sh 72-60-188-97.sslip.io    # option B : HTTPS public
#       → https://app.<domaine>, https://api.<domaine>, https://auth.<domaine>
# -----------------------------------------------------------------------------
set -euo pipefail
source "$(dirname "$0")/lib/common.sh"

DOMAINE="${1:-}"
OUT="${OUT:-$ROOT_DIR/.env.prod}"
DOCKERHUB_USER="${DOCKERHUB_USERNAME:-fouziasalahhhh}"

if [[ -e "$OUT" ]]; then
    echo "$OUT existe déjà : supprimez-le d'abord si vous voulez vraiment régénérer les secrets." >&2
    echo "(Attention : changer les secrets d'une base existante casse la connexion à cette base.)" >&2
    exit 1
fi

cp "$ROOT_DIR/.env.example" "$OUT"

set_env "$OUT" DOCKERHUB_USERNAME "$DOCKERHUB_USER"
set_env "$OUT" IMAGE_TAG latest
set_env "$OUT" BIND_ADDRESS 127.0.0.1

if [[ -n "$DOMAINE" ]]; then
    # Option B : Caddy en HTTPS devant les services (ports 80/443 de la VM).
    set_env "$OUT" FRONT_PUBLIC_URL "https://app.$DOMAINE"
    set_env "$OUT" GATEWAY_PUBLIC_URL "https://api.$DOMAINE"
    set_env "$OUT" KC_PUBLIC_URL "https://auth.$DOMAINE"
    set_env "$OUT" PUBLIC_DOMAIN "$DOMAINE"
    # Compose lit COMPOSE_FILE dans .env : ajoute la surcharge VM (Caddy).
    set_env "$OUT" COMPOSE_FILE "docker-compose.yml:docker-compose.vm.yml"
else
    # Option A : tunnel SSH. URLs localhost inchangées (vues par le navigateur à
    # travers le tunnel) ; ports de la VM décalés pour ne gêner aucune autre application.
    set_env "$OUT" FRONT_HOST_PORT 14200
    set_env "$OUT" GATEWAY_HOST_PORT 18080
    set_env "$OUT" KC_HOST_PORT 18180
fi

for var in POSTGRES_PASSWORD KEYCLOAK_DB_PASSWORD ANNONCES_DB_PASSWORD KC_BOOTSTRAP_ADMIN_PASSWORD \
           REALM_PWD_ALICE REALM_PWD_BOB REALM_PWD_MODO REALM_PWD_ADMIN; do
    set_env "$OUT" "$var" "$(random_secret 24)"
done

# Nouvelle paire RSA, différente de la clé de dev publiée dans .env.example.
bash "$ROOT_DIR/scripts/gen-keys.sh" "$OUT" > /dev/null

echo "→ $OUT créé."
echo "   Mots de passe des comptes : grep -E 'KC_BOOTSTRAP|REALM_PWD' $OUT"
