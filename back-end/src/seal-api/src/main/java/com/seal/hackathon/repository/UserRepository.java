package com.seal.hackathon.repository;

import com.seal.hackathon.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Integer> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByStudentId(String studentId);

    // Find a user who previously logged in via OAuth2 with this provider + provider
    // ID
    Optional<User> findByProviderAndProviderId(String provider, String providerId);

    // Users awaiting coordinator approval
    @Query("SELECT DISTINCT u FROM User u LEFT JOIN FETCH u.userEventRoles uer LEFT JOIN FETCH uer.role WHERE u.isApproved = false")
    List<User> findAllByIsApprovedFalse();

    // Fetch user together with all their roles to avoid N+1 in auth paths
    @Query("SELECT DISTINCT u FROM User u LEFT JOIN FETCH u.userEventRoles uer LEFT JOIN FETCH uer.role WHERE u.email = :email")
    Optional<User> findByEmailWithRoles(String email);

    @Query("SELECT DISTINCT u FROM User u LEFT JOIN FETCH u.userEventRoles uer LEFT JOIN FETCH uer.role WHERE u.userId = :userId")
    Optional<User> findByIdWithRoles(Integer userId);

    /**
     * Search approved writable student accounts a participant can invite, by email,
     * student id or full name (case-insensitive substring).
     */
    @Query("SELECT u FROM User u WHERE u.isApproved = true " +
           "AND u.isActive = true " +
           "AND u.userType IN ('FPT_STUDENT', 'EXTERNAL_STUDENT') " +
           "AND (LOWER(u.email) LIKE CONCAT('%', :q, '%') " +
           "  OR LOWER(u.studentId) LIKE CONCAT('%', :q, '%') " +
           "  OR LOWER(u.fullName) LIKE CONCAT('%', :q, '%'))")
    List<User> searchInvitableStudents(@Param("q") String q);

    /** All approved & active student accounts — recipients of "Participants" announcements. */
    @Query("SELECT u FROM User u WHERE u.isApproved = true " +
           "AND u.userType IN ('FPT_STUDENT', 'EXTERNAL_STUDENT')")
    List<User> findApprovedStudents();

    /**
     * Approved STAFF a coordinator may assign as judge/mentor — excludes anyone already
     * holding SYSTEM_ADMIN or EVENT_COORDINATOR (both are also userType=STAFF). Cannot
     * instead filter "must already hold JUDGE/MENTOR" because that role is only granted
     * the first time they're assigned (see EventRoleGranter#ensureRole), which would
     * make brand-new staff impossible to assign.
     */
    @Query("SELECT DISTINCT u FROM User u WHERE u.userType = 'STAFF' AND u.isApproved = true " +
           "AND NOT EXISTS (SELECT 1 FROM UserEventRole uer WHERE uer.user = u " +
           "  AND uer.role.roleName IN ('SYSTEM_ADMIN', 'EVENT_COORDINATOR'))")
    List<User> findAssignableStaff();

    /** Active, approved participants — the coordinator's "All Participant" account view. */
    @Query("SELECT DISTINCT u FROM User u LEFT JOIN FETCH u.userEventRoles uer LEFT JOIN FETCH uer.role " +
           "WHERE u.isApproved = true AND u.isActive = true " +
           "AND u.userType IN ('FPT_STUDENT', 'EXTERNAL_STUDENT')")
    List<User> findActiveApprovedParticipants();

    /**
     * Active, approved STAFF eligible to serve as judge/mentor — same exclusion of
     * SYSTEM_ADMIN/EVENT_COORDINATOR as {@link #findAssignableStaff()}, plus
     * isActive=true, for the coordinator's "Judge & Mentor" account view.
     */
    @Query("SELECT DISTINCT u FROM User u LEFT JOIN FETCH u.userEventRoles uer LEFT JOIN FETCH uer.role " +
           "WHERE u.userType = 'STAFF' AND u.isApproved = true AND u.isActive = true " +
           "AND NOT EXISTS (SELECT 1 FROM UserEventRole uer2 WHERE uer2.user = u " +
           "  AND uer2.role.roleName IN ('SYSTEM_ADMIN', 'EVENT_COORDINATOR'))")
    List<User> findActiveAssignableStaff();

    /**
     * Active, approved students who are NOT a member of any team in the given event —
     * i.e. registrants approved for the current season who never joined a squad. In the
     * one-active-event model these are that event's teamless free agents, eligible for
     * SETUP leftover grouping.
     */
    @Query("SELECT u FROM User u WHERE u.isApproved = true " +
           "AND u.isActive = true " +
           "AND u.userType IN ('FPT_STUDENT', 'EXTERNAL_STUDENT') " +
           "AND u.userId NOT IN (" +
           "  SELECT tm.user.userId FROM TeamMember tm " +
           "  JOIN TeamEventEntry te ON te.team = tm.team " +
           "  WHERE te.event.eventId = :eventId)")
    List<User> findGroupableFreeAgents(@Param("eventId") Integer eventId);
}
