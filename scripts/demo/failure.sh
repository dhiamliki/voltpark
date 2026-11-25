#!/bin/sh
# Demonstration des erreurs et des pannes, via l'API Gateway :
#   A. badge bloque : la borne repond par une SOAP Fault, c'est une reponse metier (403) :
#      pas de nouvel essai, pas de PENDING_START, le circuit breaker reste ferme.
#   B. charger-gateway-service arrete : nouveaux essais avec backoff, puis la session passe
#      en PENDING_START ; une fois le service revenu, POST /sessions/{id}/start-attempts la demarre.
#   C. energy-service arrete a l'arret d'une session : la session est cloturee et la facture
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
BLOCKED_ID=$(echo "$BODY" | tr '}' '\n' | grep '"badgeId":"TAG-BLOCKED"' | field id)
[ -n "$DRIVER_ID" ] || fail "aucun conducteur avec le badge $BADGE"
[ -n "$BLOCKED_ID" ] || fail "aucun conducteur avec le badge TAG-BLOCKED"
info "conducteur id=$DRIVER_ID (badge $BADGE), conducteur bloque id=$BLOCKED_ID, borne $CHARGER"

# ---------------------------------------------------------------- A
step "A1. Badge bloque, 3 demarrages : attendu 403 a chaque fois (SOAP Fault BADGE_REFUSED)"
for attempt in 1 2 3; do
    call POST /sessions "{\"driverId\":$BLOCKED_ID,\"chargerId\":\"$CHARGER\"}"
    info "$(echo "$BODY" | field errorCode) : $(echo "$BODY" | detail)"
done

step "A2. Demarrage normal juste apres : attendu ACTIVE (le circuit n'a pas ete ouvert par les refus)"
call POST /sessions "{\"driverId\":$DRIVER_ID,\"chargerId\":\"$CHARGER\"}"
SESSION_ID=$(echo "$BODY" | field id)
info "session id=$SESSION_ID, statut $(echo "$BODY" | field status)"
stop_session "$SESSION_ID" >/dev/null
info "session arretee"

# ---------------------------------------------------------------- B
step "B1. Arret de charger-gateway-service (passerelle SOAP des bornes)"
docker stop charger-gateway-service >/dev/null && info "charger-gateway-service arrete"

step "B2. Demarrage d'une session : attendu 201 avec le statut PENDING_START (3 essais, puis fallback)"
START=$(date +%s)
call POST /sessions "{\"driverId\":$DRIVER_ID,\"chargerId\":\"$CHARGER\"}"
PENDING_ID=$(echo "$BODY" | field id)
info "session id=$PENDING_ID, statut $(echo "$BODY" | field status) (reponse en $(( $(date +%s) - START )) s)"

step "B3. Nouvel essai pendant la panne : attendu 502, la session reste PENDING_START"
call POST "/sessions/$PENDING_ID/start-attempts"
info "$(echo "$BODY" | detail)"

step "B4. Redemarrage de charger-gateway-service"
docker start charger-gateway-service >/dev/null
wait_healthy charger-gateway-service

step "B5. Nouvel essai : POST /sessions/{id}/start-attempts, attendu 200 et ACTIVE"
# le circuit du client SOAP peut encore etre ouvert quelques secondes apres la panne
for attempt in 1 2 3 4 5 6; do
    call POST "/sessions/$PENDING_ID/start-attempts"
    [ "$CODE" = "200" ] && break
    info "pas encore, nouvel essai dans 5 s"
    sleep 5
done
info "session id=$PENDING_ID, statut $(echo "$BODY" | field status), transaction borne $(echo "$BODY" | field transactionId)"
sleep 3
stop_session "$PENDING_ID"
info "statut $(echo "$BODY" | field status), energie $(echo "$BODY" | field totalEnergyKwh) kWh"

# ---------------------------------------------------------------- C
step "C1. Nouvelle session sur $CHARGER, la borne envoie ses mesures"
call POST /sessions "{\"driverId\":$DRIVER_ID,\"chargerId\":\"$CHARGER\"}"
SESSION_ID=$(echo "$BODY" | field id)
STATUS=$(echo "$BODY" | field status)
[ "$STATUS" = "ACTIVE" ] || fail "la session n'a pas demarre ($STATUS)"
info "session id=$SESSION_ID, statut $STATUS"
sleep 6

step "C2. Arret d'energy-service"
docker stop energy-service >/dev/null && info "energy-service arrete"

step "C3. Arret de la session : attendu 200, facture PENDING_ENERGY"
stop_session "$SESSION_ID"
info "statut $(echo "$BODY" | field status)"
call GET "/sessions/$SESSION_ID/invoice"
INVOICE_ID=$(echo "$BODY" | field id)
info "facture id=$INVOICE_ID, statut $(echo "$BODY" | field status), total $(echo "$BODY" | field totalAmount)"

step "C4. Recalcul pendant la panne : attendu 503"
call POST "/invoices/$INVOICE_ID/recalculation"
info "$(echo "$BODY" | detail)"

step "C5. Redemarrage d'energy-service"
docker start energy-service >/dev/null
wait_healthy energy-service

step "C6. Recalcul de la facture : attendu 200, statut ISSUED"
# le client gRPC se reconnecte apres quelques secondes
for attempt in 1 2 3 4 5 6 7 8 9 10 11 12; do
    call POST "/invoices/$INVOICE_ID/recalculation"
    [ "$CODE" = "200" ] && break
    info "pas encore disponible, nouvel essai dans 5 s"
    sleep 5
done
info "statut $(echo "$BODY" | field status), energie $(echo "$BODY" | field totalEnergyKwh) kWh, total $(echo "$BODY" | field totalAmount) $(echo "$BODY" | field currency)"

step "Termine"
