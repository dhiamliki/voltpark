package tn.voltpark.session.api.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record ReservationRequest(
        @NotNull(message = "L'ID du conducteur est obligatoire")
        Long driverId,

        @NotBlank(message = "L'ID de la borne est obligatoire")
        String chargerId,

        @NotNull(message = "L'heure de debut est obligatoire")
        @FutureOrPresent(message = "L'heure de debut ne peut pas etre dans le passe")
        Instant startTime,

        @NotNull(message = "L'heure de fin est obligatoire")
        Instant endTime
) {}
