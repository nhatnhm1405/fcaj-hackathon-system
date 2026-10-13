package com.seal.hackathon.service;

import com.seal.hackathon.dto.response.UserResponse;
import com.seal.hackathon.entity.User;
import com.seal.hackathon.event.AccountApprovalEmailEvent;
import com.seal.hackathon.exception.BadRequestException;
import com.seal.hackathon.repository.NotificationRepository;
import com.seal.hackathon.repository.PasswordResetOtpRepository;
import com.seal.hackathon.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuthService authService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private NotificationService notificationService;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private PasswordResetOtpRepository passwordResetOtpRepository;

    @InjectMocks
    private AccountService accountService;

    @Test
    void rejectUser_shouldDeletePendingAccountAndPreApprovalDependencies() {
        User pendingUser = User.builder()
                .userId(42)
                .email("pending@example.com")
                .fullName("Pending User")
                .userType("EXTERNAL_STUDENT")
                .isApproved(false)
                .isActive(true)
                .provider("LOCAL")
                .build();
        UserResponse expected = UserResponse.builder()
                .userId(42)
                .email("pending@example.com")
                .fullName("Pending User")
                .isApproved(false)
                .build();

        when(userRepository.findByIdWithRoles(42)).thenReturn(Optional.of(pendingUser));
        when(authService.mapToUserResponse(pendingUser)).thenReturn(expected);

        UserResponse actual = accountService.rejectUser(42, 7, " duplicate registration ");

        assertSame(expected, actual);
        verify(auditLogService).record(7, "REJECT_ACCOUNT", "USER", 42,
                "duplicate registration", null);
        verify(notificationRepository).deleteAllByRecipient_UserIdOrSender_UserId(42, 42);
        verify(passwordResetOtpRepository).deleteAllByUser_UserId(42);
        verify(userRepository).delete(pendingUser);
        verify(userRepository).flush();
        verify(eventPublisher).publishEvent(new AccountApprovalEmailEvent(
                "pending@example.com", "Pending User", false));
        verify(userRepository, never()).save(pendingUser);
    }

    @Test
    void rejectUser_shouldNotDeleteApprovedAccount() {
        User approvedUser = User.builder()
                .userId(42)
                .email("approved@example.com")
                .fullName("Approved User")
                .userType("FPT_STUDENT")
                .isApproved(true)
                .isActive(true)
                .provider("LOCAL")
                .build();

        when(userRepository.findByIdWithRoles(42)).thenReturn(Optional.of(approvedUser));

        assertThrows(BadRequestException.class, () -> accountService.rejectUser(42, 7, null));

        verify(notificationRepository, never()).deleteAllByRecipient_UserIdOrSender_UserId(42, 42);
        verify(passwordResetOtpRepository, never()).deleteAllByUser_UserId(42);
        verify(userRepository, never()).delete(approvedUser);
        verify(eventPublisher, never()).publishEvent(new AccountApprovalEmailEvent(
                "approved@example.com", "Approved User", false));
    }
}
