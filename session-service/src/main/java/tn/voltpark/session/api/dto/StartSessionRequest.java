package tn.voltpark.session.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record StartSessionRequest(
        @NotNull(message = "L'identifiant du conducteur est obligatoire")
        Long driverId,

        @NotBlank(message = "L'identifiant de la borne est obligatoire")
        String chargerId,

        Long reservationId,

        Integer connectorId
) {}
