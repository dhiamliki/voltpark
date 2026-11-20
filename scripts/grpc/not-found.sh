#!/bin/sh
# Appel sur une session inconnue : energy-service doit repondre NOT_FOUND.
. "$(dirname "$0")/common.sh"

echo "GetSessionEnergy UNKNOWN-SESSION (code attendu : NotFound)"
grpcurl_run -d '{"session_id": "UNKNOWN-SESSION"}' "$TARGET" "$SERVICE/GetSessionEnergy"
