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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordResetOtpRepository passwordResetOtpRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private PasswordResetService passwordResetService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(passwordResetService, "otpExpirationMinutes", 10L);
    }

    @Test
    void requestOtp_shouldRejectUnknownEmail() {
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> passwordResetService.requestOtp(forgotRequest("missing@example.com")));

        assertEquals("mail invalid", ex.getMessage());
        verify(passwordResetOtpRepository, never()).save(any());
        verify(emailService, never()).sendPasswordResetOtpEmail(anyString(), anyString(), anyString(), anyLong());
    }

    @Test
    void requestOtp_shouldAllowReadOnlyAccount() {
        User user = user(1, "student@example.com", true, true);
        when(userRepository.findByEmail("student@example.com")).thenReturn(Optional.of(user));
        when(passwordResetOtpRepository.save(any(PasswordResetOtp.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(passwordEncoder.encode(anyString())).thenAnswer(invocation -> "encoded-" + invocation.getArgument(0));

        passwordResetService.requestOtp(forgotRequest("student@example.com"));

        verify(passwordResetOtpRepository).save(any(PasswordResetOtp.class));
        verify(emailService).sendPasswordResetOtpEmail(eq("student@example.com"), eq("Student User"), anyString(), eq(10L));
    }

    @Test
    void requestOtp_shouldRejectUnapprovedAccount() {
        User user = user(1, "student@example.com", false, true);
        when(userRepository.findByEmail("student@example.com")).thenReturn(Optional.of(user));

        ForbiddenException ex = assertThrows(ForbiddenException.class,
                () -> passwordResetService.requestOtp(forgotRequest("student@example.com")));

        assertEquals("account not approved", ex.getMessage());
        verify(passwordResetOtpRepository, never()).save(any());
        verify(emailService, never()).sendPasswordResetOtpEmail(anyString(), anyString(), anyString(), anyLong());
    }

    @Test
    void requestOtp_shouldSaveHashAndSendRawOtp_whenAccountCanReset() {
        User user = user(1, "student@example.com", true, true);
        when(userRepository.findByEmail("student@example.com")).thenReturn(Optional.of(user));
        when(passwordResetOtpRepository.save(any(PasswordResetOtp.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(passwordEncoder.encode(anyString())).thenAnswer(invocation -> "encoded-" + invocation.getArgument(0));

        passwordResetService.requestOtp(forgotRequest(" STUDENT@example.com "));

        ArgumentCaptor<PasswordResetOtp> otpCaptor = ArgumentCaptor.forClass(PasswordResetOtp.class);
        ArgumentCaptor<String> rawOtpCaptor = ArgumentCaptor.forClass(String.class);
        verify(passwordResetOtpRepository).markActiveTokensUsed(eq(1), any(LocalDateTime.class));
        verify(passwordResetOtpRepository).save(otpCaptor.capture());
        verify(emailService).sendPasswordResetOtpEmail(eq("student@example.com"), eq("Student User"), rawOtpCaptor.capture(), eq(10L));

        PasswordResetOtp saved = otpCaptor.getValue();
        String rawOtp = rawOtpCaptor.getValue();
        assertTrue(rawOtp.matches("\\d{6}"));
        assertEquals("encoded-" + rawOtp, saved.getOtpHash());
        assertEquals(user, saved.getUser());
        assertEquals(0, saved.getAttemptCount());
        assertNotNull(saved.getExpiresAt());
    }

    @Test
    void verifyOtp_shouldIncreaseAttemptCount_whenOtpIsWrong() {
        User user = user(1, "student@example.com", true, true);
        PasswordResetOtp resetOtp = activeOtp(user, "123456", 0);
        when(userRepository.findByEmail("student@example.com")).thenReturn(Optional.of(user));
        when(passwordResetOtpRepository.findTopByUser_UserIdAndUsedAtIsNullOrderByCreatedAtDesc(1))
                .thenReturn(Optional.of(resetOtp));
        when(passwordEncoder.matches("000000", "encoded-123456")).thenReturn(false);

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> passwordResetService.verifyOtp(verifyRequest("student@example.com", "000000")));

        assertEquals("OTP invalid.", ex.getMessage());
        assertEquals(1, resetOtp.getAttemptCount());
        assertNull(resetOtp.getUsedAt());
        verify(passwordResetOtpRepository).save(resetOtp);
    }

    @Test
    void verifyOtp_shouldBlockSession_whenWrongOtpReachesTenAttempts() {
        User user = user(1, "student@example.com", true, true);
        PasswordResetOtp resetOtp = activeOtp(user, "123456", 9);
        when(userRepository.findByEmail("student@example.com")).thenReturn(Optional.of(user));
        when(passwordResetOtpRepository.findTopByUser_UserIdAndUsedAtIsNullOrderByCreatedAtDesc(1))
                .thenReturn(Optional.of(resetOtp));
        when(passwordEncoder.matches("000000", "encoded-123456")).thenReturn(false);

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> passwordResetService.verifyOtp(verifyRequest("student@example.com", "000000")));

        assertEquals("OTP attempts exceeded. Please request a new code.", ex.getMessage());
        assertEquals(10, resetOtp.getAttemptCount());
        assertNotNull(resetOtp.getUsedAt());
        verify(passwordResetOtpRepository).save(resetOtp);
    }

    @Test
    void verifyOtpAndResetPassword_shouldUpdatePasswordAndConsumeSession_whenOtpIsCorrect() {
        User user = user(1, "student@example.com", true, true);
        PasswordResetOtp resetOtp = activeOtp(user, "123456", 0);
        when(userRepository.findByEmail("student@example.com")).thenReturn(Optional.of(user));
        when(passwordResetOtpRepository.findTopByUser_UserIdAndUsedAtIsNullOrderByCreatedAtDesc(1))
                .thenReturn(Optional.of(resetOtp));
        when(passwordResetOtpRepository.save(any(PasswordResetOtp.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(passwordEncoder.matches("123456", "encoded-123456")).thenReturn(true);

        ResetOtpResponse response = passwordResetService.verifyOtp(verifyRequest("student@example.com", "123456"));

        assertNotNull(response.getResetToken());
        assertNotNull(resetOtp.getVerifiedAt());
        assertEquals(hash(response.getResetToken()), resetOtp.getResetTokenHash());

        when(passwordResetOtpRepository.findByResetTokenHashAndUsedAtIsNull(hash(response.getResetToken())))
                .thenReturn(Optional.of(resetOtp));
        when(passwordEncoder.encode("NewPass123")).thenReturn("encoded-password");

        passwordResetService.resetPassword(resetRequest(response.getResetToken(), "NewPass123"));

        assertEquals("encoded-password", user.getPasswordHash());
        assertNotNull(resetOtp.getUsedAt());
        verify(userRepository).save(user);
    }

    private ForgotPasswordRequest forgotRequest(String email) {
        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setEmail(email);
        return request;
    }

    private VerifyResetOtpRequest verifyRequest(String email, String otp) {
        VerifyResetOtpRequest request = new VerifyResetOtpRequest();
        request.setEmail(email);
        request.setOtp(otp);
        return request;
    }

    private ResetPasswordRequest resetRequest(String resetToken, String newPassword) {
        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setResetToken(resetToken);
        request.setNewPassword(newPassword);
        return request;
    }

    private PasswordResetOtp activeOtp(User user, String rawOtp, int attempts) {
        return PasswordResetOtp.builder()
                .user(user)
                .otpHash("encoded-" + rawOtp)
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .attemptCount(attempts)
                .build();
    }

    private User user(Integer id, String email, boolean approved, boolean isActive) {
        return User.builder()
                .userId(id)
                .email(email)
                .fullName("Student User")
                .userType("FPT_STUDENT")
                .passwordHash("old-password")
                .provider("LOCAL")
                .isApproved(approved)
                .isActive(isActive)
                .build();
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
            throw new IllegalStateException(ex);
        }
    }
}
