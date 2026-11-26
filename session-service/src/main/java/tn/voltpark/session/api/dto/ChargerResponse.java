package tn.voltpark.session.api.dto;

import tn.voltpark.session.domain.model.Charger;

public record ChargerResponse(
        String id,
        Long stationId,
        String stationName,
        Charger.ChargerType chargerType,
        Charger.ChargerStatus status,
        Double maxPowerKw,
        String connectorType
) {}
