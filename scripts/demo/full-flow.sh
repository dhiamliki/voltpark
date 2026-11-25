#!/bin/sh
# Demonstration de bout en bout, uniquement via l'API Gateway :
# conducteur -> stations et bornes -> reservation -> demarrage (SOAP vers la borne, mesures gRPC)
# -> suivi en direct (SSE) -> arret -> facture.
#
# Usage : ./full-flow.sh [duree_du_suivi_en_secondes]   (15 par defaut)
# Variables : GATEWAY_URL, DEMO_BADGE (TAG-002 par defaut), DEMO_CHARGER (CP-TUN-001 par defaut)
. "$(dirname "$0")/common.sh"

LIVE_SECONDS="${1:-15}"
BADGE="${DEMO_BADGE:-TAG-002}"
CHARGER="${DEMO_CHARGER:-CP-TUN-001}"
info "Gateway : $GATEWAY_URL"

step "1. Choix du conducteur (badge $BADGE, autorise par la borne)"
call GET /drivers
DRIVER_ID=$(echo "$BODY" | tr '}' '\n' | grep "\"badgeId\":\"$BADGE\"" | field id)
[ -n "$DRIVER_ID" ] || fail "aucun conducteur avec le badge $BADGE"
info "conducteur id=$DRIVER_ID"

step "2. Stations et bornes"
call GET /stations
echo "$BODY" | tr '}' '\n' | sed -n 's/.*"id":\([0-9]*\),"name":"\([^"]*\)".*/    station \1 : \2/p'
call GET /chargers
echo "$BODY" | tr '}' '\n' | sed -n 's/.*"id":"\([^"]*\)".*"status":"\([^"]*\)".*/    borne \1 : \2/p'

step "3. Reservation de la borne $CHARGER"
call POST /reservations "{\"driverId\":$DRIVER_ID,\"chargerId\":\"$CHARGER\",\"startTime\":\"$(iso_in 5)\",\"endTime\":\"$(iso_in 1800)\"}"
RESERVATION_ID=""
if [ "$CODE" = "201" ]; then
    RESERVATION_ID=$(echo "$BODY" | field id)
    info "reservation id=$RESERVATION_ID, statut $(echo "$BODY" | field status)"
    RESERVATION_JSON=",\"reservationId\":$RESERVATION_ID"
else
    info "reservation refusee ($(echo "$BODY" | detail)) : on continue sans reservation"
    RESERVATION_JSON=""
fi

step "4. Demarrage de la session (session-service -> SOAP RemoteStartTransaction -> borne)"
call POST /sessions "{\"driverId\":$DRIVER_ID,\"chargerId\":\"$CHARGER\"$RESERVATION_JSON}"
[ "$CODE" = "201" ] || fail "demarrage refuse : $BODY"
SESSION_ID=$(echo "$BODY" | field id)
info "session id=$SESSION_ID"
info "statut $(echo "$BODY" | field status), transaction borne $(echo "$BODY" | field transactionId)"

step "5. Suivi en direct pendant ${LIVE_SECONDS} s (GET /sessions/{id}/live, relais du flux gRPC WatchSession)"
sleep 2
follow_live "$SESSION_ID" "$LIVE_SECONDS"

step "6. Arret de la session (SOAP RemoteStopTransaction, puis GetSessionEnergy en gRPC)"
call POST "/sessions/$SESSION_ID/stop"
info "statut $(echo "$BODY" | field status), energie $(echo "$BODY" | field totalEnergyKwh) kWh"

step "7. Facture"
call GET "/sessions/$SESSION_ID/invoice"
info "facture id=$(echo "$BODY" | field id), statut $(echo "$BODY" | field status)"
info "energie : $(echo "$BODY" | field totalEnergyKwh) kWh (HP $(echo "$BODY" | field peakKwh), HC $(echo "$BODY" | field offPeakKwh))"
info "cout HP $(echo "$BODY" | field peakEnergyCost) + HC $(echo "$BODY" | field offPeakEnergyCost) + occupation $(echo "$BODY" | field idleFee)"
info "TOTAL : $(echo "$BODY" | field totalAmount) $(echo "$BODY" | field currency)"

if [ -n "$RESERVATION_ID" ]; then
    step "8. Liberation du creneau reserve (sinon il reste reserve pour la prochaine demonstration)"
    call DELETE "/reservations/$RESERVATION_ID"
fi

step "Termine"
