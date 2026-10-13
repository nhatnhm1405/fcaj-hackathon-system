package com.seal.hackathon.service;

import com.seal.hackathon.dto.request.LoginRequest;
import com.seal.hackathon.dto.request.RegisterRequest;
import com.seal.hackathon.dto.response.AuthResponse;
import com.seal.hackathon.dto.response.UserResponse;
import com.seal.hackathon.entity.TeamMember;
import com.seal.hackathon.entity.User;
import com.seal.hackathon.exception.BadRequestException;
import com.seal.hackathon.exception.ForbiddenException;
import com.seal.hackathon.exception.ResourceNotFoundException;
import com.seal.hackathon.exception.UnauthorizedException;
import com.seal.hackathon.repository.TeamEventEntryRepository;
import com.seal.hackathon.repository.TeamMemberRepository;
import com.seal.hackathon.repository.UserRepository;
import com.seal.hackathon.security.JwtService;
import com.seal.hackathon.security.UserPrincipal;
import com.seal.hackathon.validation.FptStudentIdValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final TeamEventEntryRepository teamEventEntryRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    // ---------------------------------------------------------------
    // Register
    // ---------------------------------------------------------------

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.getEmail().toLowerCase().trim();
        String userType = request.getUserType().toUpperCase().trim();
        String studentId = normalizeOptionalText(request.getStudentId());
        String university = normalizeOptionalText(request.getUniversity());

        // 1. Email uniqueness
        if (userRepository.existsByEmail(email)) {
            throw new BadRequestException("An account with this email already exists.");
        }

        // 2. UserType-specific field validation
        validateUserTypeFields(userType, studentId, university, request.getUserType());

        // 2b. Student ID uniqueness
        if (studentId != null && userRepository.existsByStudentId(studentId)) {
            throw new BadRequestException("An account with this student ID already exists.");
        }

        // 3. Create and save the User.
        // No role assignment here — participants are identified by user_type.
        // Staff roles (COORDINATOR, MENTOR, JUDGE) are assigned later by a coordinator
        // via UserEventRole.
        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName().trim())
                .userType(userType)
                .studentId(studentId)
                .university(university)
                .isApproved(false)
                .isActive(true)
                .provider("LOCAL")
                .build();

        user = userRepository.save(user);

        return AuthResponse.builder()
                .userId(user.getUserId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .message("Registration successful. Please wait for an Event Coordinator to approve your account.")
                .build();
    }

    public boolean checkStudentId(String studentId) {
        if (studentId == null || studentId.isBlank()) return false;
        return userRepository.existsByStudentId(studentId.trim());
    }

    // ---------------------------------------------------------------
    // Login
    // ---------------------------------------------------------------

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        // 1. Find user by email
        User user = userRepository.findByEmailWithRoles(request.getEmail().toLowerCase().trim())
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password."));

        // 2. Check password
        if (user.getPasswordHash() == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid email or password.");
        }

        // 3. Check is_approved
        if (!Boolean.TRUE.equals(user.getIsApproved())) {
            throw new ForbiddenException(
                    "Your account is pending approval. Please wait for an Event Coordinator to review your registration.");
        }

        // 3b. Inactive accounts. Students stay able to log in — they need to send a
        // "request to compete" from the dashboard to be reactivated. Everyone else
        // (e.g. a guest judge whose event has ended) has no self-service path, so an
        // inactive account is barred until a System Admin reactivates it.
        if (!Boolean.TRUE.equals(user.getIsActive()) && !isStudent(user)) {
            throw new ForbiddenException(
                    "Your account is inactive. Please contact a System Admin to be reactivated.");
        }

        // 4. Generate JWT
        UserPrincipal principal = new UserPrincipal(user);
        List<String> roles = principal.getAuthorities().stream()
                .map(a -> a.getAuthority().replace("ROLE_", ""))
                .collect(Collectors.toList());

        String token = jwtService.generateToken(principal, user.getUserId(), roles);

        return AuthResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .userId(user.getUserId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .roles(roles)
                .message("Login successful.")
                .build();
    }

    private boolean isStudent(User user) {
        String t = user.getUserType();
        return "FPT_STUDENT".equalsIgnoreCase(t) || "EXTERNAL_STUDENT".equalsIgnoreCase(t);
    }

    // ---------------------------------------------------------------
    // Get current user info (for /api/auth/me)
    // ---------------------------------------------------------------

    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(String email) {
        User user = userRepository.findByEmailWithRoles(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));

        UserResponse response = mapToUserResponse(user);
        enrichCurrentTeamInfo(response, user.getUserId());
        return response;
    }

    /**
     * First-time OAuth user finishes signup: picks userType (FPT_STUDENT /
     * EXTERNAL_STUDENT / STAFF) and supplies student details. Only valid while
     * userType is still the PENDING_PROFILE placeholder. Approval is unchanged.
     */
    @Transactional
    public UserResponse completeProfile(String email, com.seal.hackathon.dto.request.CompleteProfileRequest request) {
        User user = userRepository.findByEmailWithRoles(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));
        if (!"PENDING_PROFILE".equals(user.getUserType())) {
            throw new BadRequestException("Your profile is already complete.");
        }
        String type = request.getUserType() == null ? "" : request.getUserType().toUpperCase().trim();
        // OAuth self-signup is for STUDENTS only — staff accounts (judge/mentor/
        // coordinator) are provisioned by an administrator, not self-registered.
        if (!type.equals("FPT_STUDENT") && !type.equals("EXTERNAL_STUDENT")) {
            throw new BadRequestException("Sign-up via Google/GitHub is for students only. "
                    + "Staff accounts are created by an administrator.");
        }
        String studentId = normalizeOptionalText(request.getStudentId());
        if (studentId == null) {
            throw new BadRequestException("Student ID is required.");
        }
        if (type.equals("FPT_STUDENT") && !FptStudentIdValidator.isValid(studentId)) {
            throw new BadRequestException(FptStudentIdValidator.INVALID_MESSAGE);
        }
        if (userRepository.existsByStudentId(studentId)) {
            throw new BadRequestException("An account with this student ID already exists.");
        }
        if (type.equals("EXTERNAL_STUDENT") && (request.getUniversity() == null || request.getUniversity().isBlank())) {
            throw new BadRequestException("University is required for external students.");
        }
        user.setUserType(type);
        user.setStudentId(studentId);
        user.setUniversity(request.getUniversity() != null && !request.getUniversity().isBlank() ? request.getUniversity().trim() : null);
        userRepository.save(user);
        return mapToUserResponse(user);
    }

    /** A user patches their own profile (fullName / university). */
    @Transactional
    public UserResponse updateOwnProfile(String email, com.seal.hackathon.dto.request.UpdateProfileRequest request) {
        User user = userRepository.findByEmailWithRoles(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));
        if (request.getStudentId() != null
                && !Objects.equals(normalizeOptionalText(request.getStudentId()), normalizeOptionalText(user.getStudentId()))) {
            throw new BadRequestException("Student ID cannot be changed from profile settings.");
        }
        if (request.getFullName() != null && !request.getFullName().isBlank()) {
            user.setFullName(request.getFullName().trim());
        }
        if (request.getUniversity() != null) {
            user.setUniversity(request.getUniversity().isBlank() ? null : request.getUniversity().trim());
        }
        userRepository.save(user);
        return mapToUserResponse(user);
    }

    /** A signed-in user changes their own password by proving the current one. */
    @Transactional
    public void changePassword(String email, com.seal.hackathon.dto.request.ChangePasswordRequest request) {
        User user = userRepository.findByEmailWithRoles(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));

        // OAuth-only accounts have no local password to change.
        if (!"LOCAL".equalsIgnoreCase(user.getProvider()) || user.getPasswordHash() == null) {
            throw new BadRequestException("This account signs in with Google/GitHub and has no password to change.");
        }
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new BadRequestException("Current password is incorrect.");
        }
        if (passwordEncoder.matches(request.getNewPassword(), user.getPasswordHash())) {
            throw new BadRequestException("New password must be different from the current password.");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    /** A user replaces their own profile picture. Stores the file on disk and
     *  saves the public URL (/uploads/avatars/...) on the user record. */
    @Transactional
    public UserResponse updateAvatar(String email, MultipartFile file) {
        User user = userRepository.findByEmailWithRoles(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));

        if (file == null || file.isEmpty()) {
            throw new BadRequestException("No image file was uploaded.");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new BadRequestException("Only image files are allowed.");
        }
        if (file.getSize() > 5L * 1024 * 1024) {
            throw new BadRequestException("Image must be 5MB or smaller.");
        }

        try {
            Path avatarDir = Paths.get(uploadDir, "avatars").toAbsolutePath().normalize();
            Files.createDirectories(avatarDir);
            String filename = "avatar_" + user.getUserId() + "_" + System.currentTimeMillis()
                    + extensionFor(contentType);
            Path target = avatarDir.resolve(filename);
            file.transferTo(target.toFile());
            user.setAvatarUrl("/uploads/avatars/" + filename);
            userRepository.save(user);
            return mapToUserResponse(user);
        } catch (IOException e) {
            throw new BadRequestException("Failed to store the uploaded image.");
        }
    }

    /** A user removes their own profile picture. Clears avatarUrl and best-effort
     *  deletes the stored file (only files we host under /uploads/). */
    @Transactional
    public UserResponse removeAvatar(String email) {
        User user = userRepository.findByEmailWithRoles(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));

        String current = user.getAvatarUrl();
        if (current != null && current.startsWith("/uploads/")) {
            try {
                // current is like /uploads/avatars/foo.png — strip the leading /uploads/
                Path file = Paths.get(uploadDir, current.substring("/uploads/".length()))
                        .toAbsolutePath().normalize();
                Files.deleteIfExists(file);
            } catch (IOException ignored) {
                // A leftover file is harmless; clearing the reference is what matters.
            }
        }
        user.setAvatarUrl(null);
        userRepository.save(user);
        return mapToUserResponse(user);
    }

    private String extensionFor(String contentType) {
        return switch (contentType) {
            case "image/png" -> ".png";
            case "image/gif" -> ".gif";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
    }

    private String normalizeOptionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private void validateUserTypeFields(
            String userType,
            String studentId,
            String university,
            String requestedUserType) {
        switch (userType) {
            case "FPT_STUDENT" -> {
                if (studentId == null) {
                    throw new BadRequestException("Student ID is required for FPT students.");
                }
                if (!FptStudentIdValidator.isValid(studentId)) {
                    throw new BadRequestException(FptStudentIdValidator.INVALID_MESSAGE);
                }
            }
            case "EXTERNAL_STUDENT" -> {
                if (studentId == null) {
                    throw new BadRequestException("Student ID is required for external students.");
                }
                if (university == null) {
                    throw new BadRequestException("University name is required for external students.");
                }
            }
            case "STAFF" -> {
                // No extra required fields for staff (coordinators, mentors, judges)
            }
            default -> throw new BadRequestException(
                    "Invalid user type: " + requestedUserType +
                            ". Must be one of: FPT_STUDENT, EXTERNAL_STUDENT, STAFF.");
        }
    }

    public UserResponse mapToUserResponse(User user) {
        List<String> roles = user.getUserEventRoles().stream()
                .map(uer -> uer.getRole().getRoleName())
                .distinct()
                .sorted()
                .collect(Collectors.toList());

        return UserResponse.builder()
                .userId(user.getUserId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .userType(user.getUserType())
                .judgeType(user.getJudgeType())
                .studentId(user.getStudentId())
                .university(user.getUniversity())
                .judgeType(user.getJudgeType())
                .isApproved(user.getIsApproved())
                .isActive(user.getIsActive())
                .expiredAt(user.getExpiredAt())
                .provider(user.getProvider())
                .avatarUrl(user.getAvatarUrl())
                .roles(roles)
                .createdAt(user.getCreatedAt())
                .build();
    }

    private void enrichCurrentTeamInfo(UserResponse response, Integer userId) {
        List<TeamMember> memberships = teamMemberRepository.findByUser_UserIdOrderByIdDesc(userId);
        if (memberships.isEmpty()) {
            return;
        }

        memberships.stream()
                .filter(this::isCurrentEventMembership)
                .findFirst()
                .ifPresent(membership -> {
                    response.setTeamId(membership.getTeam().getTeamId());
                    response.setIsLeader(normalizeTeamRole(membership));
                });
    }

    private boolean isCurrentEventMembership(TeamMember membership) {
        return teamEventEntryRepository.findAllByTeam_TeamId(membership.getTeam().getTeamId()).stream()
                .map(entry -> entry.getEvent().getStatus())
                .anyMatch(status -> "OPEN".equalsIgnoreCase(status)
                        || "SETUP".equalsIgnoreCase(status)
                        || "IN_PROGRESS".equalsIgnoreCase(status));
    }

    private String normalizeTeamRole(TeamMember membership) {
        if ("LEADER".equalsIgnoreCase(membership.getMemberRole())) {
            return "LEADER";
        }
        return "MEMBER";
    }
}
