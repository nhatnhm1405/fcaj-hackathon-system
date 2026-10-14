package com.fcaj.hackathon.repository;

import com.fcaj.hackathon.entity.ScoringCriteria;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ScoringCriteriaRepository extends JpaRepository<ScoringCriteria, Integer> {
    List<ScoringCriteria> findAllByRound_RoundIdOrderByOrderNumber(Integer roundId);
    List<ScoringCriteria> findAllByEvent_EventIdAndRoundIsNullOrderByOrderNumber(Integer eventId);

    // A template's own criteria items: not tied to any event or round.
    List<ScoringCriteria> findAllByTemplate_TemplateIdAndEventIsNullAndRoundIsNullOrderByOrderNumber(Integer templateId);
}
