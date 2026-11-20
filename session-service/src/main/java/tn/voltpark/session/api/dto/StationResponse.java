package tn.voltpark.session.api.dto;

import java.math.BigDecimal;
import java.util.List;

public record StationResponse(
        Long id,
        String name,
        String address,
        Double latitude,
        Double longitude,
        BigDecimal pricePerKwh,
        BigDecimal peakPricePerKwh,
        BigDecimal offPeakPricePerKwh,
        BigDecimal idleFeePerMinute,
        List<ChargerResponse> chargers
) {}
