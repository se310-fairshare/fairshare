package nz.ac.auckland.se310.fairshare.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateGroupRequest(
        @NotBlank(message = "Group name is required")
        @Size(max = 50, message = "Group name must be at most 50 characters")
        String name,

        @Size(max = 255, message = "Description must be at most 255 characters")
        String description) {}
