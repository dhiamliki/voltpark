package tn.voltpark.session.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import tn.voltpark.session.domain.model.Charger;

public record ChargerRequest(
        @NotBlank(message = "L'identifiant de la borne est obligatoire (ex: CP-TUN-001)")
        String id,

        @NotNull(message = "L'ID de la station est obligatoire")
        Long stationId,

        Charger.ChargerType chargerType,

        Double maxPowerKw
) {}
