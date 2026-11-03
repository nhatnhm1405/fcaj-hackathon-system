package com.seal.hackathon.repository;

import com.seal.hackathon.entity.Team;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TeamRepository extends JpaRepository<Team, Integer> {

    // Serializes concurrent roster mutations (e.g. two invites being accepted
    // at once) — this is about the roster (TeamMember), which stays on Team,
    // not about season-scoped state (which lives on TeamEventEntry now).
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Team t WHERE t.teamId = :teamId")
    Optional<Team> findByIdForUpdate(@Param("teamId") Integer teamId);
}
