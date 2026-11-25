package tn.voltpark.session.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.PositiveOrZero;
import tn.voltpark.session.domain.model.ChargingSession;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Corps de PATCH /api/v1/sessions/{id}. Seul status=STOPPED est accepte.
 * Les mesures sont optionnelles : elles ne servent que si energy-service ne repond pas.
 */
public record StopSessionRequest(
        @NotNull(message = "Le statut cible est obligatoire (STOPPED)")
        ChargingSession.SessionStatus status,

        @PositiveOrZero(message = "L'energie totale doit etre positive")
        BigDecimal totalEnergyKwh,

        @PositiveOrZero(message = "L'energie heures pleines doit etre positive")
        BigDecimal peakKwh,

        @PositiveOrZero(message = "L'energie heures creuses doit etre positive")
        BigDecimal offPeakKwh,

        @PastOrPresent(message = "La fin de charge ne peut pas etre dans le futur")
        Instant chargeCompletedAt
) {}
