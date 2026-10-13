package com.seal.hackathon.repository;

import com.seal.hackathon.entity.RoundResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoundResultRepository extends JpaRepository<RoundResult, Integer> {
    List<RoundResult> findAllByRound_RoundIdOrderByRankPosition(Integer roundId);
    List<RoundResult> findAllByRound_RoundIdAndIsPublishedTrueOrderByRankPosition(Integer roundId);
    Optional<RoundResult> findByTeam_TeamIdAndRound_RoundId(Integer teamId, Integer roundId);
    List<RoundResult> findAllByTeam_TeamIdAndRound_Event_EventId(Integer teamId, Integer eventId);

    @Query("""
            SELECT rr
            FROM RoundResult rr
            JOIN FETCH rr.round r
            WHERE rr.team.teamId = :teamId
            ORDER BY r.orderNumber ASC
            """)
    List<RoundResult> findAllByTeamIdOrderByRoundOrder(@Param("teamId") Integer teamId);
}
