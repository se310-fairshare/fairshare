package nz.ac.auckland.se310.fairshare.dto;

import java.util.List;

public record SettlementRequest(List<MemberBalance> balances) {}
