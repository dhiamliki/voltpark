package tn.voltpark.energy.service;

import java.time.Instant;

/** Mesure recue d'une borne, independante du format de transport (gRPC). */
public record NewReading(
        String sessionId,
        String chargerId,
        Instant timestamp,
        double powerKw,
        double energyKwh,
        int socPercent) {
}
