# Suivi en direct d'une session (server streaming WatchSession).
# Usage : .\watch-session.ps1 [-SessionId <id>] [-MaxTime <secondes>]
# Lancer pendant une session du simulateur pour voir les mesures arriver chaque seconde.
param([string]$SessionId, [int]$MaxTime = 20)
. "$PSScriptRoot\common.ps1"

if (-not $SessionId) { $SessionId = Get-LatestSimSession }
if (-not $SessionId) { Write-Error "Aucune session trouvee dans les logs du simulateur."; exit 1 }

Write-Output "WatchSession $SessionId (max ${MaxTime}s)"
Invoke-Grpcurl -Json "{`"session_id`": `"$SessionId`"}" -GrpcurlArgs "-max-time", $MaxTime, $Target, "$Service/WatchSession"
