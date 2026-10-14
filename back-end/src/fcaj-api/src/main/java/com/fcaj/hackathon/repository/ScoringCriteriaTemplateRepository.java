package com.fcaj.hackathon.repository;

import com.fcaj.hackathon.entity.ScoringCriteriaTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ScoringCriteriaTemplateRepository extends JpaRepository<ScoringCriteriaTemplate, Integer> {
    Optional<ScoringCriteriaTemplate> findFirstByIsDefaultTrue();
}
