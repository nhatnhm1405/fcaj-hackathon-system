package com.seal.hackathon.repository;

import com.seal.hackathon.entity.Announcement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AnnouncementRepository extends JpaRepository<Announcement, Integer> {

    boolean existsByTrack_TrackId(Integer trackId);

    /** Sent history for a given author (mentor or coordinator), newest first. */
    List<Announcement> findBySender_UserIdOrderByCreatedAtDesc(Integer senderUserId);
}
