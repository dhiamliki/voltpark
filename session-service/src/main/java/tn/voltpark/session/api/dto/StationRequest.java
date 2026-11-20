package tn.voltpark.session.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record StationRequest(
        @NotBlank String name,
        String address,
        Double latitude,
        Double longitude,
        @NotNull @DecimalMin("0.0") BigDecimal pricePerKwh,
        @NotNull @DecimalMin("0.0") BigDecimal peakPricePerKwh,
        @NotNull @DecimalMin("0.0") BigDecimal offPeakPricePerKwh,
        @NotNull @DecimalMin("0.0") BigDecimal idleFeePerMinute
) {}
