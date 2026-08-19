package nz.ac.auckland.se310.fairshare.dto;

import java.math.BigDecimal;

public record GroupMemberResponse(
        Long userId,
        String username,
        String email,
        BigDecimal netBalance,
        boolean currentUser) {}
