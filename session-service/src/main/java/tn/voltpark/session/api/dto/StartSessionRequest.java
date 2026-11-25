package tn.voltpark.session.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record StartSessionRequest(
        @NotNull(message = "L'identifiant du conducteur est obligatoire")
        @Positive(message = "L'identifiant du conducteur doit etre positif")
        Long driverId,

        @NotBlank(message = "L'identifiant de la borne est obligatoire")
        String chargerId,

        @Positive(message = "L'identifiant de la reservation doit etre positif")
        Long reservationId,

        @Min(value = 1, message = "Le connecteur doit etre compris entre 1 et 10")
        @Max(value = 10, message = "Le connecteur doit etre compris entre 1 et 10")
        Integer connectorId
) {}
