#!/bin/sh
# Liste les services gRPC exposes par energy-service (grace a la reflection).
. "$(dirname "$0")/common.sh"

grpcurl_run "$TARGET" list
grpcurl_run "$TARGET" describe "$SERVICE"
