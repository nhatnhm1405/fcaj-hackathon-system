package com.seal.hackathon.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Safe user representation — password_hash is NEVER included here.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserResponse {

    private Integer userId;
    private String email;
    private String fullName;
    private String userType;
    private String judgeType;
    private String studentId;
    private String university;

    private Boolean isApproved;
    private Boolean isActive;
    private Integer teamId;
    private String isLeader;

    // Set for time-limited accounts (e.g. guest judges); null for regular accounts
    private LocalDateTime expiredAt;

    private String provider;
    private String avatarUrl;

    // All role names this user currently has (across all events)
    private List<String> roles;

    private LocalDateTime createdAt;
}
