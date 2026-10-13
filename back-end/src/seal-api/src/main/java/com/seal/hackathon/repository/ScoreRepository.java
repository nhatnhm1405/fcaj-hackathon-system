package com.seal.hackathon.repository;

import com.seal.hackathon.entity.Score;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ScoreRepository extends JpaRepository<Score, Integer> {
    List<Score> findAllBySubmission_SubmissionId(Integer submissionId);
    List<Score> findAllByJudge_UserIdAndSubmission_Round_RoundId(Integer judgeId, Integer roundId);
    List<Score> findAllBySubmission_Round_RoundId(Integer roundId);
    @Query("""
            SELECT s
            FROM Score s
            JOIN FETCH s.submission submission
            JOIN FETCH submission.team
            JOIN FETCH s.judge
            JOIN FETCH s.criteria
            WHERE submission.round.roundId = :roundId
              AND s.isDraft = false
            """)
    List<Score> findAllFinalizedByRoundWithDetails(@Param("roundId") Integer roundId);
    List<Score> findAllByJudge_UserIdAndSubmission_Round_RoundIdAndIsDraftFalse(
            Integer judgeId, Integer roundId);
    Optional<Score> findBySubmission_SubmissionIdAndJudge_UserIdAndCriteria_CriteriaId(
            Integer submissionId, Integer judgeUserId, Integer criteriaId);
    boolean existsBySubmission_SubmissionIdAndJudge_UserId(Integer submissionId, Integer judgeId);
    boolean existsBySubmission_SubmissionIdAndJudge_UserIdAndIsDraftFalse(Integer submissionId, Integer judgeId);
    boolean existsByCriteria_CriteriaId(Integer criteriaId);
}
