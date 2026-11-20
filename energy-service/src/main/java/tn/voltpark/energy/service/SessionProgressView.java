package tn.voltpark.energy.service;

import java.time.Instant;

/** Etat courant d'une session : derniere mesure, estimation du temps restant et etat de cloture. */
public record SessionProgressView(
        String sessionId,
        Instant timestamp,
        double powerKw,
        double energyKwh,
        int socPercent,
        int estimatedMinutesRemaining,
        boolean closed) {
}
