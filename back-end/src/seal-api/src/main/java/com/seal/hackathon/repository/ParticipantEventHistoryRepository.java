package com.seal.hackathon.repository;

import com.seal.hackathon.entity.ParticipantEventHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ParticipantEventHistoryRepository extends JpaRepository<ParticipantEventHistory, Integer> {

    List<ParticipantEventHistory> findByUser_UserId(Integer userId);

    Optional<ParticipantEventHistory> findByUser_UserIdAndEventId(Integer userId, Integer eventId);
}
