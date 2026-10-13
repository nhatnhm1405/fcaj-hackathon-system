package com.seal.hackathon.repository;

import com.seal.hackathon.entity.RoundTimerNotice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RoundTimerNoticeRepository extends JpaRepository<RoundTimerNotice, Integer> {

    List<RoundTimerNotice> findByRoundIdAndPhase(Integer roundId, String phase);

    boolean existsByRoundIdAndPhaseAndMilestoneKey(Integer roundId, String phase, String milestoneKey);

    // Clears a phase's fired marks so a fresh run (start after stop/expire) can re-fire them.
    void deleteByRoundIdAndPhase(Integer roundId, String phase);
}
