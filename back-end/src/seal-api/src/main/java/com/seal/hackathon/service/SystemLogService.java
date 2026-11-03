package com.seal.hackathon.service;

import com.seal.hackathon.dto.response.SystemLogResponse;
import com.seal.hackathon.entity.SystemLog;
import com.seal.hackathon.entity.User;
import com.seal.hackathon.repository.SystemLogRepository;
import com.seal.hackathon.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Writes and reads platform/admin events (SystemLog). Admin operations call
 * {@link #record} to leave an audit trail; the System Log screen reads via
 * {@link #getAllLogs}. SYSTEM_ADMIN-only at the API layer.
 */
@Service
@RequiredArgsConstructor
public class SystemLogService {

    private static final int MAX_ACTION_LENGTH = 50;
    private static final int MAX_DETAIL_LENGTH = 5000;

    private final SystemLogRepository systemLogRepository;
    private final UserRepository userRepository;

    /**
     * Appends a system-log entry. Invalid actor/action input is treated as a
     * no-op so malformed rows do not reach the database.
     */
    @Transactional
    public void record(Integer actorUserId, String action, String detail) {
        if (actorUserId == null) {
            return;
        }
        String normalizedAction = normalizeAction(action);
        if (normalizedAction == null) {
            return;
        }

        User actor = userRepository.findById(actorUserId).orElse(null);
        if (actor == null) {
            return; // best-effort logging — don't fail the caller over a missing actor
        }
        systemLogRepository.save(SystemLog.builder()
                .actor(actor)
                .action(normalizedAction)
                .detail(normalizeDetail(detail))
                .build());
    }

    @Transactional(readOnly = true)
    public List<SystemLogResponse> getAllLogs() {
        return systemLogRepository.findAllWithActor().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private SystemLogResponse mapToResponse(SystemLog log) {
        return SystemLogResponse.builder()
                .logId(log.getLogId())
                .actorUserId(log.getActor().getUserId())
                .actorName(log.getActor().getFullName())
                .action(log.getAction())
                .detail(log.getDetail())
                .createdAt(log.getCreatedAt())
                .build();
    }

    private String normalizeAction(String action) {
        if (action == null || action.isBlank()) {
            return null;
        }
        String normalized = action.trim()
                .replaceAll("\\s+", "_")
                .toUpperCase(Locale.ROOT);
        if (normalized.length() > MAX_ACTION_LENGTH) {
            return null;
        }
        return normalized;
    }

    private String normalizeDetail(String detail) {
        if (detail == null || detail.isBlank()) {
            return null;
        }
        String normalized = detail.trim();
        if (normalized.length() <= MAX_DETAIL_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, MAX_DETAIL_LENGTH);
    }
}
