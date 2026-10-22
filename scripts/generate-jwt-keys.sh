#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
KEY_DIR="${JWT_KEY_DIR:-${SCRIPT_DIR}/../.local/jwt}"

umask 077
mkdir -p "${KEY_DIR}"
chmod 700 "${KEY_DIR}"
if [[ ! -f "${KEY_DIR}/private.pem" ]]; then
    openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "${KEY_DIR}/private.pem"
fi
openssl pkey -in "${KEY_DIR}/private.pem" -pubout -out "${KEY_DIR}/public.pem"
# The enclosing host directory remains private; bind-mounted files must be readable by the app's container UID.
chmod 644 "${KEY_DIR}/private.pem" "${KEY_DIR}/public.pem"
