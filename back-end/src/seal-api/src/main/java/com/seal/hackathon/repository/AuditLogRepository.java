package com.seal.hackathon.repository;

import com.seal.hackathon.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Integer> {

    /**
     * Newest-first audit entries with the actor fetched eagerly so a review UI can
     * show the actor's name without an extra query per row.
     */
    @Query("SELECT al FROM AuditLog al JOIN FETCH al.actor ORDER BY al.createdAt DESC")
    List<AuditLog> findAllWithActor();

    /**
     * Newest-first audit entries for a single target (e.g. all actions on EVENT #2).
     * Note: AuditLog is scoped by target_type/target_id, not by event_id — fetching
     * "everything for an event" beyond direct EVENT-target rows is a read-side
     * design decision (see the audit read endpoint, when added).
     */
    @Query("SELECT al FROM AuditLog al JOIN FETCH al.actor "
            + "WHERE al.targetType = :targetType AND al.targetId = :targetId "
            + "ORDER BY al.createdAt DESC")
    List<AuditLog> findByTargetWithActor(@Param("targetType") String targetType,
                                         @Param("targetId") Integer targetId);
}
