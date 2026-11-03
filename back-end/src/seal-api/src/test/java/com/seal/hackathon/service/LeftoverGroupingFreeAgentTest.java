package com.seal.hackathon.service;

import com.seal.hackathon.dto.response.GroupingCommitResponse;
import com.seal.hackathon.dto.response.GroupingPreviewResponse;
import com.seal.hackathon.entity.HackathonEvent;
import com.seal.hackathon.entity.Team;
import com.seal.hackathon.entity.TeamMember;
import com.seal.hackathon.entity.User;
import com.seal.hackathon.repository.HackathonEventRepository;
import com.seal.hackathon.repository.JoinRequestRepository;
import com.seal.hackathon.repository.TeamEventEntryRepository;
import com.seal.hackathon.repository.TeamInviteRepository;
import com.seal.hackathon.repository.TeamMemberRepository;
import com.seal.hackathon.repository.TeamRepository;
import com.seal.hackathon.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies the leftover-grouping path for teamless registrants: an active, approved
 * student on no team of the event is a free agent, and committing places them onto a
 * newly created team by creating a fresh membership (there is no prior team to move).
 */
@ExtendWith(MockitoExtension.class)
class LeftoverGroupingFreeAgentTest {

    private static final int EVENT_ID = 1;

    @Mock private TeamRepository teamRepository;
    @Mock private TeamEventEntryRepository teamEventEntryRepository;
    @Mock private TeamMemberRepository teamMemberRepository;
    @Mock private HackathonEventRepository eventRepository;
    @Mock private JoinRequestRepository joinRequestRepository;
    @Mock private TeamInviteRepository teamInviteRepository;
    @Mock private UserRepository userRepository;
    @Mock private NotificationService notificationService;
    @Mock private AuditLogService auditLogService;
    @Mock private HackathonEventService hackathonEventService;

    @InjectMocks private LeftoverGroupingService service;

    private User student(int id, String name) {
        return User.builder().userId(id).fullName(name).userType("FPT_STUDENT")
                .isApproved(true).isActive(true).build();
    }

    @Test
    void previewCountsTeamlessRegistrantsAsFreeAgents() {
        HackathonEvent event = mockSetupEvent(false);
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
        when(teamEventEntryRepository.findAllByEvent_EventIdAndStatus(EVENT_ID, "APPROVED")).thenReturn(List.of());
        when(userRepository.findGroupableFreeAgents(EVENT_ID))
                .thenReturn(List.of(student(1, "Messi"), student(2, "Ronaldo"), student(3, "Mbappe")));

        GroupingPreviewResponse preview = service.preview(EVENT_ID);

        assertEquals(3, preview.getLeftoverPeople(), "3 teamless registrants are leftover people");
        assertEquals(3, preview.getSoloCount(), "each teamless registrant is a solo free agent");
        assertEquals(1, preview.getProposedTeams().size(), "3 free agents form exactly one team");
        assertEquals(3, preview.getProposedTeams().get(0).getMembers().size());
    }

    @Test
    void commitCreatesMembershipsForTeamlessFreeAgents() {
        HackathonEvent event = mockSetupEvent(true);
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
        when(teamEventEntryRepository.findAllByEvent_EventIdAndStatus(EVENT_ID, "APPROVED")).thenReturn(List.of());
        when(userRepository.findGroupableFreeAgents(EVENT_ID))
                .thenReturn(List.of(student(1, "Messi"), student(2, "Ronaldo"), student(3, "Mbappe")));
        when(teamEventEntryRepository.existsByEventIdAndNormalizedName(anyInt(), anyString())).thenReturn(false);
        when(teamRepository.save(any(Team.class))).thenAnswer(inv -> inv.getArgument(0));
        when(teamMemberRepository.save(any(TeamMember.class))).thenAnswer(inv -> inv.getArgument(0));

        GroupingCommitResponse result = service.commit(EVENT_ID, 99, "setup grouping");

        assertEquals(1, result.getTeamsCreated(), "one new team is created");
        assertEquals(3, result.getPeoplePlaced(), "all three free agents are placed");
        // Teamless free agents have no prior membership → one is created per person.
        verify(teamMemberRepository, atLeast(3)).save(any(TeamMember.class));
        verify(teamRepository).save(any(Team.class));
    }

    private HackathonEvent mockSetupEvent(boolean forCommit) {
        HackathonEvent event = org.mockito.Mockito.mock(HackathonEvent.class);
        when(event.getStatus()).thenReturn("SETUP");
        if (forCommit) {
            when(event.getEventId()).thenReturn(EVENT_ID);
            when(event.getName()).thenReturn("SEAL Demo Summer 2026");
        }
        return event;
    }
}
