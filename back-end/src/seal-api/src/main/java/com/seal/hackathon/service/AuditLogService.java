package com.seal.hackathon.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seal.hackathon.dto.response.AuditLogResponse;
import com.seal.hackathon.entity.AuditLog;
import com.seal.hackathon.entity.User;
import com.seal.hackathon.repository.AuditLogRepository;
import com.seal.hackathon.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Writes competition BUSINESS-action audit entries (AuditLog) — the counterpart to
 * {@link SystemLogService} (platform/admin actions). Best-effort: a logging failure
 * (e.g. an unknown actor) never blocks the business action that triggered it.
 */
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Full form — records an action on a target with an optional reason + metadata. */
    @Transactional
    public void record(Integer actorUserId, String action, String targetType,
                       Integer targetId, String reason, Map<String, Object> metadata) {
        User actor = userRepository.findById(actorUserId).orElse(null);
        if (actor == null) {
            return; // best-effort trail — don't fail the caller over a missing actor
        }
        auditLogRepository.save(AuditLog.builder()
                .actor(actor)
                .action(action)
                .targetType(targetType)
                .targetId(targetId)
                .reason(reason)
                .metadataJson(toJson(metadata))
                .build());
    }

    /** Convenience form — action on a target, no reason/metadata. */
    @Transactional
    public void record(Integer actorUserId, String action, String targetType, Integer targetId) {
        record(actorUserId, action, targetType, targetId, null, null);
    }

    /**
     * Newest-first audit entries directly scoped to one event (target EVENT/eventId)
     * — e.g. CREATE_EVENT, COMPLETE_EVENT, DRAW_TRACKS, REDRAW_TRACKS. Actions logged
     * against other targets (a TEAM, a ROUND, a USER) are NOT included here; widening
     * "an event's audit" to those is a future query-design decision.
     */
    @Transactional(readOnly = true)
    public List<AuditLogResponse> getLogsForEvent(Integer eventId) {
        return auditLogRepository.findByTargetWithActor("EVENT", eventId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /** Serialises a metadata map to JSON (escaping handled by Jackson); null/empty → null. */
    private String toJson(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            return null; // best-effort — never block the business action over metadata
        }
    }

    private AuditLogResponse mapToResponse(AuditLog log) {
        return AuditLogResponse.builder()
                .logId(log.getLogId())
                .actorUserId(log.getActor().getUserId())
                .actorName(log.getActor().getFullName())
                .action(log.getAction())
                .targetType(log.getTargetType())
                .targetId(log.getTargetId())
                .reason(log.getReason())
                .metadataJson(log.getMetadataJson())
                .ipAddress(log.getIpAddress())
                .createdAt(log.getCreatedAt())
                .build();
    }
}
