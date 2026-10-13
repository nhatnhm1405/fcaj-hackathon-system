package com.seal.hackathon.service;

import com.seal.hackathon.dto.request.CompleteProfileRequest;
import com.seal.hackathon.dto.request.RegisterRequest;
import com.seal.hackathon.dto.request.UpdateProfileRequest;
import com.seal.hackathon.dto.response.UserResponse;
import com.seal.hackathon.entity.HackathonEvent;
import com.seal.hackathon.entity.Team;
import com.seal.hackathon.entity.TeamEventEntry;
import com.seal.hackathon.entity.TeamMember;
import com.seal.hackathon.entity.User;
import com.seal.hackathon.exception.BadRequestException;
import com.seal.hackathon.repository.TeamEventEntryRepository;
import com.seal.hackathon.repository.TeamMemberRepository;
import com.seal.hackathon.repository.UserRepository;
import com.seal.hackathon.security.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private TeamMemberRepository teamMemberRepository;

    @Mock
    private TeamEventEntryRepository teamEventEntryRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthService authService;

    @Test
    void register_shouldRejectInvalidFptStudentId() {
        RegisterRequest request = fptRegistration("SE230000");

        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> authService.register(request));

        assertEquals(
                "FPT student ID must start with HE, SE, DE, QE, or CE followed by "
                        + "6 digits from 000000 to 229999.",
                exception.getMessage());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void register_shouldAcceptAndTrimValidFptStudentId() {
        RegisterRequest request = fptRegistration(" CE229999 ");
        when(passwordEncoder.encode(request.getPassword())).thenReturn("hashed-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User savedUser = invocation.getArgument(0);
            savedUser.setUserId(101);
            return savedUser;
        });

        authService.register(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertEquals("CE229999", userCaptor.getValue().getStudentId());
    }

    @Test
    void completeProfile_shouldRejectInvalidFptStudentIdForOAuthSignup() {
        User oauthUser = User.builder()
                .userId(101)
                .email("oauth.student@fpt.edu.vn")
                .fullName("OAuth Student")
                .userType("PENDING_PROFILE")
                .provider("GOOGLE")
                .isApproved(false)
                .isActive(true)
                .build();
        CompleteProfileRequest request = new CompleteProfileRequest();
        request.setUserType("FPT_STUDENT");
        request.setStudentId("se123456");
        when(userRepository.findByEmailWithRoles(oauthUser.getEmail())).thenReturn(Optional.of(oauthUser));

        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> authService.completeProfile(oauthUser.getEmail(), request));

        assertEquals(
                "FPT student ID must start with HE, SE, DE, QE, or CE followed by "
                        + "6 digits from 000000 to 229999.",
                exception.getMessage());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void updateOwnProfile_shouldUpdateEditableFieldsButKeepStudentId() {
        User user = student();
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setFullName(" Nguyen Updated ");
        request.setUniversity(" FPT University ");

        when(userRepository.findByEmailWithRoles(user.getEmail())).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        UserResponse response = authService.updateOwnProfile(user.getEmail(), request);

        assertEquals("Nguyen Updated", response.getFullName());
        assertEquals("SE123456", response.getStudentId());
        assertEquals("FPT University", response.getUniversity());
        verify(userRepository).save(user);
    }

    @Test
    void updateOwnProfile_shouldAllowUnchangedStudentIdEcho() {
        User user = student();
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setStudentId(" SE123456 ");

        when(userRepository.findByEmailWithRoles(user.getEmail())).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        UserResponse response = authService.updateOwnProfile(user.getEmail(), request);

        assertEquals("SE123456", response.getStudentId());
        verify(userRepository).save(user);
    }

    @Test
    void updateOwnProfile_shouldRejectStudentIdChange() {
        User user = student();
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setStudentId("SE999999");

        when(userRepository.findByEmailWithRoles(user.getEmail())).thenReturn(Optional.of(user));

        assertThrows(BadRequestException.class, () -> authService.updateOwnProfile(user.getEmail(), request));
        verify(userRepository, never()).save(user);
    }

    @Test
    void getCurrentUser_shouldNotExposeCompletedTeamAsCurrentTeam() {
        User user = student();
        HackathonEvent completedEvent = event(1, "COMPLETED");
        Team completedTeam = team(10, completedEvent);
        TeamMember oldMembership = member(1, completedTeam, user, "LEADER");

        when(userRepository.findByEmailWithRoles(user.getEmail())).thenReturn(Optional.of(user));
        when(teamMemberRepository.findByUser_UserIdOrderByIdDesc(user.getUserId()))
                .thenReturn(List.of(oldMembership));

        UserResponse response = authService.getCurrentUser(user.getEmail());

        assertNull(response.getTeamId());
        assertNull(response.getIsLeader());
    }

    @Test
    void getCurrentUser_shouldExposeOpenTeamAsCurrentTeam() {
        User user = student();
        HackathonEvent openEvent = event(1, "OPEN");
        Team currentTeam = team(10, openEvent);
        TeamMember currentMembership = member(1, currentTeam, user, "LEADER");

        when(userRepository.findByEmailWithRoles(user.getEmail())).thenReturn(Optional.of(user));
        when(teamMemberRepository.findByUser_UserIdOrderByIdDesc(user.getUserId()))
                .thenReturn(List.of(currentMembership));

        UserResponse response = authService.getCurrentUser(user.getEmail());

        assertEquals(10, response.getTeamId());
        assertEquals("LEADER", response.getIsLeader());
    }

    private User student() {
        return User.builder()
                .userId(100)
                .email("student@fpt.edu.vn")
                .fullName("Nguyen Student")
                .userType("FPT_STUDENT")
                .studentId("SE123456")
                .university("FPT")
                .isApproved(true)
                .isActive(true)
                .provider("LOCAL")
                .build();
    }

    private RegisterRequest fptRegistration(String studentId) {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("new.student@fpt.edu.vn");
        request.setPassword("Test@1234");
        request.setFullName("New Student");
        request.setUserType("FPT_STUDENT");
        request.setStudentId(studentId);
        return request;
    }

    private HackathonEvent event(Integer eventId, String status) {
        return HackathonEvent.builder()
                .eventId(eventId)
                .name("SEAL Hackathon " + eventId)
                .season("Spring")
                .year(2026)
                .startDate(LocalDateTime.now().plusDays(10))
                .endDate(LocalDateTime.now().plusDays(12))
                .status(status)
                .build();
    }

    private Team team(Integer teamId, HackathonEvent event) {
        Team team = Team.builder()
                .teamId(teamId)
                .name("Seal Team")
                .build();
        TeamEventEntry entry = TeamEventEntry.builder()
                .id(teamId)
                .team(team)
                .event(event)
                .status("APPROVED")
                .build();
        when(teamEventEntryRepository.findAllByTeam_TeamId(teamId)).thenReturn(List.of(entry));
        return team;
    }

    private TeamMember member(Integer id, Team team, User user, String role) {
        return TeamMember.builder()
                .id(id)
                .team(team)
                .user(user)
                .memberRole(role)
                .build();
    }
}
