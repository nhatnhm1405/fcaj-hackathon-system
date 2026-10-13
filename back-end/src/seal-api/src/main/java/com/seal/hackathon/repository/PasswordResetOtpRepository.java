package com.seal.hackathon.repository;

import com.seal.hackathon.entity.PasswordResetOtp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface PasswordResetOtpRepository extends JpaRepository<PasswordResetOtp, Long> {

    Optional<PasswordResetOtp> findTopByUser_UserIdAndUsedAtIsNullOrderByCreatedAtDesc(Integer userId);

    Optional<PasswordResetOtp> findByResetTokenHashAndUsedAtIsNull(String resetTokenHash);

    @Modifying
    @Query("""
            UPDATE PasswordResetOtp p
            SET p.usedAt = :usedAt
            WHERE p.user.userId = :userId
              AND p.usedAt IS NULL
            """)
    void markActiveTokensUsed(@Param("userId") Integer userId, @Param("usedAt") LocalDateTime usedAt);

    void deleteAllByUser_UserId(Integer userId);
}
