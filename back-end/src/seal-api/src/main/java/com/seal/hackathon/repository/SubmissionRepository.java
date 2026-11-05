package com.seal.hackathon.repository;

import com.seal.hackathon.entity.Submission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SubmissionRepository extends JpaRepository<Submission, Integer> {
    Optional<Submission> findByTeam_TeamIdAndRound_RoundId(Integer teamId, Integer roundId);
    List<Submission> findAllByRound_RoundId(Integer roundId);
    List<Submission> findAllByTeam_TeamId(Integer teamId);

    @Query("""
            SELECT s
            FROM Submission s
            JOIN FETCH s.team t
            JOIN FETCH s.round r
            JOIN FETCH s.submittedBy u
            WHERE r.event.eventId = :eventId
            ORDER BY r.orderNumber ASC, t.name ASC, s.submittedAt ASC
            """)
    List<Submission> findAllByEventIdForExport(@Param("eventId") Integer eventId);

    @Query("""
            SELECT DISTINCT s
            FROM Submission s
            JOIN s.round r
            JOIN s.team t
            LEFT JOIN TeamEventEntry te ON te.team = t AND te.event = r.event
            LEFT JOIN te.track teamTrack
            JOIN JudgeAssignment ja ON ja.round = r
            WHERE s.submissionId = :submissionId
              AND ja.judge.userId = :judgeId
              AND ja.isActive = true
              AND (ja.track IS NULL OR ja.track = teamTrack)
            """)
    Optional<Submission> findBySubmissionIdAndJudgeId(
            @Param("submissionId") Integer submissionId,
            @Param("judgeId") Integer judgeId);

    @Query("""
            SELECT DISTINCT s
            FROM Submission s
            JOIN s.round r
            JOIN s.team t
            LEFT JOIN TeamEventEntry te ON te.team = t AND te.event = r.event
            LEFT JOIN te.track teamTrack
            JOIN JudgeAssignment ja ON ja.round = r
            WHERE r.roundId = :roundId
              AND ja.judge.userId = :judgeId
              AND ja.isActive = true
              AND (ja.track IS NULL OR ja.track = teamTrack)
            ORDER BY s.submittedAt DESC
            """)
    List<Submission> findAllByRoundIdAndJudgeId(
            @Param("roundId") Integer roundId,
            @Param("judgeId") Integer judgeId);
}
