package nz.ac.auckland.se310.fairshare.dto;

import java.time.Instant;

public record GroupResponse(
        Long id, String name, String description,
        String baseCurrency, Instant createdAt, int memberCount) {}