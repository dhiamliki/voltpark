# Demonstration des pannes, via l'API Gateway :
#   A. charger-gateway-service arrete : le demarrage d'une session passe en PENDING_START
#      (timeouts, retry et circuit breaker du client SOAP, puis fallback).
#   B. energy-service arrete a l'arret d'une session : la session est cloturee et la facture
#      est creee en PENDING_ENERGY, puis recalculee quand energy-service est revenu.
#
# Usage : .\failure.ps1
# Variables : GATEWAY_URL, DEMO_BADGE (TAG-002 par defaut), DEMO_CHARGER (CP-TUN-002 par defaut)
. "$PSScriptRoot\common.ps1"

$Badge = if ($env:DEMO_BADGE) { $env:DEMO_BADGE } else { "TAG-002" }
$Charger = if ($env:DEMO_CHARGER) { $env:DEMO_CHARGER } else { "CP-TUN-002" }
Write-Info "Gateway : $GatewayUrl"
$DriverId = Get-DriverId $Badge
Write-Info "conducteur id=$DriverId, borne $Charger"

# ---------------------------------------------------------------- A
Write-Step "A1. Arret de charger-gateway-service (passerelle SOAP des bornes)"
docker stop charger-gateway-service | Out-Null
Write-Info "charger-gateway-service arrete"

Write-Step "A2. Demarrage d'une session : attendu 201 avec le statut PENDING_START (fallback)"
$watch = [System.Diagnostics.Stopwatch]::StartNew()
$r = Invoke-Api POST "/sessions" @{ driverId = $DriverId; chargerId = $Charger }
$PendingId = $r.Json.id
Write-Info "session id=$PendingId, statut $($r.Json.status) (reponse en $([math]::Round($watch.Elapsed.TotalSeconds, 1)) s)"

Write-Step "A3. Redemarrage de charger-gateway-service"
docker start charger-gateway-service | Out-Null
Wait-Healthy charger-gateway-service

Write-Step "A4. Cloture de la session en attente (aucune transaction ouverte sur la borne)"
$r = Invoke-Api POST "/sessions/$PendingId/stop"
Write-Info "statut $($r.Json.status)"

# ---------------------------------------------------------------- B
Write-Step "B1. Nouvelle session sur $Charger, la borne envoie ses mesures"
# le circuit du client SOAP peut encore etre ouvert quelques secondes apres la panne A
$status = ""
for ($attempt = 1; $attempt -le 6; $attempt++) {
    $r = Invoke-Api POST "/sessions" @{ driverId = $DriverId; chargerId = $Charger }
    $status = $r.Json.status
    $SessionId = $r.Json.id
    if ($status -eq "ACTIVE") { break }
    Write-Info "statut $status, nouvel essai dans 5 s"
    if ($SessionId) { Invoke-Api POST "/sessions/$SessionId/stop" | Out-Null }
    Start-Sleep -Seconds 5
}
if ($status -ne "ACTIVE") { Stop-Demo "la session n'a pas pu demarrer" }
Write-Info "session id=$SessionId, statut $status"
Start-Sleep -Seconds 6

Write-Step "B2. Arret d'energy-service"
docker stop energy-service | Out-Null
Write-Info "energy-service arrete"

Write-Step "B3. Arret de la session : attendu 200, facture PENDING_ENERGY"
$r = Invoke-Api POST "/sessions/$SessionId/stop"
Write-Info "statut $($r.Json.status)"
$i = (Invoke-Api GET "/sessions/$SessionId/invoice").Json
$InvoiceId = $i.id
Write-Info "facture id=$InvoiceId, statut $($i.status), total $($i.totalAmount)"

Write-Step "B4. Recalcul pendant la panne : attendu 503"
$r = Invoke-Api POST "/invoices/$InvoiceId/recalculation"
Write-Info "$($r.Json.detail)"

Write-Step "B5. Redemarrage d'energy-service"
docker start energy-service | Out-Null
Wait-Healthy energy-service

Write-Step "B6. Recalcul de la facture : attendu 200, statut ISSUED"
# le client gRPC se reconnecte apres quelques secondes
for ($attempt = 1; $attempt -le 12; $attempt++) {
    $r = Invoke-Api POST "/invoices/$InvoiceId/recalculation"
    if ($r.Code -eq 200) { break }
    Write-Info "pas encore disponible, nouvel essai dans 5 s"
    Start-Sleep -Seconds 5
}
Write-Info "statut $($r.Json.status), energie $($r.Json.totalEnergyKwh) kWh, total $($r.Json.totalAmount) $($r.Json.currency)"

Write-Step "Termine"
