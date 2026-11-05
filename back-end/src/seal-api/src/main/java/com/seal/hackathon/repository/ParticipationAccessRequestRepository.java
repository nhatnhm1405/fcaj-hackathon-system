package com.seal.hackathon.repository;

import com.seal.hackathon.entity.ParticipationAccessRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ParticipationAccessRequestRepository extends JpaRepository<ParticipationAccessRequest, Integer> {
    Optional<ParticipationAccessRequest> findByUser_UserIdAndStatus(Integer userId, String status);

    List<ParticipationAccessRequest> findByStatusOrderByRequestedAtDesc(String status);
}
