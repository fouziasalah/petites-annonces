#!/usr/bin/env bash
# -----------------------------------------------------------------------------
# Génère la paire de clés RSA qui signe les tokens du realm « annonces » (§ 6.4).
#
#   scripts/gen-keys.sh              écrit dans .env (créé depuis .env.example s'il n'existe pas)
#   scripts/gen-keys.sh .env.example écrit dans un autre fichier
#
# Ce que fait le script :
#   1. génère une clé privée RSA 2048 bits et un certificat auto-signé (openssl) ;
#   2. écrit REALM_RSA_PRIVATE_KEY et REALM_RSA_CERTIFICATE dans le fichier d'environnement
#      (base64 sur une ligne : format attendu par Keycloak dans realm-annonces.json) ;
#   3. recopie la clé PUBLIQUE dans kong/kong.yml, entre les lignes BEGIN/END PUBLIC KEY,
#      pour que Kong vérifie les tokens avec la bonne clé.
# Aucun fichier de clé n'est laissé sur le disque.
#
# Ensuite, pour appliquer la nouvelle clé :
#   - le realm n'est importé que s'il n'existe pas : le supprimer dans la console Keycloak
#     (ou docker compose down -v, qui efface TOUTES les données) ;
#   - docker compose up -d --wait   (Keycloak réimporte le realm)
#   - docker compose restart gateway (Kong relit kong.yml)
# -----------------------------------------------------------------------------
set -euo pipefail

# Git Bash (Windows) convertit les arguments commençant par « / » en chemins Windows :
# on l'en empêche pour le sujet du certificat « /CN=… ». Sans effet sous Linux.
export MSYS2_ARG_CONV_EXCL="/CN="

# Dossier racine du projet (parent du dossier scripts).
RACINE="$(cd "$(dirname "$0")/.." && pwd)"
FICHIER_ENV="${1:-$RACINE/.env}"
KONG_YML="$RACINE/kong/kong.yml"

if [[ ! -f "$FICHIER_ENV" ]]; then
    cp "$RACINE/.env.example" "$FICHIER_ENV"
    echo "→ $FICHIER_ENV créé depuis .env.example"
fi

# Dossier temporaire, effacé automatiquement à la fin du script (trap … EXIT).
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

# 1. Clé privée, certificat et clé publique.
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$tmp/privee.pem" 2>/dev/null
openssl req -new -x509 -key "$tmp/privee.pem" -subj "/CN=annonces" -days 3650 -out "$tmp/certificat.pem" 2>/dev/null
openssl pkey -in "$tmp/privee.pem" -pubout -out "$tmp/publique.pem"

# Contenu base64 d'un fichier PEM, sans les lignes -----BEGIN/END----- ni retours à la ligne.
corps_pem() { grep -v -- '-----' "$1" | tr -d '\r\n'; }

# Remplace (ou ajoute) la ligne NOM=valeur dans le fichier d'environnement.
ecrire_variable() {
    local nom="$1" valeur="$2"
    grep -v "^$nom=" "$FICHIER_ENV" > "$tmp/env" || true
    echo "$nom=$valeur" >> "$tmp/env"
    cat "$tmp/env" > "$FICHIER_ENV"
}

# 2. Clé privée et certificat → fichier d'environnement (lus par Keycloak).
ecrire_variable REALM_RSA_PRIVATE_KEY "$(corps_pem "$tmp/privee.pem")"
ecrire_variable REALM_RSA_CERTIFICATE "$(corps_pem "$tmp/certificat.pem")"
echo "→ clé privée et certificat écrits dans $FICHIER_ENV"

# 3. Clé publique → kong/kong.yml. On garde l'indentation de la ligne BEGIN (YAML l'exige).
indentation="$(grep -- '-----BEGIN PUBLIC KEY-----' "$KONG_YML" | sed 's/-----BEGIN.*//')"
{
    sed '/-----BEGIN PUBLIC KEY-----/,$d' "$KONG_YML"                 # tout ce qui précède la clé
    sed "s/^/$indentation/" "$tmp/publique.pem"                       # la nouvelle clé, indentée
    sed '1,/-----END PUBLIC KEY-----/d' "$KONG_YML"                   # tout ce qui suit la clé
} > "$tmp/kong.yml"
cat "$tmp/kong.yml" > "$KONG_YML"
echo "→ clé publique recopiée dans kong/kong.yml"
