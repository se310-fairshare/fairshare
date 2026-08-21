package nz.ac.auckland.se310.fairshare.dto;

import java.math.BigDecimal;

public record MemberBalance(Long userId, BigDecimal balance) {}
