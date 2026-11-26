package tn.voltpark.session.api.dto;

import java.math.BigDecimal;
import java.util.List;

public record StationResponse(
        Long id,
        String code,
        String name,
        String operator,
        String corridor,
        String address,
        Double latitude,
        Double longitude,
        BigDecimal pricePerKwh,
        BigDecimal peakPricePerKwh,
        BigDecimal offPeakPricePerKwh,
        BigDecimal idleFeePerMinute,
        List<ChargerResponse> chargers
) {}
