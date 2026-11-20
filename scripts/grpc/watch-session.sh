#!/bin/sh
# Suivi en direct d'une session (server streaming WatchSession).
# Usage : ./watch-session.sh [session_id] [duree_max_secondes]
# Lancer pendant une session du simulateur pour voir les mesures arriver chaque seconde.
. "$(dirname "$0")/common.sh"

SESSION_ID="${1:-$(latest_sim_session)}"
MAX_TIME="${2:-20}"
if [ -z "$SESSION_ID" ]; then
    echo "Aucune session trouvee dans les logs du simulateur." >&2
    exit 1
fi

echo "WatchSession $SESSION_ID (max ${MAX_TIME}s)"
grpcurl_run -max-time "$MAX_TIME" -d "{\"session_id\": \"$SESSION_ID\"}" "$TARGET" "$SERVICE/WatchSession"
