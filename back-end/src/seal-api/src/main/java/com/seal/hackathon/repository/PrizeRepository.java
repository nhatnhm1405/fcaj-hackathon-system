package com.seal.hackathon.repository;

import com.seal.hackathon.entity.Prize;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PrizeRepository extends JpaRepository<Prize, Integer> {

    boolean existsByTrack_TrackId(Integer trackId);

    // Coordinator view — every slot, draft or announced.
    List<Prize> findAllByEvent_EventIdOrderByRankPosition(Integer eventId);

    // Public view - only announced prizes.
    List<Prize> findAllByEvent_EventIdAndAwardedAtIsNotNullOrderByRankPosition(Integer eventId);

    Optional<Prize> findFirstByTeam_TeamIdAndAwardedAtIsNotNullOrderByRankPositionAsc(Integer teamId);
}
