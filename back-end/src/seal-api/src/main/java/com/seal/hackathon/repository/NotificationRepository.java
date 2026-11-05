package com.seal.hackathon.repository;

import com.seal.hackathon.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Integer> {
    List<Notification> findByRecipient_UserIdOrderByCreatedAtDesc(Integer userId);
    List<Notification> findByRecipient_UserIdAndIsReadFalseOrderByCreatedAtDesc(Integer userId);
    long countByRecipient_UserIdAndIsReadFalse(Integer userId);
    void deleteAllByRecipient_UserIdOrSender_UserId(Integer recipientUserId, Integer senderUserId);
}
