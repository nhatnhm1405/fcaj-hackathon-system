package com.seal.hackathon.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateReopenRequestRequest {
    // Optional explanation shown to the Admin who reviews the request.
    @Size(max = 1000, message = "Reason must be at most 1000 characters")
    private String reason;
}
