package com.seal.hackathon.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Maps to the `User` table in seal_hackathon schema.
 * password_hash is nullable to support OAuth2 users who have no local password.
 * provider tracks which auth method was used (LOCAL, GOOGLE, GITHUB).
 */
@Entity
@Table(name = "`User`") //các dấu backtick `` dùng với mục đích phân biệt keyword trong MySQL và tên bảng thật sự
@Getter
@Setter //lombok tự sinh getter/setter, constructure và builder pattern
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) //DB tự tăng user_id (đã có auto_increment trong MySQL)
    @Column(name = "user_id")
    private Integer userId;

    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    // Nullable: OAuth2 users do not have a local password
    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 255)
    private String fullName;

    // FPT_STUDENT | EXTERNAL_STUDENT | STAFF
    @Column(name = "user_type", nullable = false, length = 20)
    private String userType;

    // INTERNAL | GUEST; only set for users who act as judges
    @Column(name = "judge_type", length = 20)
    private String judgeType;

    @Column(name = "student_id", length = 50)
    private String studentId;

    @Column(name = "university", length = 255)
    private String university;

    // false = pending approval, true = approved and can log in
    @Column(name = "is_approved", nullable = false)
    @Builder.Default
    private Boolean isApproved = false;

    // Reused flag: false means a participant is read-only; true means writable/active.
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // MySQL ON UPDATE CURRENT_TIMESTAMP — managed by DB, not Hibernate
    @Column(name = "expired_at")
    private LocalDateTime expiredAt;

    // LOCAL | GOOGLE | GITHUB  (added via ALTER TABLE)
    @Column(name = "provider", length = 20, nullable = false)
    @Builder.Default
    private String provider = "LOCAL";

    // Unique ID from the OAuth2 provider (added via ALTER TABLE)
    @Column(name = "provider_id", length = 255)
    private String providerId;

    // Profile picture URL from OAuth2 provider (added via ALTER TABLE)
    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    // Roles assigned to this user (across all events)
    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @Builder.Default
    private List<UserEventRole> userEventRoles = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (provider == null) {
            provider = "LOCAL";
        }
    }
}
