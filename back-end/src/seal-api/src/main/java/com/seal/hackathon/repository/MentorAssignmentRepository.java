package com.seal.hackathon.repository;

import com.seal.hackathon.entity.MentorAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MentorAssignmentRepository extends JpaRepository<MentorAssignment, Integer> {

    /**
     * Active mentor assignments of one mentor, with Track and Event
     * fetched eagerly to avoid N+1 queries.
     */
    @Query("SELECT ma FROM MentorAssignment ma " +
           "JOIN FETCH ma.track t " +
           "JOIN FETCH t.event e " +
           "WHERE ma.mentor.userId = :mentorUserId " +
           "AND ma.isActive = true " +
           "ORDER BY ma.id DESC")
    List<MentorAssignment> findActiveByMentor(@Param("mentorUserId") Integer mentorUserId);

    // Duplicate check for the unique key (mentor_user_id, track_id)
    boolean existsByMentor_UserIdAndTrack_TrackId(Integer mentorUserId, Integer trackId);

    // Business rule: a mentor manages at most ONE track per event. Checks whether the
    // mentor already has an active assignment to any track in the given event.
    boolean existsByMentor_UserIdAndTrack_Event_EventIdAndIsActiveTrue(Integer mentorUserId, Integer eventId);

    List<MentorAssignment> findAllByTrack_TrackIdAndIsActiveTrue(Integer trackId);

    /** Remove both active and inactive configuration rows before deleting a track. */
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM MentorAssignment ma WHERE ma.track.trackId = :trackId")
    int deleteAllByTrackId(@Param("trackId") Integer trackId);

    /** Coordinator roster: all active mentor assignments in an event. */
    @Query("SELECT ma FROM MentorAssignment ma " +
           "JOIN FETCH ma.track t " +
           "JOIN FETCH ma.mentor m " +
           "WHERE t.event.eventId = :eventId " +
           "AND ma.isActive = true " +
           "ORDER BY ma.id DESC")
    List<MentorAssignment> findActiveByEvent(@Param("eventId") Integer eventId);
}
