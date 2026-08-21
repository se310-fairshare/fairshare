package nz.ac.auckland.se310.fairshare.dto;

import jakarta.validation.constraints.NotBlank;

public record ManageGroupMemberRequest(
        @NotBlank(message = "Enter an email or username") String identifier) {}
