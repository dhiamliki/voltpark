#!/bin/sh
# Demonstration des pannes, via l'API Gateway :
#   A. charger-gateway-service arrete : le demarrage d'une session passe en PENDING_START
#      (timeouts, retry et circuit breaker du client SOAP, puis fallback).
#   B. energy-service arrete a l'arret d'une session : la session est cloturee et la facture
#      est creee en PENDING_ENERGY, puis recalculee quand energy-service est revenu.
#
# Usage : ./failure.sh
# Variables : GATEWAY_URL, DEMO_BADGE (TAG-002 par defaut), DEMO_CHARGER (CP-TUN-002 par defaut)
. "$(dirname "$0")/common.sh"

BADGE="${DEMO_BADGE:-TAG-002}"
CHARGER="${DEMO_CHARGER:-CP-TUN-002}"
info "Gateway : $GATEWAY_URL"

call GET /drivers
DRIVER_ID=$(echo "$BODY" | tr '}' '\n' | grep "\"badgeId\":\"$BADGE\"" | field id)
[ -n "$DRIVER_ID" ] || fail "aucun conducteur avec le badge $BADGE"
info "conducteur id=$DRIVER_ID, borne $CHARGER"

# ---------------------------------------------------------------- A
step "A1. Arret de charger-gateway-service (passerelle SOAP des bornes)"
docker stop charger-gateway-service >/dev/null && info "charger-gateway-service arrete"

step "A2. Demarrage d'une session : attendu 201 avec le statut PENDING_START (fallback)"
START=$(date +%s)
call POST /sessions "{\"driverId\":$DRIVER_ID,\"chargerId\":\"$CHARGER\"}"
PENDING_ID=$(echo "$BODY" | field id)
info "session id=$PENDING_ID, statut $(echo "$BODY" | field status) (reponse en $(( $(date +%s) - START )) s)"

step "A3. Redemarrage de charger-gateway-service"
docker start charger-gateway-service >/dev/null
wait_healthy charger-gateway-service

step "A4. Cloture de la session en attente (aucune transaction ouverte sur la borne)"
call POST "/sessions/$PENDING_ID/stop"
info "statut $(echo "$BODY" | field status)"

# ---------------------------------------------------------------- B
step "B1. Nouvelle session sur $CHARGER, la borne envoie ses mesures"
# le circuit du client SOAP peut encore etre ouvert quelques secondes apres la panne A
for attempt in 1 2 3 4 5 6; do
    call POST /sessions "{\"driverId\":$DRIVER_ID,\"chargerId\":\"$CHARGER\"}"
    STATUS=$(echo "$BODY" | field status)
    SESSION_ID=$(echo "$BODY" | field id)
    [ "$STATUS" = "ACTIVE" ] && break
    info "statut $STATUS, nouvel essai dans 5 s"
    [ -n "$SESSION_ID" ] && call POST "/sessions/$SESSION_ID/stop" >/dev/null
    sleep 5
done
[ "$STATUS" = "ACTIVE" ] || fail "la session n'a pas pu demarrer"
info "session id=$SESSION_ID, statut $STATUS"
sleep 6

step "B2. Arret d'energy-service"
docker stop energy-service >/dev/null && info "energy-service arrete"

step "B3. Arret de la session : attendu 200, facture PENDING_ENERGY"
call POST "/sessions/$SESSION_ID/stop"
info "statut $(echo "$BODY" | field status)"
call GET "/sessions/$SESSION_ID/invoice"
INVOICE_ID=$(echo "$BODY" | field id)
info "facture id=$INVOICE_ID, statut $(echo "$BODY" | field status), total $(echo "$BODY" | field totalAmount)"

step "B4. Recalcul pendant la panne : attendu 503"
call POST "/invoices/$INVOICE_ID/recalculation"
info "$(echo "$BODY" | field detail)"

step "B5. Redemarrage d'energy-service"
docker start energy-service >/dev/null
wait_healthy energy-service

step "B6. Recalcul de la facture : attendu 200, statut ISSUED"
# le client gRPC se reconnecte apres quelques secondes
for attempt in 1 2 3 4 5 6 7 8 9 10 11 12; do
    call POST "/invoices/$INVOICE_ID/recalculation"
    [ "$CODE" = "200" ] && break
    info "pas encore disponible, nouvel essai dans 5 s"
    sleep 5
done
info "statut $(echo "$BODY" | field status), energie $(echo "$BODY" | field totalEnergyKwh) kWh, total $(echo "$BODY" | field totalAmount) $(echo "$BODY" | field currency)"

step "Termine"
