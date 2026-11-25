package tn.voltpark.session.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import tn.voltpark.session.domain.model.Charger;

public record ChargerRequest(
        @NotBlank(message = "L'identifiant de la borne est obligatoire (ex: CP-TUN-001)")
        @Size(max = 50, message = "L'identifiant de la borne fait au plus 50 caracteres")
        String id,

        @NotNull(message = "L'ID de la station est obligatoire")
        @Positive(message = "L'ID de la station doit etre positif")
        Long stationId,

        Charger.ChargerType chargerType,

        @Positive(message = "La puissance maximale doit etre strictement positive")
        @DecimalMax(value = "1000.0", message = "La puissance maximale ne peut pas depasser 1000 kW")
        Double maxPowerKw
) {}
