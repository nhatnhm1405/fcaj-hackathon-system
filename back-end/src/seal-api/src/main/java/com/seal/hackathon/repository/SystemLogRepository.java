package com.seal.hackathon.repository;

import com.seal.hackathon.entity.SystemLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SystemLogRepository extends JpaRepository<SystemLog, Integer> {

    /**
     * Newest-first system logs with the actor fetched eagerly so the admin UI
     * can show the actor's name without an extra query per row.
     */
    @Query("SELECT sl FROM SystemLog sl JOIN FETCH sl.actor ORDER BY sl.createdAt DESC")
    List<SystemLog> findAllWithActor();
}
