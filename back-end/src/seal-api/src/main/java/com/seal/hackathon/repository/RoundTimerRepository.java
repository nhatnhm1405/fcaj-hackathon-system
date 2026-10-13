package com.seal.hackathon.repository;

import com.seal.hackathon.entity.RoundTimer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoundTimerRepository extends JpaRepository<RoundTimer, Integer> {

    Optional<RoundTimer> findByRound_RoundIdAndPhase(Integer roundId, String phase);

    List<RoundTimer> findByRound_RoundId(Integer roundId);
}
