package com.seal.hackathon.repository;

import com.seal.hackathon.entity.ReopenRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReopenRequestRepository extends JpaRepository<ReopenRequest, Integer> {

    // Pending requests across all events — the Admin review queue (newest first).
    List<ReopenRequest> findByStatusOrderByCreatedAtDesc(String status);

    // Guards against duplicate pending requests for the same event.
    boolean existsByEvent_EventIdAndStatus(Integer eventId, String status);

    // Latest request for an event — lets the Coordinator UI show "awaiting admin".
    Optional<ReopenRequest> findFirstByEvent_EventIdOrderByCreatedAtDesc(Integer eventId);
}
