package tn.voltpark.session.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record DriverRequest(
        @NotBlank(message = "Le nom est obligatoire")
        @Size(max = 100, message = "Le nom fait au plus 100 caracteres")
        String name,

        @NotBlank(message = "L'email est obligatoire")
        @Email(message = "Format d'email invalide")
        @Size(max = 150, message = "L'email fait au plus 150 caracteres")
        String email,

        @NotBlank(message = "Le badge RFID (idTag) est obligatoire")
        @Size(max = 50, message = "Le badge RFID fait au plus 50 caracteres")
        String badgeId,

        @Pattern(regexp = "^\\+?[0-9 ]{6,20}$", message = "Numero de telephone invalide (ex: +216 20 123 456)")
        String phoneNumber
) {}
