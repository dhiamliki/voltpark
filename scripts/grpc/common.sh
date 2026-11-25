#!/bin/sh
# Fonctions communes : lance grpcurl dans un conteneur relie au reseau Docker Compose.
# Variables optionnelles : VOLTPARK_NETWORK, ENERGY_GRPC_TARGET.

NETWORK="${VOLTPARK_NETWORK:-voltpark_voltpark-net}"
TARGET="${ENERGY_GRPC_TARGET:-energy-service:9090}"
SERVICE="voltpark.energy.v1.EnergyService"

grpcurl_run() {
    docker run --rm -i --network "$NETWORK" fullstorydev/grpcurl:v1.9.1 -plaintext "$@"
}

# Derniere session demarree par le simulateur (lue dans ses logs) : session planifiee
# (SIM-<horodatage>) ou session reelle demarree via session-service (UUID).
latest_sim_session() {
    docker logs charger-simulator 2>&1 | sed -n 's/.*Starting simulated session \([^ ]*\) on charger.*/\1/p' | tail -n 1
}
