package com.seal.hackathon.service;

import com.seal.hackathon.dto.request.ForgotPasswordRequest;
import com.seal.hackathon.dto.request.ResetPasswordRequest;
import com.seal.hackathon.dto.request.VerifyResetOtpRequest;
import com.seal.hackathon.dto.response.ResetOtpResponse;
import com.seal.hackathon.entity.PasswordResetOtp;
import com.seal.hackathon.entity.User;
import com.seal.hackathon.exception.BadRequestException;
import com.seal.hackathon.exception.ForbiddenException;
import com.seal.hackathon.repository.PasswordResetOtpRepository;
import com.seal.hackathon.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private static final int OTP_DIGITS = 6;
    private static final int MAX_ATTEMPTS = 10;

    private final UserRepository userRepository;
    private final PasswordResetOtpRepository passwordResetOtpRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.password-reset.otp-expiration-minutes:10}")
    private long otpExpirationMinutes;

    @Transactional
    public void requestOtp(ForgotPasswordRequest request) {
        User user = userRepository.findByEmail(normalizeEmail(request.getEmail()))
                .orElseThrow(() -> new BadRequestException("mail invalid"));

        validateResettableAccount(user);

        LocalDateTime now = LocalDateTime.now();
        passwordResetOtpRepository.markActiveTokensUsed(user.getUserId(), now);

        String otp = generateOtp();
        PasswordResetOtp resetOtp = PasswordResetOtp.builder()
                .user(user)
                .otpHash(passwordEncoder.encode(otp))
                .expiresAt(now.plusMinutes(otpExpirationMinutes))
                .attemptCount(0)
                .build();
        passwordResetOtpRepository.save(resetOtp);

        emailService.sendPasswordResetOtpEmail(user.getEmail(), user.getFullName(), otp, otpExpirationMinutes);
    }

    @Transactional
    public ResetOtpResponse verifyOtp(VerifyResetOtpRequest request) {
        User user = userRepository.findByEmail(normalizeEmail(request.getEmail()))
                .orElseThrow(() -> new BadRequestException("mail invalid"));

        PasswordResetOtp resetOtp = passwordResetOtpRepository
                .findTopByUser_UserIdAndUsedAtIsNullOrderByCreatedAtDesc(user.getUserId())
                .orElseThrow(() -> new BadRequestException("No password reset request found."));

        LocalDateTime now = LocalDateTime.now();
        ensureOtpCanBeUsed(resetOtp, now);

        String candidateOtp = request.getOtp() == null ? "" : request.getOtp().trim();
        if (!passwordEncoder.matches(candidateOtp, resetOtp.getOtpHash())) {
            int attempts = resetOtp.getAttemptCount() == null ? 1 : resetOtp.getAttemptCount() + 1;
            resetOtp.setAttemptCount(attempts);
            if (attempts >= MAX_ATTEMPTS) {
                resetOtp.setUsedAt(now);
                passwordResetOtpRepository.save(resetOtp);
                throw new BadRequestException("OTP attempts exceeded. Please request a new code.");
            }
            passwordResetOtpRepository.save(resetOtp);
            throw new BadRequestException("OTP invalid.");
        }

        String resetToken = generateResetToken();
        resetOtp.setVerifiedAt(now);
        resetOtp.setResetTokenHash(hash(resetToken));
        passwordResetOtpRepository.save(resetOtp);

        return ResetOtpResponse.builder()
                .resetToken(resetToken)
                .build();
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        PasswordResetOtp resetOtp = passwordResetOtpRepository
                .findByResetTokenHashAndUsedAtIsNull(hash(request.getResetToken()))
                .orElseThrow(() -> new BadRequestException("Reset session invalid. Please verify OTP again."));

        LocalDateTime now = LocalDateTime.now();
        if (resetOtp.getVerifiedAt() == null) {
            throw new BadRequestException("Reset session invalid. Please verify OTP again.");
        }
        ensureOtpCanBeUsed(resetOtp, now);

        User user = resetOtp.getUser();
        validateResettableAccount(user);

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        resetOtp.setUsedAt(now);
        passwordResetOtpRepository.save(resetOtp);
    }

    private void validateResettableAccount(User user) {
        if (!Boolean.TRUE.equals(user.getIsApproved())) {
            throw new ForbiddenException("account not approved");
        }
        if (!"LOCAL".equalsIgnoreCase(user.getProvider()) || user.getPasswordHash() == null) {
            throw new BadRequestException("This account uses Google/GitHub login.");
        }
    }

    private void ensureOtpCanBeUsed(PasswordResetOtp resetOtp, LocalDateTime now) {
        if (resetOtp.getExpiresAt().isBefore(now)) {
            resetOtp.setUsedAt(now);
            passwordResetOtpRepository.save(resetOtp);
            throw new BadRequestException("OTP expired. Please request a new code.");
        }
        if (resetOtp.getAttemptCount() != null && resetOtp.getAttemptCount() >= MAX_ATTEMPTS) {
            resetOtp.setUsedAt(now);
            passwordResetOtpRepository.save(resetOtp);
            throw new BadRequestException("OTP attempts exceeded. Please request a new code.");
        }
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.toLowerCase().trim();
    }

    private String generateOtp() {
        int bound = (int) Math.pow(10, OTP_DIGITS);
        return String.format("%0" + OTP_DIGITS + "d", secureRandom.nextInt(bound));
    }

    private String generateResetToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hashed.length * 2);
            for (byte b : hashed) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available.", ex);
        }
    }
}
