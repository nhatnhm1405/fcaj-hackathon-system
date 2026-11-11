package com.seal.hackathon.repository;

import com.seal.hackathon.entity.TeamEventEntry;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TeamEventEntryRepository extends JpaRepository<TeamEventEntry, Integer> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT te FROM TeamEventEntry te WHERE te.id = :id")
    Optional<TeamEventEntry> findByIdForUpdate(@Param("id") Integer id);

    Optional<TeamEventEntry> findByTeam_TeamIdAndEvent_EventId(Integer teamId, Integer eventId);

    boolean existsByTeam_TeamIdAndEvent_EventId(Integer teamId, Integer eventId);

    // "Current entry" resolver: a team is only ever mid-review under one live
    // season at a time in practice (rejoin, not yet built, would be the only
    // way to create a second entry) — the most recently created entry is
    // always the relevant one for teamId-only coordinator/leader actions.
    Optional<TeamEventEntry> findTopByTeam_TeamIdOrderByIdDesc(Integer teamId);

    List<TeamEventEntry> findAllByTeam_TeamId(Integer teamId);

    @Query("""
            SELECT COUNT(te) > 0
            FROM TeamEventEntry te
            WHERE te.event.eventId = :eventId
              AND UPPER(TRIM(te.team.name)) = :normalizedName
            """)
    boolean existsByEventIdAndNormalizedName(
            @Param("eventId") Integer eventId,
            @Param("normalizedName") String normalizedName);

    List<TeamEventEntry> findAllByEvent_EventId(Integer eventId);
    List<TeamEventEntry> findAllByEvent_EventIdAndStatus(Integer eventId, String status);
    List<TeamEventEntry> findAllByStatus(String status);
    long countByEvent_EventIdAndStatus(Integer eventId, String status);
    long countByStatus(String status);
    List<TeamEventEntry> findAllByTrack_TrackIdAndStatus(Integer trackId, String status);
    List<TeamEventEntry> findAllByTrack_TrackId(Integer trackId);
    boolean existsByTrack_TrackId(Integer trackId);
}
