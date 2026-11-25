package tn.voltpark.session.api.dto;

import tn.voltpark.energy.grpc.v1.SessionProgress;

import java.time.Instant;

/** Evenement "progress" du flux GET /api/v1/sessions/{id}/live. */
public record LiveProgressResponse(
        String sessionId,
        Instant timestamp,
        double powerKw,
        double energyKwh,
        int socPercent,
        // -1 si l'estimation est impossible
        int estimatedMinutesRemaining
) {
    public static LiveProgressResponse from(SessionProgress progress) {
        return new LiveProgressResponse(
                progress.getSessionId(),
                Instant.ofEpochSecond(progress.getTimestamp().getSeconds(), progress.getTimestamp().getNanos()),
                progress.getPowerKw(),
                progress.getEnergyKwh(),
                progress.getSocPercent(),
                progress.getEstimatedMinutesRemaining());
    }
}
