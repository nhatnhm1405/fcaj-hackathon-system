package com.seal.hackathon.service;

import com.seal.hackathon.dto.response.ReopenRequestResponse;
import com.seal.hackathon.entity.HackathonEvent;
import com.seal.hackathon.entity.ReopenRequest;
import com.seal.hackathon.entity.User;
import com.seal.hackathon.exception.BadRequestException;
import com.seal.hackathon.exception.ResourceNotFoundException;
import com.seal.hackathon.repository.HackathonEventRepository;
import com.seal.hackathon.repository.ReopenRequestRepository;
import com.seal.hackathon.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Handles a Coordinator's "please reopen this COMPLETED event" request and the
 * Admin's review of it. Approval delegates the actual status change to
 * {@link HackathonEventService#reopenEvent} so reopen logic lives in one place.
 */
@Service
@RequiredArgsConstructor
public class ReopenRequestService {

    private static final String PENDING = "PENDING";
    private static final String APPROVED = "APPROVED";
    private static final String REJECTED = "REJECTED";

    private final ReopenRequestRepository reopenRequestRepository;
    private final HackathonEventRepository hackathonEventRepository;
    private final UserRepository userRepository;
    private final HackathonEventService hackathonEventService;
    private final NotificationService notificationService;

    // ── Coordinator: file a reopen request ────────────────────────────

    @Transactional
    public ReopenRequestResponse create(Integer eventId, String reason, Integer requesterId) {
        HackathonEvent event = hackathonEventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + eventId));
        if (!"COMPLETED".equalsIgnoreCase(event.getStatus())) {
            throw new BadRequestException("Only a COMPLETED event can be requested for reopening.");
        }
        if (reopenRequestRepository.existsByEvent_EventIdAndStatus(eventId, PENDING)) {
            throw new BadRequestException("A reopen request for this event is already awaiting admin review.");
        }
        User requester = userRepository.findById(requesterId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + requesterId));

        ReopenRequest req = ReopenRequest.builder()
                .event(event)
                .requestedBy(requester)
                .reason(reason != null && !reason.isBlank() ? reason.trim() : null)
                .status(PENDING)
                .build();
        req = reopenRequestRepository.save(req);
        return mapToResponse(req);
    }

    // ── Coordinator/Admin: latest request for an event (or null) ──────

    @Transactional(readOnly = true)
    public ReopenRequestResponse getForEvent(Integer eventId) {
        return reopenRequestRepository
                .findFirstByEvent_EventIdOrderByCreatedAtDesc(eventId)
                .map(this::mapToResponse)
                .orElse(null);
    }

    // ── Admin: pending review queue ───────────────────────────────────

    @Transactional(readOnly = true)
    public List<ReopenRequestResponse> listPending() {
        return reopenRequestRepository.findByStatusOrderByCreatedAtDesc(PENDING).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // ── Admin: approve (reopens the event) ────────────────────────────

    @Transactional
    public ReopenRequestResponse approve(Integer requestId, Integer adminId) {
        ReopenRequest req = loadPending(requestId);
        // Delegates the COMPLETED -> IN_PROGRESS transition (and its validation).
        hackathonEventService.reopenEvent(req.getEvent().getEventId());
        resolve(req, APPROVED, adminId);

        notificationService.createNotification(
                req.getRequestedBy().getUserId(),
                "Reopen request approved",
                "Your request to reopen \"" + req.getEvent().getName()
                        + "\" was approved. The event is active again.",
                "APPROVAL");
        return mapToResponse(req);
    }

    // ── Admin: reject ─────────────────────────────────────────────────

    @Transactional
    public ReopenRequestResponse reject(Integer requestId, Integer adminId) {
        ReopenRequest req = loadPending(requestId);
        resolve(req, REJECTED, adminId);

        notificationService.createNotification(
                req.getRequestedBy().getUserId(),
                "Reopen request rejected",
                "Your request to reopen \"" + req.getEvent().getName()
                        + "\" was rejected by the System Admin.",
                "APPROVAL");
        return mapToResponse(req);
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private ReopenRequest loadPending(Integer requestId) {
        ReopenRequest req = reopenRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Reopen request not found: " + requestId));
        if (!PENDING.equals(req.getStatus())) {
            throw new BadRequestException("This reopen request has already been " + req.getStatus().toLowerCase() + ".");
        }
        return req;
    }

    private void resolve(ReopenRequest req, String status, Integer adminId) {
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + adminId));
        req.setStatus(status);
        req.setResolvedBy(admin);
        req.setResolvedAt(LocalDateTime.now());
        reopenRequestRepository.save(req);
    }

    private ReopenRequestResponse mapToResponse(ReopenRequest r) {
        return ReopenRequestResponse.builder()
                .requestId(r.getRequestId())
                .eventId(r.getEvent().getEventId())
                .eventName(r.getEvent().getName())
                .requestedById(r.getRequestedBy().getUserId())
                .requesterName(r.getRequestedBy().getFullName())
                .requesterEmail(r.getRequestedBy().getEmail())
                .reason(r.getReason())
                .status(r.getStatus())
                .resolvedById(r.getResolvedBy() != null ? r.getResolvedBy().getUserId() : null)
                .resolverName(r.getResolvedBy() != null ? r.getResolvedBy().getFullName() : null)
                .createdAt(r.getCreatedAt())
                .resolvedAt(r.getResolvedAt())
                .build();
    }
}
