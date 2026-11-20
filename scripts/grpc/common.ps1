# Fonctions communes : lance grpcurl dans un conteneur relie au reseau Docker Compose.
# Variables optionnelles : VOLTPARK_NETWORK, ENERGY_GRPC_TARGET.

$Network = if ($env:VOLTPARK_NETWORK) { $env:VOLTPARK_NETWORK } else { "voltpark_voltpark-net" }
$Target = if ($env:ENERGY_GRPC_TARGET) { $env:ENERGY_GRPC_TARGET } else { "energy-service:9090" }
$Service = "voltpark.energy.v1.EnergyService"

# Avant PowerShell 7.3, les guillemets a l'interieur d'un argument passe a un programme
# externe sont supprimes : il faut les echapper avec un antislash.
$EscapeQuotes = -not ($PSVersionTable.PSVersion -ge [version]"7.3" -and $PSNativeCommandArgumentPassing -ne "Legacy")

function Invoke-Grpcurl {
    param(
        [string]$Json,
        [Parameter(ValueFromRemainingArguments = $true)][string[]]$GrpcurlArgs
    )
    $options = @()
    if ($Json) {
        if ($EscapeQuotes) { $Json = $Json.Replace('"', '\"') }
        $options = @("-d", $Json)
    }
    docker run --rm --network $Network fullstorydev/grpcurl:v1.9.1 -plaintext @options @GrpcurlArgs
}

# Derniere session creee par le simulateur (lue dans ses logs).
function Get-LatestSimSession {
    $ids = docker logs charger-simulator 2>&1 | Select-String -Pattern 'SIM-\d+' -AllMatches |
        ForEach-Object { $_.Matches } | ForEach-Object { $_.Value }
    if ($ids) { @($ids)[-1] }
}
