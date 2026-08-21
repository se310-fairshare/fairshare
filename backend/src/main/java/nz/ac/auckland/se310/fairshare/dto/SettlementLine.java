package nz.ac.auckland.se310.fairshare.dto;

import java.math.BigDecimal;

public record SettlementLine(Long fromUserId, Long toUserId, BigDecimal amount) {}
