# Fonctions communes des scripts de demonstration (Windows PowerShell 5.1 ou PowerShell 7).
# Variable optionnelle : GATEWAY_URL (par defaut http://localhost:<GATEWAY_HOST_PORT du .env, sinon 8080>).

$RootDir = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$GatewayUrl = $env:GATEWAY_URL
if (-not $GatewayUrl) {
    $port = "8080"
    $envFile = Join-Path $RootDir ".env"
    if (Test-Path $envFile) {
        $line = Get-Content $envFile | Where-Object { $_ -match '^GATEWAY_HOST_PORT=(\d+)' } | Select-Object -Last 1
        if ($line -match '^GATEWAY_HOST_PORT=(\d+)') { $port = $Matches[1] }
    }
    $GatewayUrl = "http://localhost:$port"
}
$Api = "$GatewayUrl/api/v1"

function Write-Step([string]$Text) { Write-Host ""; Write-Host "==> $Text" }
function Write-Info([string]$Text) { Write-Host "    $Text" }
function Stop-Demo([string]$Text) { Write-Host "    ERREUR : $Text"; exit 1 }

# Requete HTTP vers la gateway. Renvoie un objet { Code, Json }.
function Invoke-Api {
    param([string]$Method, [string]$Path, $Body)
    $params = @{ Method = $Method; Uri = "$Api$Path"; UseBasicParsing = $true; ContentType = "application/json" }
    if ($null -ne $Body) { $params.Body = ($Body | ConvertTo-Json -Compress) }
    try {
        $response = Invoke-WebRequest @params
        $code = [int]$response.StatusCode
        $content = $response.Content
    } catch {
        if ($null -eq $_.Exception.Response) { throw }
        $code = [int]$_.Exception.Response.StatusCode
        $content = $_.ErrorDetails.Message
    }
    Write-Info "$Method $Path -> HTTP $code"
    $json = $null
    if ($content) { try { $json = $content | ConvertFrom-Json } catch { } }
    [pscustomobject]@{ Code = $code; Json = $json }
}

# Date ISO-8601 (UTC) dans N secondes.
function Get-IsoIn([int]$Seconds) {
    (Get-Date).ToUniversalTime().AddSeconds($Seconds).ToString("yyyy-MM-ddTHH:mm:ssZ")
}

# Attend qu'un conteneur soit "healthy" (3 minutes au plus).
function Wait-Healthy([string]$Container) {
    for ($i = 0; $i -lt 90; $i++) {
        $status = docker inspect -f "{{.State.Health.Status}}" $Container 2>$null
        if ($status -eq "healthy") { Write-Info "$Container est healthy"; return }
        Start-Sleep -Seconds 2
    }
    Stop-Demo "$Container n'est pas redevenu healthy"
}

# Suivi en direct (SSE) pendant N secondes, une ligne par mesure.
function Watch-Live([string]$SessionId, [int]$Seconds) {
    $event = ""
    curl.exe -s -N --max-time $Seconds -H "Accept: text/event-stream" "$Api/sessions/$SessionId/live" | ForEach-Object {
        if ($_ -match '^event:(.*)') { $event = $Matches[1] }
        elseif ($_ -match '^data:(.*)') {
            $data = $Matches[1] | ConvertFrom-Json
            if ($event -eq "progress") {
                Write-Info "mesure : $($data.powerKw) kW, $($data.energyKwh) kWh, batterie $($data.socPercent) %"
            } else {
                Write-Info "evenement ${event} : $($Matches[1])"
            }
        }
    }
}

function Get-DriverId([string]$Badge) {
    $drivers = (Invoke-Api GET "/drivers").Json
    $driver = $drivers | Where-Object { $_.badgeId -eq $Badge } | Select-Object -First 1
    if (-not $driver) { Stop-Demo "aucun conducteur avec le badge $Badge" }
    $driver.id
}
