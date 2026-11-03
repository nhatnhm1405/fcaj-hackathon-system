package com.seal.hackathon.service;

import com.seal.hackathon.dto.response.SystemLogResponse;
import com.seal.hackathon.entity.SystemLog;
import com.seal.hackathon.entity.User;
import com.seal.hackathon.repository.SystemLogRepository;
import com.seal.hackathon.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemLogServiceTest {

    @Mock
    private SystemLogRepository systemLogRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private SystemLogService systemLogService;

    // ── record: valid cases ──────────────────────────────────────────

    @Test
    void record_shouldNormalizeActionAndDetail_whenInputIsValid() {
        User actor = user(1, "Admin User");
        when(userRepository.findById(1)).thenReturn(Optional.of(actor));

        systemLogService.record(1, " grant role ", "  Granted role to user#2  ");

        ArgumentCaptor<SystemLog> captor = ArgumentCaptor.forClass(SystemLog.class);
        verify(systemLogRepository).save(captor.capture());
        SystemLog saved = captor.getValue();
        assertEquals(actor, saved.getActor());
        assertEquals("GRANT_ROLE", saved.getAction());
        assertEquals("Granted role to user#2", saved.getDetail());
    }

    @Test
    void record_shouldSaveWithNullDetail_whenDetailIsNull() {
        User actor = user(1, "Admin User");
        when(userRepository.findById(1)).thenReturn(Optional.of(actor));

        systemLogService.record(1, "CREATE_USER", null);

        ArgumentCaptor<SystemLog> captor = ArgumentCaptor.forClass(SystemLog.class);
        verify(systemLogRepository).save(captor.capture());
        assertNull(captor.getValue().getDetail());
        assertEquals("CREATE_USER", captor.getValue().getAction());
    }

    @Test
    void record_shouldStoreNullDetail_whenDetailIsBlank() {
        when(userRepository.findById(1)).thenReturn(Optional.of(user(1, "Admin User")));

        systemLogService.record(1, "CREATE_USER", "   ");

        ArgumentCaptor<SystemLog> captor = ArgumentCaptor.forClass(SystemLog.class);
        verify(systemLogRepository).save(captor.capture());
        assertNull(captor.getValue().getDetail());
    }

    @Test
    void record_shouldTrimLongDetailToSafeLength() {
        when(userRepository.findById(1)).thenReturn(Optional.of(user(1, "Admin User")));

        systemLogService.record(1, "CREATE_USER", "x".repeat(5001));

        ArgumentCaptor<SystemLog> captor = ArgumentCaptor.forClass(SystemLog.class);
        verify(systemLogRepository).save(captor.capture());
        assertEquals(5000, captor.getValue().getDetail().length());
    }

    @Test
    void record_shouldAcceptActionAtExactly50Characters() {
        String action50 = "A".repeat(50);
        when(userRepository.findById(1)).thenReturn(Optional.of(user(1, "Admin User")));

        systemLogService.record(1, action50, "detail");

        ArgumentCaptor<SystemLog> captor = ArgumentCaptor.forClass(SystemLog.class);
        verify(systemLogRepository).save(captor.capture());
        assertEquals(50, captor.getValue().getAction().length());
    }

    @Test
    void record_shouldReplaceMultipleWhitespacesWithSingleUnderscore() {
        when(userRepository.findById(1)).thenReturn(Optional.of(user(1, "Admin User")));

        systemLogService.record(1, "create   user", "detail");

        ArgumentCaptor<SystemLog> captor = ArgumentCaptor.forClass(SystemLog.class);
        verify(systemLogRepository).save(captor.capture());
        assertEquals("CREATE_USER", captor.getValue().getAction());
    }

    @Test
    void record_shouldSaveDetailAtExactly5000Characters() {
        when(userRepository.findById(1)).thenReturn(Optional.of(user(1, "Admin User")));
        String detail5000 = "d".repeat(5000);

        systemLogService.record(1, "CREATE_USER", detail5000);

        ArgumentCaptor<SystemLog> captor = ArgumentCaptor.forClass(SystemLog.class);
        verify(systemLogRepository).save(captor.capture());
        assertEquals(5000, captor.getValue().getDetail().length());
        assertEquals(detail5000, captor.getValue().getDetail());
    }

    // ── record: no-op cases ──────────────────────────────────────────

    @Test
    void record_shouldNoOp_whenActorIdIsNull() {
        systemLogService.record(null, "CREATE_USER", "detail");

        verify(userRepository, never()).findById(any());
        verify(systemLogRepository, never()).save(any());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n"})
    void record_shouldNoOp_whenActionIsNullOrBlank(String action) {
        systemLogService.record(1, action, "detail");

        verify(userRepository, never()).findById(any());
        verify(systemLogRepository, never()).save(any());
    }

    @Test
    void record_shouldNoOp_whenActionExceeds50Characters() {
        systemLogService.record(1, "A".repeat(51), "detail");

        verify(userRepository, never()).findById(any());
        verify(systemLogRepository, never()).save(any());
    }

    @Test
    void record_shouldNoOp_whenActorDoesNotExist() {
        when(userRepository.findById(9)).thenReturn(Optional.empty());

        systemLogService.record(9, "CREATE_USER", "detail");

        verify(systemLogRepository, never()).save(any());
    }

    // ── getAllLogs ────────────────────────────────────────────────────

    @Test
    void getAllLogs_shouldMapActorAndLogFields() {
        User actor = user(1, "Admin User");
        LocalDateTime createdAt = LocalDateTime.of(2026, 6, 24, 10, 30);
        SystemLog log = SystemLog.builder()
                .logId(7)
                .actor(actor)
                .action("CREATE_USER")
                .detail("created user#2")
                .createdAt(createdAt)
                .build();
        when(systemLogRepository.findAllWithActor()).thenReturn(List.of(log));

        List<SystemLogResponse> responses = systemLogService.getAllLogs();

        assertEquals(1, responses.size());
        SystemLogResponse response = responses.get(0);
        assertEquals(7, response.getLogId());
        assertEquals(1, response.getActorUserId());
        assertEquals("Admin User", response.getActorName());
        assertEquals("CREATE_USER", response.getAction());
        assertEquals("created user#2", response.getDetail());
        assertEquals(createdAt, response.getCreatedAt());
    }

    @Test
    void getAllLogs_shouldReturnEmptyList_whenNoLogsExist() {
        when(systemLogRepository.findAllWithActor()).thenReturn(List.of());

        List<SystemLogResponse> responses = systemLogService.getAllLogs();

        assertTrue(responses.isEmpty());
    }

    @Test
    void getAllLogs_shouldMapMultipleLogs() {
        User actor1 = user(1, "Admin A");
        User actor2 = user(2, "Admin B");
        SystemLog log1 = SystemLog.builder()
                .logId(1).actor(actor1).action("CREATE_USER").detail("detail1")
                .createdAt(LocalDateTime.now()).build();
        SystemLog log2 = SystemLog.builder()
                .logId(2).actor(actor2).action("GRANT_ROLE").detail("detail2")
                .createdAt(LocalDateTime.now()).build();
        when(systemLogRepository.findAllWithActor()).thenReturn(List.of(log1, log2));

        List<SystemLogResponse> responses = systemLogService.getAllLogs();

        assertEquals(2, responses.size());
        assertEquals("CREATE_USER", responses.get(0).getAction());
        assertEquals("GRANT_ROLE", responses.get(1).getAction());
    }

    // ── Helper ───────────────────────────────────────────────────────

    private User user(Integer id, String fullName) {
        return User.builder()
                .userId(id)
                .fullName(fullName)
                .email("admin@seal.edu")
                .userType("STAFF")
                .build();
    }
}
