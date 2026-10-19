#!/usr/bin/env bash
# -----------------------------------------------------------------------------
# Fonctions partagées par les scripts (à « sourcer », pas à exécuter).
# Dépendances volontairement minimales : bash, curl, openssl, sed, grep
# (fonctionne sous Linux, dans la CI et sous Git Bash sur Windows).
# -----------------------------------------------------------------------------

# Sous Git Bash (Windows), les arguments commençant par « / » sont convertis en chemins
# Windows avant d'être passés aux binaires natifs (openssl…). On garde cette conversion
# (nécessaire pour /tmp) mais on l'exclut pour le sujet de certificat « /CN=… ».
# Sans effet sous Linux.
export MSYS2_ARG_CONV_EXCL="/CN="

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

# Charge .env (ou le fichier passé en argument) dans l'environnement courant.
load_env() {
    local env_file="${1:-$ROOT_DIR/.env}"
    if [[ ! -f "$env_file" ]]; then
        echo "Fichier $env_file introuvable (cp .env.example .env)" >&2
        return 1
    fi
    set -a
    # shellcheck disable=SC1090
    . "$env_file"
    set +a
}

# Remplace (ou ajoute) KEY=valeur dans un fichier d'environnement : set_env <fichier> <clé> <valeur>
set_env() {
    local file="$1" key="$2" value="$3" tmp
    tmp="$(mktemp)"
    awk -v k="$key" -v v="$value" '
        BEGIN { done = 0 }
        index($0, k "=") == 1 { print k "=" v; done = 1; next }
        { print }
        END { if (!done) print k "=" v }
    ' "$file" > "$tmp" && cat "$tmp" > "$file" && rm -f "$tmp"
}

# Chaîne aléatoire alphanumérique (mots de passe générés).
random_secret() {
    openssl rand -base64 48 | tr -dc 'A-Za-z0-9' | cut -c1-"${1:-32}"
}

# Décode du base64url (alphabet JWT, sans padding) vers du texte.
b64url_decode() {
    local data="${1//-/+}"
    data="${data//_//}"
    case $(( ${#data} % 4 )) in
        2) data="$data==" ;;
        3) data="$data=" ;;
    esac
    printf '%s' "$data" | openssl base64 -d -A
}

# Encode l'entrée standard en base64url sans padding.
b64url_encode() {
    openssl base64 -A | tr '+/' '-_' | tr -d '='
}

# Affiche la charge utile (payload JSON) d'un JWT. AUCUNE vérification de signature.
jwt_payload() {
    local payload
    payload="$(printf '%s' "$1" | cut -d. -f2)"
    b64url_decode "$payload"
}

# Extrait la valeur d'un champ texte de premier niveau d'un JSON compact (sans jq).
json_string() {
    sed -n "s/.*\"$1\":\"\([^\"]*\)\".*/\1/p"
}
