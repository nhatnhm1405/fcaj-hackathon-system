package com.seal.hackathon.repository;

import com.seal.hackathon.entity.HackathonEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface HackathonEventRepository extends JpaRepository<HackathonEvent, Integer> {

    // The seed audit entry is the durable marker for a demo snapshot.
    String DEMO_PHASE_ACTION = "SEED_DEMO_PHASE";
    String DEMO_EVENT = "exists (select 1 from AuditLog a where a.action = '" + DEMO_PHASE_ACTION
            + "' and a.targetType = 'EVENT' and a.targetId = e.eventId";

    @Query("select e from HackathonEvent e where not " + DEMO_EVENT + ")")
    List<HackathonEvent> findAllRegularEvents(Sort sort);

    @Query("select e from HackathonEvent e where e.status = :status and not " + DEMO_EVENT + ")")
    List<HackathonEvent> findAllByStatus(@Param("status") String status);

    @Query("select e.eventId from HackathonEvent e where e.status = :status and " + DEMO_EVENT
            + " and a.reason = :phase and a.actor.userId = :actorId) order by e.eventId desc")
    List<Integer> findDemoEventIds(@Param("phase") String phase, @Param("status") String status,
                                   @Param("actorId") int actorId, Pageable page);

    @Query("""
            select count(e) > 0 from HackathonEvent e
            where e.year = :year and upper(e.season) = upper(:season)
              and upper(e.status) <> upper(:status) and not
            """ + DEMO_EVENT + ")")
    boolean existsByYearAndSeasonIgnoreCaseAndStatusNotIgnoreCase(
            @Param("year") Integer year,
            @Param("season") String season,
            @Param("status") String status
    );

    @Query("""
            select count(e) > 0 from HackathonEvent e
            where e.year = :year and upper(e.season) = upper(:season)
              and upper(e.status) <> upper(:status) and e.eventId <> :eventId and not
            """ + DEMO_EVENT + ")")
    boolean existsByYearAndSeasonIgnoreCaseAndStatusNotIgnoreCaseAndEventIdNot(
            @Param("year") Integer year,
            @Param("season") String season,
            @Param("status") String status,
            @Param("eventId") Integer eventId
    );

    @Query("""
            select count(e) > 0
            from HackathonEvent e
            where (:excludeEventId is null or e.eventId <> :excludeEventId)
              and upper(e.status) <> upper(:excludedStatus)
              and e.startDate <= :endDate
              and e.endDate >= :startDate
              and not
            """ + DEMO_EVENT + ")")
    boolean existsOverlappingActiveEvent(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("excludeEventId") Integer excludeEventId,
            @Param("excludedStatus") String excludedStatus
    );
}
