# Appel sur une session inconnue : energy-service doit repondre NOT_FOUND.
. "$PSScriptRoot\common.ps1"

Write-Output "GetSessionEnergy UNKNOWN-SESSION (code attendu : NotFound)"
Invoke-Grpcurl -Json '{"session_id": "UNKNOWN-SESSION"}' -GrpcurlArgs $Target, "$Service/GetSessionEnergy"
