package com.seal.hackathon.repository;

import com.seal.hackathon.entity.TeamRejoinRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TeamRejoinRequestRepository extends JpaRepository<TeamRejoinRequest, Integer> {
    Optional<TeamRejoinRequest> findByTeam_TeamIdAndStatus(Integer teamId, String status);

    List<TeamRejoinRequest> findByStatusOrderByRequestedAtDesc(String status);
}
