package com.seal.hackathon.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateInviteRequest {
    @NotNull(message = "Invited user ID is required")
    private Integer invitedUserId;

    @Size(max = 1000, message = "Message must be at most 1000 characters")
    private String message;
}
