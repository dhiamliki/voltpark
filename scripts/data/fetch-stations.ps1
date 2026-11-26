# Telecharge les bornes de recharge de Tunisie depuis l'API Open Charge Map (v3)
# et regenere data/stations-tn.json (voir data/README.md).
#
# Usage : .\scripts\data\fetch-stations.ps1
# La cle API est lue dans la variable d'environnement OCM_API_KEY, sinon dans le .env local
# (jamais dans un fichier versionne). Elle est envoyee dans l'en-tete X-API-Key.
# Ensuite : recopier data/stations-tn.json dans les services (voir contracts/README.md).
$ErrorActionPreference = "Stop"
$Root = Resolve-Path "$PSScriptRoot\..\.."

$Key = $env:OCM_API_KEY
if (-not $Key -and (Test-Path "$Root\.env")) {
    $line = Get-Content "$Root\.env" | Where-Object { $_ -match '^\s*OCM_API_KEY\s*=' } | Select-Object -First 1
    if ($line) { $Key = ($line -split '=', 2)[1].Trim().Trim('"') }
}
if (-not $Key) {
    Write-Error "OCM_API_KEY absent (variable d'environnement ou .env). Cle gratuite : https://openchargemap.org/site/developerinfo"
}

$Raw = Join-Path ([System.IO.Path]::GetTempPath()) "voltpark-ocm-poi.json"
$Url = "https://api.openchargemap.io/v3/poi/?output=json&countrycode=TN&maxresults=500&compact=false&verbose=false"
Write-Host "Telechargement : $Url"
Invoke-WebRequest -Uri $Url -Headers @{ "X-API-Key" = $Key } -UserAgent "voltpark-data-script" -OutFile $Raw -UseBasicParsing

$env:OCM_METHOD = "api"
node "$Root\scripts\data\normalize-stations.mjs" $Raw "$Root\data\stations-tn.json"
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
Remove-Item $Raw
