# Liste les services gRPC exposes par energy-service (grace a la reflection).
. "$PSScriptRoot\common.ps1"

Invoke-Grpcurl -GrpcurlArgs $Target, "list"
Invoke-Grpcurl -GrpcurlArgs $Target, "describe", $Service
