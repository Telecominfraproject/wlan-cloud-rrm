#!/usr/bin/env bash
set -e

if [ "$SELFSIGNED_CERTS" = 'true' ]; then
    update-ca-certificates
fi

exec "$@"
