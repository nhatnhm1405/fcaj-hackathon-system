package com.seal.hackathon.service;

import com.seal.hackathon.dto.response.ParticipationAccessRequestResponse;
import com.seal.hackathon.entity.ParticipationAccessRequest;
import com.seal.hackathon.entity.User;
import com.seal.hackathon.exception.BadRequestException;
import com.seal.hackathon.exception.ForbiddenException;
import com.seal.hackathon.exception.ResourceNotFoundException;
import com.seal.hackathon.repository.ParticipationAccessRequestRepository;
import com.seal.hackathon.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ParticipationAccessRequestService {

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_APPROVED = "APPROVED";
    private static final String STATUS_REJECTED = "REJECTED";
    private static final Set<String> STUDENT_TYPES = Set.of("FPT_STUDENT", "EXTERNAL_STUDENT");

    private final ParticipationAccessRequestRepository requestRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final SystemLogService systemLogService;

    @Transactional
    public ParticipationAccessRequestResponse requestAccess(Integer userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        validateStudent(user);
        if (!Boolean.TRUE.equals(user.getIsApproved())) {
            throw new ForbiddenException("Your account must be approved before requesting participation access.");
        }
        if (Boolean.TRUE.equals(user.getIsActive())) {
            throw new BadRequestException("Your account already has participation access.");
        }

        var existing = requestRepository.findByUser_UserIdAndStatus(userId, STATUS_PENDING);
        ParticipationAccessRequest request = existing.orElseGet(() -> requestRepository.save(
                ParticipationAccessRequest.builder()
                        .user(user)
                        .email(user.getEmail().toLowerCase().trim())
                        .status(STATUS_PENDING)
                        .build()));

        // Only greet on a freshly-created request (the call is idempotent while one
        // is already pending) so re-clicks don't spam the notification feed.
        if (existing.isEmpty()) {
            notificationService.createNotification(
                    user.getUserId(),
                    "Request to compete submitted",
                    "Your request to join the current season is awaiting coordinator review.",
                    "PARTICIPATION_ACCESS_REQUESTED"
            );
        }
        return mapToResponse(request);
    }

    @Transactional(readOnly = true)
    public List<ParticipationAccessRequestResponse> listPending() {
        return requestRepository.findByStatusOrderByRequestedAtDesc(STATUS_PENDING).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public ParticipationAccessRequestResponse approve(Integer requestId, Integer adminId) {
        ParticipationAccessRequest request = requirePending(requestId);
        User user = request.getUser();
        user.setIsActive(true);
        userRepository.save(user);

        resolve(request, STATUS_APPROVED, adminId);
        notificationService.createNotification(
                user.getUserId(),
                "Participation access approved",
                "Your account can now create teams, join teams and submit for the current hackathon.",
                "PARTICIPATION_ACCESS_APPROVED"
        );
        systemLogService.record(adminId, "APPROVE_PARTICIPATION_ACCESS",
                "approved participation access for user#" + user.getUserId());
        return mapToResponse(request);
    }

    @Transactional
    public ParticipationAccessRequestResponse reject(Integer requestId, Integer adminId) {
        ParticipationAccessRequest request = requirePending(requestId);
        resolve(request, STATUS_REJECTED, adminId);
        notificationService.createNotification(
                request.getUser().getUserId(),
                "Participation access rejected",
                "Your request to participate was not approved. Please contact the organizers for details.",
                "PARTICIPATION_ACCESS_REJECTED"
        );
        systemLogService.record(adminId, "REJECT_PARTICIPATION_ACCESS",
                "rejected participation access for user#" + request.getUser().getUserId());
        return mapToResponse(request);
    }

    private ParticipationAccessRequest requirePending(Integer requestId) {
        ParticipationAccessRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Participation access request not found: " + requestId));
        if (!STATUS_PENDING.equalsIgnoreCase(request.getStatus())) {
            throw new BadRequestException("This request has already been resolved.");
        }
        return request;
    }

    private void resolve(ParticipationAccessRequest request, String status, Integer adminId) {
        request.setStatus(status);
        request.setResolvedAt(LocalDateTime.now());
        request.setResolvedBy(adminId);
        requestRepository.save(request);
    }

    private void validateStudent(User user) {
        if (user.getUserType() == null || !STUDENT_TYPES.contains(user.getUserType().toUpperCase())) {
            throw new ForbiddenException("Only student accounts can request participation access.");
        }
    }

    private ParticipationAccessRequestResponse mapToResponse(ParticipationAccessRequest request) {
        User user = request.getUser();
        return ParticipationAccessRequestResponse.builder()
                .requestId(request.getRequestId())
                .userId(user.getUserId())
                .email(request.getEmail())
                .fullName(user.getFullName())
                .userType(user.getUserType())
                .status(request.getStatus())
                .requestedAt(request.getRequestedAt())
                .resolvedAt(request.getResolvedAt())
                .resolvedBy(request.getResolvedBy())
                .build();
    }
}
