package com.seal.hackathon.repository;

import com.seal.hackathon.entity.JudgeAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface JudgeAssignmentRepository extends JpaRepository<JudgeAssignment, Integer> {

    /**
     * Active judge assignments of one judge, with Round, Event and Track
     * fetched eagerly to avoid N+1 queries.
     */
    @Query("SELECT ja FROM JudgeAssignment ja " +
           "JOIN FETCH ja.round r " +
           "JOIN FETCH r.event e " +
           "LEFT JOIN FETCH ja.track t " +
           "WHERE ja.judge.userId = :judgeUserId " +
           "AND ja.isActive = true " +
           "ORDER BY ja.id DESC")
    List<JudgeAssignment> findActiveByJudge(@Param("judgeUserId") Integer judgeUserId);

    // Duplicate checks for the unique key (judge_user_id, round_id, track_id)
    boolean existsByJudge_UserIdAndRound_RoundIdAndTrack_TrackId(Integer judgeUserId, Integer roundId, Integer trackId);

    boolean existsByJudge_UserIdAndRound_RoundIdAndTrackIsNull(Integer judgeUserId, Integer roundId);

    Optional<JudgeAssignment> findByJudge_UserIdAndRound_RoundIdAndTrack_TrackId(
            Integer judgeUserId, Integer roundId, Integer trackId);

    Optional<JudgeAssignment> findByJudge_UserIdAndRound_RoundIdAndTrackIsNull(
            Integer judgeUserId, Integer roundId);

    boolean existsByJudge_UserIdAndRound_RoundIdAndIsActiveTrue(Integer judgeUserId, Integer roundId);

    List<JudgeAssignment> findAllByRound_RoundIdAndIsActiveTrue(Integer roundId);

    /** A track-scoped assignment must be removed, never widened to track = NULL. */
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM JudgeAssignment ja WHERE ja.track.trackId = :trackId")
    int deleteAllByTrackId(@Param("trackId") Integer trackId);

    /**
     * Coordinator roster view: every active judge assignment in one event, with
     * judge, round and track fetched eagerly. Ordered by round then assignment id.
     */
    @Query("SELECT ja FROM JudgeAssignment ja " +
           "JOIN FETCH ja.judge j " +
           "JOIN FETCH ja.round r " +
           "JOIN FETCH r.event e " +
           "LEFT JOIN FETCH ja.track t " +
           "WHERE e.eventId = :eventId AND ja.isActive = true " +
           "ORDER BY r.roundId, ja.id")
    List<JudgeAssignment> findActiveByEvent(@Param("eventId") Integer eventId);
}
