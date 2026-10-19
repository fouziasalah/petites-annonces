#!/usr/bin/env bash
# Affiche le header et la charge utile d'un JWT (sans vérifier la signature).
# usage : scripts/decode-jwt.sh <jwt>      ou      scripts/get-token.sh modo | scripts/decode-jwt.sh
set -euo pipefail
source "$(dirname "$0")/lib/common.sh"

token="${1:-$(cat)}"
echo "# header";  b64url_decode "$(printf '%s' "$token" | cut -d. -f1)"; echo
echo "# payload"; jwt_payload "$token"; echo
