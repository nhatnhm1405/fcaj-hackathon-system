package com.seal.hackathon.service;

import com.seal.hackathon.dto.request.AssignMentorRequest;
import com.seal.hackathon.entity.HackathonEvent;
import com.seal.hackathon.entity.MentorAssignment;
import com.seal.hackathon.entity.Track;
import com.seal.hackathon.entity.User;
import com.seal.hackathon.exception.BadRequestException;
import com.seal.hackathon.repository.MentorAssignmentRepository;
import com.seal.hackathon.repository.PrizeRepository;
import com.seal.hackathon.repository.RoundRepository;
import com.seal.hackathon.repository.RoundResultRepository;
import com.seal.hackathon.repository.SubmissionRepository;
import com.seal.hackathon.repository.TeamEventEntryRepository;
import com.seal.hackathon.repository.TeamMemberRepository;
import com.seal.hackathon.repository.TrackRepository;
import com.seal.hackathon.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MentorAssignmentServiceTest {

    @Mock
    private MentorAssignmentRepository mentorAssignmentRepository;

    @Mock
    private TeamEventEntryRepository teamEventEntryRepository;

    @Mock
    private TeamMemberRepository teamMemberRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoundRepository roundRepository;

    @Mock
    private TrackRepository trackRepository;

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private RoundResultRepository roundResultRepository;

    @Mock
    private PrizeRepository prizeRepository;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private EventRoleGranter eventRoleGranter;

    @InjectMocks
    private MentorAssignmentService mentorAssignmentService;

    @Test
    void assignMentor_shouldRejectWhenEventIsNotSetupOrInProgress() {
        HackathonEvent event = HackathonEvent.builder()
                .eventId(1)
                .name("SEAL Hackathon")
                .status("OPEN")
                .build();
        User mentor = User.builder()
                .userId(20)
                .email("mentor@seal.test")
                .fullName("Mentor A")
                .userType("STAFF")
                .build();
        Track track = Track.builder()
                .trackId(11)
                .event(event)
                .name("Education Tech")
                .build();

        AssignMentorRequest request = new AssignMentorRequest();
        request.setMentorUserId(mentor.getUserId());
        request.setTrackId(track.getTrackId());

        when(userRepository.findByIdWithRoles(mentor.getUserId())).thenReturn(Optional.of(mentor));
        when(trackRepository.findById(track.getTrackId())).thenReturn(Optional.of(track));

        BadRequestException error = assertThrows(BadRequestException.class,
                () -> mentorAssignmentService.assignMentor(request, 99));

        assertTrue(error.getMessage().contains("SETUP or IN_PROGRESS"));
        verify(mentorAssignmentRepository, never()).save(any(MentorAssignment.class));
        verify(eventRoleGranter, never()).ensureRole(any(User.class), anyString(), anyInt());
    }
}
