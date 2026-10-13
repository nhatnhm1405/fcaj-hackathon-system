package com.seal.hackathon.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateJoinRequestRequest {
    @Size(max = 1000, message = "Message must be at most 1000 characters")
    private String message;
}
