#!/bin/sh
# Fonctions communes des scripts de demonstration (curl et sed uniquement, sans jq).
# Variable optionnelle : GATEWAY_URL (par defaut http://localhost:<GATEWAY_HOST_PORT du .env, sinon 8080>).

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
if [ -z "$GATEWAY_URL" ]; then
    PORT=$(sed -n 's/^GATEWAY_HOST_PORT=\([0-9]*\).*/\1/p' "$ROOT_DIR/.env" 2>/dev/null | tail -n 1)
    GATEWAY_URL="http://localhost:${PORT:-8080}"
fi
API="$GATEWAY_URL/api/v1"

step() { printf '\n==> %s\n' "$*"; }
info() { printf '    %s\n' "$*"; }
fail() { printf '    ERREUR : %s\n' "$*" >&2; exit 1; }

# Requete HTTP vers la gateway : call METHODE CHEMIN [JSON]
# Affiche le code HTTP ; le code est dans $CODE, le corps dans $BODY et l'en-tete Location dans $LOCATION.
call() {
    tmp=$(mktemp)
    hdr=$(mktemp)
    if [ -n "$3" ]; then
        CODE=$(curl -s -D "$hdr" -o "$tmp" -w '%{http_code}' -X "$1" "$API$2" -H 'Content-Type: application/json' -d "$3")
    else
        CODE=$(curl -s -D "$hdr" -o "$tmp" -w '%{http_code}' -X "$1" "$API$2")
    fi
    BODY=$(cat "$tmp")
    LOCATION=$(tr -d '\r' < "$hdr" | sed -n 's/^[Ll]ocation: *//p')
    rm -f "$tmp" "$hdr"
    if [ -n "$LOCATION" ]; then
        info "$1 $2 -> HTTP $CODE (Location: $LOCATION)"
    else
        info "$1 $2 -> HTTP $CODE"
    fi
}

# Arret d'une session : PATCH /sessions/{id} {"status":"STOPPED"}
stop_session() {
    call PATCH "/sessions/$1" '{"status":"STOPPED"}'
}

# Valeur du premier champ JSON de ce nom : echo "$BODY" | field id
field() {
    tr ',{' '\n\n' | sed -n "s/^ *\"$1\": *\"\{0,1\}\([^\"}]*\)\"\{0,1\}.*/\1/p" | head -n 1
}

# Message "detail" d'une erreur ProblemDetail (peut contenir des virgules).
detail() {
    sed -n 's/.*"detail":"\([^"]*\)".*/\1/p'
}

# Date ISO-8601 (UTC) dans N secondes.
iso_in() {
    date -u -d "+$1 seconds" +%Y-%m-%dT%H:%M:%SZ
}

# Attend qu'un conteneur soit "healthy" (3 minutes au plus).
wait_healthy() {
    i=0
    until [ "$(docker inspect -f '{{.State.Health.Status}}' "$1" 2>/dev/null)" = "healthy" ]; do
        i=$((i + 1))
        [ "$i" -gt 90 ] && fail "$1 n'est pas redevenu healthy"
        sleep 2
    done
    info "$1 est healthy"
}

# Suivi en direct (SSE) pendant N secondes, une ligne par mesure.
follow_live() {
    curl -s -N --max-time "$2" -H 'Accept: text/event-stream' "$API/sessions/$1/live" | while read -r line; do
        case "$line" in
            event:*) EVENT=${line#event:} ;;
            data:*)
                if [ "$EVENT" = "progress" ]; then
                    p=$(echo "$line" | field powerKw); e=$(echo "$line" | field energyKwh); s=$(echo "$line" | field socPercent)
                    info "mesure : ${p} kW, ${e} kWh, batterie ${s} %"
                else
                    info "evenement $EVENT : ${line#data:}"
                fi ;;
        esac
    done
}
