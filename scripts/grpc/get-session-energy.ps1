# Bilan energetique d'une session (unary GetSessionEnergy).
# Usage : .\get-session-energy.ps1 [-SessionId <id>]   (par defaut : derniere session du simulateur)
param([string]$SessionId)
. "$PSScriptRoot\common.ps1"

if (-not $SessionId) { $SessionId = Get-LatestSimSession }
if (-not $SessionId) { Write-Error "Aucune session trouvee dans les logs du simulateur."; exit 1 }

Write-Output "GetSessionEnergy $SessionId"
Invoke-Grpcurl -Json "{`"session_id`": `"$SessionId`"}" -GrpcurlArgs $Target, "$Service/GetSessionEnergy"
