package com.seal.hackathon.repository;

import com.seal.hackathon.entity.Track;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TrackRepository extends JpaRepository<Track, Integer> {
    List<Track> findAllByEvent_EventId(Integer eventId);

    boolean existsByEvent_EventIdAndNameIgnoreCase(Integer eventId, String name);

    boolean existsByEvent_EventIdAndNameIgnoreCaseAndTrackIdNot(Integer eventId, String name, Integer trackId);

    @Query("""
            SELECT COUNT(t) > 0
            FROM Track t
            WHERE t.event.eventId = :eventId
              AND UPPER(TRIM(t.name)) = :normalizedName
            """)
    boolean existsByEventIdAndNormalizedName(
            @Param("eventId") Integer eventId,
            @Param("normalizedName") String normalizedName);

    @Query("""
            SELECT COUNT(t) > 0
            FROM Track t
            WHERE t.event.eventId = :eventId
              AND UPPER(TRIM(t.name)) = :normalizedName
              AND t.trackId <> :trackId
            """)
    boolean existsByEventIdAndNormalizedNameAndTrackIdNot(
            @Param("eventId") Integer eventId,
            @Param("normalizedName") String normalizedName,
            @Param("trackId") Integer trackId);
}
