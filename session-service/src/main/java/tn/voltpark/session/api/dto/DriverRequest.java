package tn.voltpark.session.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record DriverRequest(
        @NotBlank(message = "Le nom est obligatoire")
        String name,

        @NotBlank(message = "L'email est obligatoire")
        @Email(message = "Format d'email invalide")
        String email,

        @NotBlank(message = "Le badge RFID (idTag) est obligatoire")
        String badgeId,

        String phoneNumber
) {}
