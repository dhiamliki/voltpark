package tn.voltpark.session.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record StationRequest(
        @NotBlank(message = "Le nom de la station est obligatoire")
        @Size(max = 150, message = "Le nom fait au plus 150 caracteres")
        String name,

        @Size(max = 255, message = "L'adresse fait au plus 255 caracteres")
        String address,

        @DecimalMin(value = "-90.0", message = "La latitude doit etre comprise entre -90 et 90")
        @DecimalMax(value = "90.0", message = "La latitude doit etre comprise entre -90 et 90")
        Double latitude,

        @DecimalMin(value = "-180.0", message = "La longitude doit etre comprise entre -180 et 180")
        @DecimalMax(value = "180.0", message = "La longitude doit etre comprise entre -180 et 180")
        Double longitude,

        @NotNull(message = "Le prix du kWh est obligatoire")
        @DecimalMin(value = "0.0", message = "Le prix du kWh doit etre positif")
        BigDecimal pricePerKwh,

        @NotNull(message = "Le prix du kWh en heures pleines est obligatoire")
        @DecimalMin(value = "0.0", message = "Le prix heures pleines doit etre positif")
        BigDecimal peakPricePerKwh,

        @NotNull(message = "Le prix du kWh en heures creuses est obligatoire")
        @DecimalMin(value = "0.0", message = "Le prix heures creuses doit etre positif")
        BigDecimal offPeakPricePerKwh,

        @NotNull(message = "Les frais d'occupation par minute sont obligatoires")
        @DecimalMin(value = "0.0", message = "Les frais d'occupation doivent etre positifs")
        BigDecimal idleFeePerMinute
) {}
