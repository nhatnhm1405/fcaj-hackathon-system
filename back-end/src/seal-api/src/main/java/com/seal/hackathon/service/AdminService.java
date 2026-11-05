package com.seal.hackathon.service;

import com.seal.hackathon.dto.request.CreateUserRequest;
import com.seal.hackathon.dto.request.GrantRoleRequest;
import com.seal.hackathon.dto.request.UpdateUserRequest;
import com.seal.hackathon.dto.response.UserEventRoleResponse;
import com.seal.hackathon.dto.response.UserResponse;
import com.seal.hackathon.entity.HackathonEvent;
import com.seal.hackathon.entity.Role;
import com.seal.hackathon.entity.User;
import com.seal.hackathon.entity.UserEventRole;
import com.seal.hackathon.exception.BadRequestException;
import com.seal.hackathon.exception.ResourceNotFoundException;
import com.seal.hackathon.repository.HackathonEventRepository;
import com.seal.hackathon.repository.RoleRepository;
import com.seal.hackathon.repository.UserEventRoleRepository;
import com.seal.hackathon.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Platform administration — SYSTEM_ADMIN only.
 *
 * The System Admin runs the PLATFORM: global user accounts, role grants
 * (notably granting EVENT_COORDINATOR), account administration, and the system log.
 * Event-scoped work (approving participants, assigning judges/mentors, scoring)
 * belongs to EVENT_COORDINATOR, not here.
 */
@Service
@RequiredArgsConstructor
public class AdminService {

    private static final Set<String> USER_TYPES = Set.of("FPT_STUDENT", "EXTERNAL_STUDENT", "STAFF");
    private static final Set<String> JUDGE_TYPES = Set.of("INTERNAL", "GUEST");

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserEventRoleRepository userEventRoleRepository;
    private final HackathonEventRepository hackathonEventRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;
    private final SystemLogService systemLogService;
    private final NotificationService notificationService;

    // ── Users ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<UserResponse> getAllUsers() {
        return userRepository.findAll().stream()
                .map(authService::mapToUserResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public UserResponse getUserById(Integer userId) {
        return userRepository.findByIdWithRoles(userId)
                .map(authService::mapToUserResponse)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
    }

    /**
     * Creates a pre-approved platform account. No role is granted here —
     * use {@link #grantRole} afterwards. judgeType (INTERNAL/GUEST) may be set
     * up front for users who will act as judges.
     */
    @Transactional
    public UserResponse createUser(CreateUserRequest request, Integer adminId) {
        if (userRepository.existsByEmail(request.getEmail().toLowerCase().trim())) {
            throw new BadRequestException("An account with this email already exists.");
        }

        String userType = request.getUserType().toUpperCase();
        if (!USER_TYPES.contains(userType)) {
            throw new BadRequestException("Invalid user type. Must be FPT_STUDENT, EXTERNAL_STUDENT, or STAFF.");
        }

        String judgeType = normalizeJudgeType(request.getJudgeType());

        User user = User.builder()
                .email(request.getEmail().toLowerCase().trim())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName().trim())
                .userType(userType)
                .judgeType(judgeType)
                .provider("LOCAL")
                .isApproved(true)
                .isActive(true)
                .build();

        user = userRepository.save(user);

        systemLogService.record(adminId, "CREATE_USER",
                "created user#" + user.getUserId() + " (" + user.getEmail() + ")");

        User refreshed = userRepository.findByIdWithRoles(user.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found after creation."));
        return authService.mapToUserResponse(refreshed);
    }

    /**
     * Edits an existing account's profile fields (patch semantics — null fields are
     * left unchanged). Login identity (email), account category (userType) and the
     * approval/read-only flags are intentionally not editable here.
     */
    @Transactional
    public UserResponse updateUser(Integer userId, UpdateUserRequest request, Integer adminId) {
        User user = userRepository.findByIdWithRoles(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        if (request.getFullName() != null && !request.getFullName().isBlank()) {
            user.setFullName(request.getFullName().trim());
        }
        if (request.getStudentId() != null) {
            user.setStudentId(request.getStudentId().isBlank() ? null : request.getStudentId().trim());
        }
        if (request.getUniversity() != null) {
            user.setUniversity(request.getUniversity().isBlank() ? null : request.getUniversity().trim());
        }
        if (request.getJudgeType() != null) {
            user.setJudgeType(request.getJudgeType().isBlank() ? null : normalizeJudgeType(request.getJudgeType()));
        }

        userRepository.save(user);
        systemLogService.record(adminId, "UPDATE_USER", "updated user#" + userId);

        User refreshed = userRepository.findByIdWithRoles(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        return authService.mapToUserResponse(refreshed);
    }

    /**
     * Directly toggles an account's active flag. The admin uses this to reactivate
     * a guest judge for a new season — guest judges have no self-service "request to
     * compete" flow (unlike student participants), so the admin flips them back on.
     */
    @Transactional
    public UserResponse setUserActive(Integer userId, boolean active, Integer adminId) {
        User user = userRepository.findByIdWithRoles(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        user.setIsActive(active);
        userRepository.save(user);
        systemLogService.record(adminId, active ? "ACTIVATE_USER" : "DEACTIVATE_USER",
                (active ? "activated" : "deactivated") + " user#" + userId);
        if (active) {
            notificationService.createNotification(userId,
                    "Account activated",
                    "A System Admin reactivated your account. You can take part in the current season again.",
                    "ACCOUNT_ACTIVATED");
        }
        User refreshed = userRepository.findByIdWithRoles(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        return authService.mapToUserResponse(refreshed);
    }

    // ── Role grants ───────────────────────────────────────────────────

    @Transactional
    public UserResponse grantRole(GrantRoleRequest request, Integer adminId) {
        User user = userRepository.findByIdWithRoles(request.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + request.getUserId()));

        String roleName = request.getRoleName().toUpperCase();
        Role role = roleRepository.findByRoleName(roleName)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + roleName));

        boolean alreadyGranted = (request.getEventId() == null)
                ? userEventRoleRepository.existsByUser_UserIdAndRole_RoleNameAndEventIdIsNull(user.getUserId(), roleName)
                : userEventRoleRepository.existsByUser_UserIdAndRole_RoleNameAndEventId(user.getUserId(), roleName, request.getEventId());
        if (alreadyGranted) {
            throw new BadRequestException("User already has role " + roleName + " at this scope.");
        }

        userEventRoleRepository.save(UserEventRole.builder()
                .user(user)
                .role(role)
                .eventId(request.getEventId())
                .build());

        systemLogService.record(adminId, "GRANT_ROLE",
                "granted " + roleName + " to user#" + user.getUserId()
                        + (request.getEventId() == null ? " (system-wide)" : " for event#" + request.getEventId()));

        User refreshed = userRepository.findByIdWithRoles(user.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + user.getUserId()));
        return authService.mapToUserResponse(refreshed);
    }

    @Transactional
    public UserResponse revokeRole(GrantRoleRequest request, Integer adminId) {
        User user = userRepository.findByIdWithRoles(request.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + request.getUserId()));

        String roleName = request.getRoleName().toUpperCase();

        List<UserEventRole> matches = userEventRoleRepository.findAllByUser_UserId(user.getUserId()).stream()
                .filter(uer -> uer.getRole().getRoleName().equalsIgnoreCase(roleName))
                .filter(uer -> Objects.equals(uer.getEventId(), request.getEventId()))
                .collect(Collectors.toList());

        if (matches.isEmpty()) {
            throw new ResourceNotFoundException("User does not have role " + roleName + " at this scope.");
        }

        // User.userEventRoles is @OneToMany(cascade = ALL); the rows were fetched
        // into that managed collection by findByIdWithRoles above. Removing them
        // from the collection too keeps it in sync — otherwise the cascade
        // re-persists the rows on flush and the delete is silently undone
        // (request returns 200 but the role stays). See SEAL revoke-role bug.
        user.getUserEventRoles().removeAll(matches);
        userEventRoleRepository.deleteAll(matches);

        systemLogService.record(adminId, "REVOKE_ROLE",
                "revoked " + roleName + " from user#" + user.getUserId()
                        + (request.getEventId() == null ? " (system-wide)" : " for event#" + request.getEventId()));

        User refreshed = userRepository.findByIdWithRoles(user.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + user.getUserId()));
        return authService.mapToUserResponse(refreshed);
    }

    /**
     * Lists every role grant (UserEventRole) with role and event names resolved,
     * so the admin UI shows labels instead of raw foreign-key IDs.
     */
    @Transactional(readOnly = true)
    public List<UserEventRoleResponse> getAllRoleGrants() {
        List<UserEventRole> grants = userEventRoleRepository.findAll();

        Set<Integer> eventIds = grants.stream()
                .map(UserEventRole::getEventId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Integer, String> eventNames = hackathonEventRepository.findAllById(eventIds).stream()
                .collect(Collectors.toMap(HackathonEvent::getEventId, HackathonEvent::getName));

        return grants.stream()
                .map(uer -> UserEventRoleResponse.builder()
                        .id(uer.getId())
                        .userId(uer.getUser().getUserId())
                        .userFullName(uer.getUser().getFullName())
                        .userEmail(uer.getUser().getEmail())
                        .roleName(uer.getRole().getRoleName())
                        .eventId(uer.getEventId())
                        .eventName(eventNames.get(uer.getEventId()))
                        .build())
                .collect(Collectors.toList());
    }

    // ── Helper ────────────────────────────────────────────────────────

    private String normalizeJudgeType(String judgeType) {
        if (judgeType == null || judgeType.isBlank()) {
            return null;
        }
        String normalized = judgeType.toUpperCase();
        if (!JUDGE_TYPES.contains(normalized)) {
            throw new BadRequestException("judgeType must be INTERNAL or GUEST.");
        }
        return normalized;
    }
}
