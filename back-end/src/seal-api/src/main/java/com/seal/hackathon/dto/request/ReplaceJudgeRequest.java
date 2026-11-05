package com.seal.hackathon.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ReplaceJudgeRequest {

    @NotNull(message = "Replacement judge is required")
    private Integer judgeUserId;

    @NotBlank(message = "Replacement reason is required")
    @Size(max = 500, message = "Replacement reason must be 500 characters or fewer")
    private String reason;
}
