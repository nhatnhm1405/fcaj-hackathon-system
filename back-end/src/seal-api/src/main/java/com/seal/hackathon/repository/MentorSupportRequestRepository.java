package com.seal.hackathon.repository;

import com.seal.hackathon.entity.MentorSupportRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MentorSupportRequestRepository extends JpaRepository<MentorSupportRequest, Integer> {

    boolean existsByTrack_TrackId(Integer trackId);

    /** The team's current OPEN request, if any (enforces one-open-at-a-time). */
    Optional<MentorSupportRequest> findFirstByTeam_TeamIdAndStatus(Integer teamId, String status);

    /** A team's requests, newest first (participant history). */
    List<MentorSupportRequest> findByTeam_TeamIdOrderByCreatedAtDesc(Integer teamId);

    /** All requests across a set of tracks (the mentor's assigned tracks), newest first. */
    @Query("SELECT r FROM MentorSupportRequest r " +
           "JOIN FETCH r.team t " +
           "JOIN FETCH r.track tr " +
           "JOIN FETCH r.requester u " +
           "WHERE r.track.trackId IN :trackIds " +
           "ORDER BY r.createdAt DESC")
    List<MentorSupportRequest> findByTrackIds(@Param("trackIds") List<Integer> trackIds);
}
