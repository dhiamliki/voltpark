#!/bin/sh
# Bilan energetique d'une session (unary GetSessionEnergy).
# Usage : ./get-session-energy.sh [session_id]   (par defaut : derniere session du simulateur)
. "$(dirname "$0")/common.sh"

SESSION_ID="${1:-$(latest_sim_session)}"
if [ -z "$SESSION_ID" ]; then
    echo "Aucune session trouvee dans les logs du simulateur." >&2
    exit 1
fi

echo "GetSessionEnergy $SESSION_ID"
grpcurl_run -d "{\"session_id\": \"$SESSION_ID\"}" "$TARGET" "$SERVICE/GetSessionEnergy"
