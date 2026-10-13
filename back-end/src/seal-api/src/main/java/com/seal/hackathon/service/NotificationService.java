package com.seal.hackathon.service;

import com.seal.hackathon.dto.response.NotificationResponse;
import com.seal.hackathon.entity.Announcement;
import com.seal.hackathon.entity.Notification;
import com.seal.hackathon.entity.User;
import com.seal.hackathon.exception.ForbiddenException;
import com.seal.hackathon.exception.ResourceNotFoundException;
import com.seal.hackathon.repository.NotificationRepository;
import com.seal.hackathon.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    // ── Get all notifications for current user ────────────────────────

    @Transactional(readOnly = true)
    public List<NotificationResponse> getMyNotifications(Integer userId) {
        return notificationRepository
                .findByRecipient_UserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // ── Get unread count ──────────────────────────────────────────────

    @Transactional(readOnly = true)
    public long getUnreadCount(Integer userId) {
        return notificationRepository.countByRecipient_UserIdAndIsReadFalse(userId);
    }

    // ── Mark a single notification as read ───────────────────────────

    @Transactional
    public NotificationResponse markAsRead(Integer userId, Integer notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found: " + notificationId));
        if (!notification.getRecipient().getUserId().equals(userId)) {
            throw new ForbiddenException("This notification does not belong to you.");
        }
        notification.setIsRead(true);
        notificationRepository.save(notification);
        return mapToResponse(notification);
    }

    // ── Mark all notifications as read ───────────────────────────────

    @Transactional
    public void markAllAsRead(Integer userId) {
        List<Notification> unread = notificationRepository
                .findByRecipient_UserIdAndIsReadFalseOrderByCreatedAtDesc(userId);
        unread.forEach(n -> n.setIsRead(true));
        notificationRepository.saveAll(unread);
    }

    // ── Internal: create a notification (used by other services) ─────

    @Transactional
    public void createNotification(Integer recipientUserId, String title, String content,
                                   String type) {
        createNotification(recipientUserId, title, content, type, null);
    }

    /** Variant used by announcements: links the notification to its source Announcement. */
    @Transactional
    public void createNotification(Integer recipientUserId, String title, String content,
                                   String type, Announcement announcement) {
        User recipient = userRepository.findById(recipientUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + recipientUserId));

        Notification notification = Notification.builder()
                .recipient(recipient)
                .title(title)
                .content(content)
                .type(type)
                .isRead(false)
                .announcement(announcement)
                .build();
        notificationRepository.save(notification);
    }

    /**
     * Variant with a direct sender (no Announcement) — e.g. a mentor support
     * request. The sender/role/scope let the UI show a "From" line and pop the
     * same email-style detail as announcements do.
     */
    @Transactional
    public void createNotification(Integer recipientUserId, String title, String content,
                                   String type, User sender, String senderRole, String scopeLabel) {
        User recipient = userRepository.findById(recipientUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + recipientUserId));

        Notification notification = Notification.builder()
                .recipient(recipient)
                .title(title)
                .content(content)
                .type(type)
                .isRead(false)
                .sender(sender)
                .senderRole(senderRole)
                .scopeLabel(scopeLabel)
                .build();
        notificationRepository.save(notification);
    }

    // ── Helper ────────────────────────────────────────────────────────

    private NotificationResponse mapToResponse(Notification n) {
        Announcement ann = n.getAnnouncement();
        String senderName = null, senderRole = null, scopeLabel = null, linkUrl = null;
        if (ann != null) {
            senderName = ann.getSender() != null ? ann.getSender().getFullName() : null;
            senderRole = ann.getSenderRole();
            scopeLabel = ann.getTrack() != null
                    ? ann.getTrack().getName()
                    : (ann.getEvent() != null ? ann.getEvent().getName() : null);
            linkUrl = ann.getLinkUrl();
        } else if (n.getSender() != null) {
            // Direct-sender notification (e.g. mentor support request).
            senderName = n.getSender().getFullName();
            senderRole = n.getSenderRole();
            scopeLabel = n.getScopeLabel();
        }
        return NotificationResponse.builder()
                .notificationId(n.getNotificationId())
                .title(n.getTitle())
                .content(n.getContent())
                .type(n.getType())
                .isRead(n.getIsRead())
                .createdAt(n.getCreatedAt())
                .senderName(senderName)
                .senderRole(senderRole)
                .scopeLabel(scopeLabel)
                .linkUrl(linkUrl)
                .build();
    }
}
