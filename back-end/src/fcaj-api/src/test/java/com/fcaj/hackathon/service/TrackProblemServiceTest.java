package com.fcaj.hackathon.service;

import com.fcaj.hackathon.entity.*;
import com.fcaj.hackathon.repository.*;
import com.fcaj.hackathon.storage.*;
import com.fcaj.hackathon.exception.ForbiddenException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class TrackProblemServiceTest {
    @Mock TrackRepository tracks;
    @Mock HackathonEventRepository events;
    @Mock TeamMemberRepository members;
    @Mock AuditLogService audit;
    @Mock ObjectStorage storage;
    @Mock UserRepository users;
    @InjectMocks TrackProblemService service;
    private Track track;
    private User coordinator;
    @BeforeEach void setup() {
        track=Track.builder().trackId(2).event(HackathonEvent.builder().eventId(1).status("IN_PROGRESS").build())
                .name("Track").problemStorageKey("problems/1/2/previous.pdf").problemFileName("previous.pdf").problemReleased(true).build();
        coordinator=User.builder().userId(3).isApproved(true).build();
        coordinator.getUserEventRoles().add(UserEventRole.builder().user(coordinator).role(Role.builder().roleName("EVENT_COORDINATOR").build()).eventId(1).build());
        TransactionSynchronizationManager.initSynchronization();
    }
    @AfterEach void cleanup() { TransactionSynchronizationManager.clearSynchronization(); }
    private void approved() { when(users.findByIdWithRoles(3)).thenReturn(Optional.of(coordinator)); }
    private void finish(int status) { TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCompletion(status)); }
    @Test void replaceDeletesPreviousOnlyAfterCommit() {
        approved();when(tracks.findById(2)).thenReturn(Optional.of(track));
        service.uploadProblem(3,1,2,new MockMultipartFile("file","new.pdf","application/pdf",new byte[]{1,2,3}));
        verify(storage,never()).delete(anyString(),any());String created=track.getProblemStorageKey();assertThat(created).startsWith("problems/1/2/");
        finish(TransactionSynchronization.STATUS_COMMITTED);verify(storage).delete("problems/1/2/previous.pdf",null);verify(storage,never()).delete(created,null);
    }
    @Test void rollbackRetainsPreviousObjectAndCleansOnlyUncommittedUpload() {
        approved();when(tracks.findById(2)).thenReturn(Optional.of(track));
        service.uploadProblem(3,1,2,new MockMultipartFile("file","new.pdf","application/pdf",new byte[]{1,2,3}));String created=track.getProblemStorageKey();
        finish(TransactionSynchronization.STATUS_ROLLED_BACK);verify(storage).delete(created,null);verify(storage,never()).delete("problems/1/2/previous.pdf",null);
    }
    @Test void coordinatorGrantMustMatchEventForMutationAndPreview() {
        approved();coordinator.getUserEventRoles().getFirst().setEventId(99);when(tracks.findById(2)).thenReturn(Optional.of(track));
        assertThatThrownBy(() -> service.uploadProblem(3,1,2,new MockMultipartFile("file",new byte[]{1}))).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.downloadProblem(1,2,3,true)).isInstanceOf(ForbiddenException.class);verifyNoInteractions(storage);
    }
    @Test void globalCoordinatorGrantDoesNotAuthorizeAnEvent() {
        approved();coordinator.getUserEventRoles().getFirst().setEventId(null);
        assertThatThrownBy(() -> service.releaseProblem(3,1,2)).isInstanceOf(ForbiddenException.class);verifyNoInteractions(storage,tracks);
    }
    @Test void unreleasedOrWrongTrackParticipantCannotDownload() {
        when(tracks.findById(2)).thenReturn(Optional.of(track));
        assertThatThrownBy(() -> service.downloadProblem(1,2,8,false)).isInstanceOf(ForbiddenException.class);
        when(members.existsByUser_UserIdAndTeam_Track_TrackIdAndTeam_StatusIgnoreCase(8,2,"APPROVED")).thenReturn(true);track.setProblemReleased(false);
        assertThatThrownBy(() -> service.downloadProblem(1,2,8,false)).isInstanceOf(ForbiddenException.class);verifyNoInteractions(storage);
    }
    @Test void approvedTrackMemberGetsOnlyReleasedPrivateObject() {
        when(tracks.findById(2)).thenReturn(Optional.of(track));
        when(members.existsByUser_UserIdAndTeam_Track_TrackIdAndTeam_StatusIgnoreCase(8,2,"APPROVED")).thenReturn(true);
        when(storage.read(track.getProblemStorageKey(),null,20L*1024*1024)).thenReturn(new byte[]{1,2});
        assertThat(service.downloadProblem(1,2,8,false).fileName()).isEqualTo("previous.pdf");
        verify(storage).read("problems/1/2/previous.pdf",null,20L*1024*1024);
    }
    @Test void rolledBackRemovalNeverDeletesReferencedObject() {
        approved();when(tracks.findById(2)).thenReturn(Optional.of(track));service.removeProblem(3,1,2);
        finish(TransactionSynchronization.STATUS_ROLLED_BACK);verifyNoInteractions(storage);
    }
}
