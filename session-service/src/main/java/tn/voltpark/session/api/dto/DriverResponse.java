package tn.voltpark.session.api.dto;

import java.time.Instant;

public record DriverResponse(
        Long id,
        String name,
        String email,
        String badgeId,
        String phoneNumber,
        Instant createdAt
) {}
