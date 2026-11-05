package com.seal.hackathon.repository;

import com.seal.hackathon.entity.Round;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoundRepository extends JpaRepository<Round, Integer> {

    @Query("SELECT r FROM Round r JOIN FETCH r.event WHERE r.roundId = :roundId AND r.event.eventId = :eventId")
    Optional<Round> findByIdAndEventId(Integer roundId, Integer eventId);

    List<Round> findAllByEvent_EventIdOrderByOrderNumber(Integer eventId);

    // The final round of an event (isFinal = true). Used to source prize winners.
    Optional<Round> findFirstByEvent_EventIdAndIsFinalTrue(Integer eventId);

    // Same as above but excluding a given round — used when updating a round to check for
    // *other* final rounds already existing in the event.
    Optional<Round> findFirstByEvent_EventIdAndIsFinalTrueAndRoundIdNot(Integer eventId, Integer roundId);
}
