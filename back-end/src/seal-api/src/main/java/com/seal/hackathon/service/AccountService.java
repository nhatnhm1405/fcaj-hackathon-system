package com.seal.hackathon.service;

import com.seal.hackathon.dto.response.UserResponse;
import com.seal.hackathon.entity.User;
import com.seal.hackathon.event.AccountApprovalEmailEvent;
import com.seal.hackathon.exception.BadRequestException;
import com.seal.hackathon.exception.ResourceNotFoundException;
import com.seal.hackathon.repository.NotificationRepository;
import com.seal.hackathon.repository.PasswordResetOtpRepository;
import com.seal.hackathon.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final UserRepository userRepository;
    private final AuthService authService; // reuse the mapping helper
    private final ApplicationEventPublisher eventPublisher;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;
    private final NotificationRepository notificationRepository;
    private final PasswordResetOtpRepository passwordResetOtpRepository;

    // ---------------------------------------------------------------
    // List pending approvals
    // ---------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<UserResponse> getPendingApprovals() {
        return userRepository.findAllByIsApprovedFalse().stream()
                .map(authService::mapToUserResponse)
                .collect(Collectors.toList());
    }

    /** All active, approved participants — the coordinator's "All Participant" account view. */
    @Transactional(readOnly = true)
    public List<UserResponse> getActiveParticipants() {
        return userRepository.findActiveApprovedParticipants().stream()
                .map(authService::mapToUserResponse)
                .collect(Collectors.toList());
    }

    /** All active, approved judge/mentor-eligible staff — the coordinator's "Judge & Mentor" account view. */
    @Transactional(readOnly = true)
    public List<UserResponse> getActiveJudgeMentorStaff() {
        return userRepository.findActiveAssignableStaff().stream()
                .map(authService::mapToUserResponse)
                .collect(Collectors.toList());
    }

    // ---------------------------------------------------------------
    // Approve a user
    // ---------------------------------------------------------------

    @Transactional
    public UserResponse approveUser(Integer userId, Integer actorUserId) {
        User user = getUserOrThrow(userId);

        if (Boolean.TRUE.equals(user.getIsApproved())) {
            throw new BadRequestException("User is already approved.");
        }

        user.setIsApproved(true);
        user.setIsActive(true);
        userRepository.save(user);
        // Rejected users have is_active=false and cannot log in, so an in-app
        // notification only makes sense for approvals (they get an email either way).
        notificationService.createNotification(
                user.getUserId(),
                "Account approved",
                "Your account has been approved. You can now access SEAL Hackathon.",
                "ACCOUNT_APPROVED"
        );
        eventPublisher.publishEvent(new AccountApprovalEmailEvent(
                user.getEmail(),
                user.getFullName(),
                true
        ));
        auditLogService.record(actorUserId, "APPROVE_ACCOUNT", "USER", user.getUserId());
        return authService.mapToUserResponse(user);
    }

    // ---------------------------------------------------------------
    // Reject a user
    // Rejection permanently deletes a still-pending account and its pre-approval
    // notification/password-reset data. Approved accounts are never deleted here.
    // The decision remains recorded in AuditLog with the optional reason.
    // ---------------------------------------------------------------

    @Transactional
    public UserResponse rejectUser(Integer userId, Integer actorUserId, String reason) {
        User user = getUserOrThrow(userId);

        if (Boolean.TRUE.equals(user.getIsApproved())) {
            throw new BadRequestException("Only pending accounts can be rejected.");
        }

        UserResponse rejectedUser = authService.mapToUserResponse(user);
        String rejectionReason = (reason != null && !reason.isBlank()) ? reason.trim() : null;

        auditLogService.record(actorUserId, "REJECT_ACCOUNT", "USER", user.getUserId(),
                rejectionReason, null);

        // OAuth sign-ups receive a welcome notification before approval, and local
        // sign-ups can request a password-reset OTP. Remove those FK dependants first.
        notificationRepository.deleteAllByRecipient_UserIdOrSender_UserId(
                user.getUserId(), user.getUserId());
        passwordResetOtpRepository.deleteAllByUser_UserId(user.getUserId());
        userRepository.delete(user);
        userRepository.flush();

        eventPublisher.publishEvent(new AccountApprovalEmailEvent(
                user.getEmail(),
                user.getFullName(),
                false
        ));
        return rejectedUser;
    }

    // ---------------------------------------------------------------
    // Helper
    // ---------------------------------------------------------------

    private User getUserOrThrow(Integer userId) {
        return userRepository.findByIdWithRoles(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
    }
}
