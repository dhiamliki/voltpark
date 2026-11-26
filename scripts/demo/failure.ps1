# Demonstration des erreurs et des pannes, via l'API Gateway :
#   A. badge bloque : la borne repond par une SOAP Fault, c'est une reponse metier (403) :
#      pas de nouvel essai, pas de PENDING_START, le circuit breaker reste ferme.
#   B. charger-gateway-service arrete : nouveaux essais avec backoff, puis la session passe
#      en PENDING_START ; une fois le service revenu, POST /sessions/{id}/start-attempts la demarre.
#   C. energy-service arrete a l'arret d'une session : la session est cloturee et la facture
#      est creee en PENDING_ENERGY, puis recalculee quand energy-service est revenu.
#
# Usage : .\failure.ps1
# Variables : GATEWAY_URL, DEMO_BADGE (TAG-002 par defaut), DEMO_CHARGER (CP-175672-2 par defaut)
. "$PSScriptRoot\common.ps1"

$Badge = if ($env:DEMO_BADGE) { $env:DEMO_BADGE } else { "TAG-002" }
$Charger = if ($env:DEMO_CHARGER) { $env:DEMO_CHARGER } else { "CP-175672-2" }
Write-Info "Gateway : $GatewayUrl"
$DriverId = Get-DriverId $Badge
$BlockedId = Get-DriverId "TAG-BLOCKED"
Write-Info "conducteur id=$DriverId (badge $Badge), conducteur bloque id=$BlockedId, borne $Charger"

# ---------------------------------------------------------------- A
Write-Step "A1. Badge bloque, 3 demarrages : attendu 403 a chaque fois (SOAP Fault BADGE_REFUSED)"
for ($attempt = 1; $attempt -le 3; $attempt++) {
    $r = Invoke-Api POST "/sessions" @{ driverId = $BlockedId; chargerId = $Charger }
    Write-Info "$($r.Json.errorCode) : $($r.Json.detail)"
}

Write-Step "A2. Demarrage normal juste apres : attendu ACTIVE (le circuit n'a pas ete ouvert par les refus)"
$r = Invoke-Api POST "/sessions" @{ driverId = $DriverId; chargerId = $Charger }
Write-Info "session id=$($r.Json.id), statut $($r.Json.status)"
Stop-Session $r.Json.id | Out-Null
Write-Info "session arretee"

# ---------------------------------------------------------------- B
Write-Step "B1. Arret de charger-gateway-service (passerelle SOAP des bornes)"
docker stop charger-gateway-service | Out-Null
Write-Info "charger-gateway-service arrete"

Write-Step "B2. Demarrage d'une session : attendu 201 avec le statut PENDING_START (3 essais, puis fallback)"
$watch = [System.Diagnostics.Stopwatch]::StartNew()
$r = Invoke-Api POST "/sessions" @{ driverId = $DriverId; chargerId = $Charger }
$PendingId = $r.Json.id
Write-Info "session id=$PendingId, statut $($r.Json.status) (reponse en $([math]::Round($watch.Elapsed.TotalSeconds, 1)) s)"

Write-Step "B3. Nouvel essai pendant la panne : attendu 502, la session reste PENDING_START"
$r = Invoke-Api POST "/sessions/$PendingId/start-attempts"
Write-Info "$($r.Json.detail)"

Write-Step "B4. Redemarrage de charger-gateway-service"
docker start charger-gateway-service | Out-Null
Wait-Healthy charger-gateway-service

Write-Step "B5. Nouvel essai : POST /sessions/{id}/start-attempts, attendu 200 et ACTIVE"
# le circuit du client SOAP peut encore etre ouvert quelques secondes apres la panne
for ($attempt = 1; $attempt -le 6; $attempt++) {
    $r = Invoke-Api POST "/sessions/$PendingId/start-attempts"
    if ($r.Code -eq 200) { break }
    Write-Info "pas encore, nouvel essai dans 5 s"
    Start-Sleep -Seconds 5
}
Write-Info "session id=$PendingId, statut $($r.Json.status), transaction borne $($r.Json.transactionId)"
Start-Sleep -Seconds 3
$r = Stop-Session $PendingId
Write-Info "statut $($r.Json.status), energie $($r.Json.totalEnergyKwh) kWh"

# ---------------------------------------------------------------- C
Write-Step "C1. Nouvelle session sur $Charger, la borne envoie ses mesures"
$r = Invoke-Api POST "/sessions" @{ driverId = $DriverId; chargerId = $Charger }
$SessionId = $r.Json.id
if ($r.Json.status -ne "ACTIVE") { Stop-Demo "la session n'a pas demarre ($($r.Json.status))" }
Write-Info "session id=$SessionId, statut $($r.Json.status)"
Start-Sleep -Seconds 6

Write-Step "C2. Arret d'energy-service"
docker stop energy-service | Out-Null
Write-Info "energy-service arrete"

Write-Step "C3. Arret de la session : attendu 200, facture PENDING_ENERGY"
$r = Stop-Session $SessionId
Write-Info "statut $($r.Json.status)"
$i = (Invoke-Api GET "/sessions/$SessionId/invoice").Json
$InvoiceId = $i.id
Write-Info "facture id=$InvoiceId, statut $($i.status), total $($i.totalAmount)"

Write-Step "C4. Recalcul pendant la panne : attendu 503"
$r = Invoke-Api POST "/invoices/$InvoiceId/recalculation"
Write-Info "$($r.Json.detail)"

Write-Step "C5. Redemarrage d'energy-service"
docker start energy-service | Out-Null
Wait-Healthy energy-service

Write-Step "C6. Recalcul de la facture : attendu 200, statut ISSUED"
# le client gRPC se reconnecte apres quelques secondes
for ($attempt = 1; $attempt -le 12; $attempt++) {
    $r = Invoke-Api POST "/invoices/$InvoiceId/recalculation"
    if ($r.Code -eq 200) { break }
    Write-Info "pas encore disponible, nouvel essai dans 5 s"
    Start-Sleep -Seconds 5
}
Write-Info "statut $($r.Json.status), energie $($r.Json.totalEnergyKwh) kWh, total $($r.Json.totalAmount) $($r.Json.currency)"

Write-Step "Termine"
