package com.seal.hackathon.repository;

import com.seal.hackathon.entity.HackathonEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

public interface HackathonEventRepository extends JpaRepository<HackathonEvent, Integer> {

    List<HackathonEvent> findAllByStatus(String status);

    boolean existsByYearAndSeasonIgnoreCaseAndStatusNotIgnoreCase(
            Integer year,
            String season,
            String status
    );

    boolean existsByYearAndSeasonIgnoreCaseAndStatusNotIgnoreCaseAndEventIdNot(
            Integer year,
            String season,
            String status,
            Integer eventId
    );

    @Query("""
            select count(e) > 0
            from HackathonEvent e
            where (:excludeEventId is null or e.eventId <> :excludeEventId)
              and upper(e.status) <> upper(:excludedStatus)
              and e.startDate <= :endDate
              and e.endDate >= :startDate
            """)
    boolean existsOverlappingActiveEvent(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("excludeEventId") Integer excludeEventId,
            @Param("excludedStatus") String excludedStatus
    );
}
