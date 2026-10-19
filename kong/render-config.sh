#!/bin/sh
# -----------------------------------------------------------------------------
# Produit la configuration Kong finale à partir de kong/kong.yml.
#
#   render-config.sh <modèle> <sortie>
#
# Remplace UNIQUEMENT ces variables (liste blanche, aucun autre « $ » n'est touché) :
#   ${FRONT_PUBLIC_URL}, ${KC_PUBLIC_URL}, ${REALM_RSA_PUBLIC_KEY}
#   ${FRONT_ORIGIN_REGEX} : dérivée de FRONT_PUBLIC_URL → ^https://app\.exemple\.fr$
# La clé publique (base64 sur une ligne dans .env) est découpée en lignes de
# 64 caractères, avec l'indentation du placeholder : c'est le format PEM attendu.
#
# Utilisé :
#   - au démarrage du conteneur Kong (voir docker-compose.yml, entrypoint) ;
#   - en CI pour valider la config (kong config parse).
# POSIX sh + awk uniquement : disponibles dans l'image officielle de Kong.
# -----------------------------------------------------------------------------
set -eu

template="$1"
output="$2"

: "${FRONT_PUBLIC_URL:?FRONT_PUBLIC_URL manquant}"
: "${KC_PUBLIC_URL:?KC_PUBLIC_URL manquant}"
: "${REALM_RSA_PUBLIC_KEY:?REALM_RSA_PUBLIC_KEY manquant (scripts/gen-keys.sh)}"

awk -v front="$FRONT_PUBLIC_URL" -v kc="$KC_PUBLIC_URL" -v key="$REALM_RSA_PUBLIC_KEY" '
    index($0, "${REALM_RSA_PUBLIC_KEY}") {
        indent = substr($0, 1, index($0, "${REALM_RSA_PUBLIC_KEY}") - 1)
        for (i = 1; i <= length(key); i += 64) print indent substr(key, i, 64)
        next
    }
    BEGIN {
        # Points échappés : dans une regex, « . » signifierait « n importe quel caractère ».
        # Concaténation plutôt que gsub : le traitement des antislashs dans le remplacement
        # de gsub diffère selon les awk (mawk dans l image Kong, gawk ailleurs).
        # (Pas d apostrophe dans ce programme : il est entre guillemets simples.)
        n = split(front, parts, ".")
        origin_regex = parts[1]
        for (i = 2; i <= n; i++) origin_regex = origin_regex "\\." parts[i]
        origin_regex = "^" origin_regex "$"
    }
    {
        gsub(/\$\{FRONT_ORIGIN_REGEX\}/, origin_regex)
        gsub(/\$\{FRONT_PUBLIC_URL\}/, front)
        gsub(/\$\{KC_PUBLIC_URL\}/, kc)
        print
    }
' "$template" > "$output"

# Garde-fou : plus aucun ${...} hors commentaires, sinon Kong démarrerait avec une
# config absurde (ex. une clé publique littéralement égale à « ${REALM_RSA_PUBLIC_KEY} »).
if grep -v '^[[:space:]]*#' "$output" | grep -q '\${'; then
    echo "render-config: variable non remplacée dans $output :" >&2
    grep -n '\${' "$output" | grep -v ':[[:space:]]*#' >&2
    exit 1
fi
