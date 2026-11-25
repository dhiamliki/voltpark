# Demonstration de bout en bout, uniquement via l'API Gateway :
# conducteur -> stations et bornes -> reservation -> demarrage (SOAP vers la borne, mesures gRPC)
# -> suivi en direct (SSE) -> arret -> facture.
#
# Usage : .\full-flow.ps1 [-LiveSeconds 15]
# Variables : GATEWAY_URL, DEMO_BADGE (TAG-002 par defaut), DEMO_CHARGER (CP-TUN-001 par defaut)
param([int]$LiveSeconds = 15)
. "$PSScriptRoot\common.ps1"

$Badge = if ($env:DEMO_BADGE) { $env:DEMO_BADGE } else { "TAG-002" }
$Charger = if ($env:DEMO_CHARGER) { $env:DEMO_CHARGER } else { "CP-TUN-001" }
Write-Info "Gateway : $GatewayUrl"

Write-Step "1. Choix du conducteur (badge $Badge, autorise par la borne)"
$DriverId = Get-DriverId $Badge
Write-Info "conducteur id=$DriverId"

Write-Step "2. Stations et bornes"
foreach ($s in (Invoke-Api GET "/stations").Json) { Write-Info "station $($s.id) : $($s.name)" }
foreach ($c in (Invoke-Api GET "/chargers").Json) { Write-Info "borne $($c.id) : $($c.status)" }

Write-Step "3. Reservation de la borne $Charger (POST /reservations : 201 + Location)"
$r = Invoke-Api POST "/reservations" @{ driverId = $DriverId; chargerId = $Charger; startTime = (Get-IsoIn 5); endTime = (Get-IsoIn 1800) }
$ReservationId = $null
$start = @{ driverId = $DriverId; chargerId = $Charger }
if ($r.Code -eq 201) {
    $ReservationId = $r.Json.id
    $start.reservationId = $ReservationId
    Write-Info "reservation id=$ReservationId, statut $($r.Json.status)"
} else {
    Write-Info "reservation refusee ($($r.Json.detail)) : on continue sans reservation"
}

Write-Step "4. Demarrage de la session (POST /sessions -> SOAP RemoteStartTransaction -> borne)"
$r = Invoke-Api POST "/sessions" $start
if ($r.Code -ne 201) { Stop-Demo "demarrage refuse : $($r.Json.detail)" }
$SessionId = $r.Json.id
Write-Info "session id=$SessionId"
Write-Info "statut $($r.Json.status), transaction borne $($r.Json.transactionId)"
if ($ReservationId) { Write-Info "reservation : statut $((Invoke-Api GET "/reservations/$ReservationId").Json.status)" }

Write-Step "5. Suivi en direct pendant $LiveSeconds s (GET /sessions/{id}/live, relais du flux gRPC WatchSession)"
Start-Sleep -Seconds 2
Watch-Live $SessionId $LiveSeconds

Write-Step "6. Arret : PATCH /sessions/{id} {`"status`":`"STOPPED`"} (SOAP RemoteStopTransaction, puis GetSessionEnergy en gRPC)"
$r = Stop-Session $SessionId
Write-Info "statut $($r.Json.status), energie $($r.Json.totalEnergyKwh) kWh"

Write-Step "7. Facture"
$i = (Invoke-Api GET "/sessions/$SessionId/invoice").Json
Write-Info "facture id=$($i.id), statut $($i.status)"
Write-Info "energie : $($i.totalEnergyKwh) kWh (HP $($i.peakKwh), HC $($i.offPeakKwh))"
Write-Info "cout HP $($i.peakEnergyCost) + HC $($i.offPeakEnergyCost) + occupation $($i.idleFee)"
Write-Info "TOTAL : $($i.totalAmount) $($i.currency)"

if ($ReservationId) {
    Write-Step "8. Reservation terminee avec la session (le creneau est libere)"
    Write-Info "reservation : statut $((Invoke-Api GET "/reservations/$ReservationId").Json.status)"
}

Write-Step "Termine"
