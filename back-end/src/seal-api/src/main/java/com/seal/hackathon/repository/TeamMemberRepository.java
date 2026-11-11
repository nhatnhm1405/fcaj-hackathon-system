package com.seal.hackathon.repository;

import com.seal.hackathon.entity.TeamMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TeamMemberRepository extends JpaRepository<TeamMember, Integer> {

    @Query("""
            SELECT COUNT(tm) > 0
            FROM TeamMember tm
            JOIN TeamEventEntry te ON te.team = tm.team
            WHERE tm.user.userId = :userId
              AND te.event.eventId = :eventId
            """)
    boolean existsByUser_UserIdAndTeam_Event_EventId(@Param("userId") Integer userId, @Param("eventId") Integer eventId);

    boolean existsByUser_UserIdAndTeam_TeamId(Integer userId, Integer teamId);

    /** Find all teams that a user belongs to, filtered by event status (e.g. OPEN, IN_PROGRESS) */
    @Query("""
            SELECT tm
            FROM TeamMember tm
            JOIN TeamEventEntry te ON te.team = tm.team
            WHERE tm.user.userId = :userId
              AND te.event.status IN :statuses
            """)
    List<TeamMember> findByUser_UserIdAndTeam_Event_StatusIn(
            @Param("userId") Integer userId, @Param("statuses") List<String> statuses);

    /** Find all teams that a user belongs to, newest membership first */
    List<TeamMember> findByUser_UserIdOrderByIdDesc(Integer userId);

    /** Find all members of a specific team */
    List<TeamMember> findByTeam_TeamId(Integer teamId);

    long countByTeam_TeamId(Integer teamId);

    /** True if the user belongs to a team with the given status that is assigned to
     *  the given track. Used to gate "đề thi" downloads to members of an APPROVED
     *  team in the track. */
    @Query("""
            SELECT COUNT(tm) > 0
            FROM TeamMember tm
            JOIN TeamEventEntry te ON te.team = tm.team
            WHERE tm.user.userId = :userId
              AND te.track.trackId = :trackId
              AND UPPER(te.status) = UPPER(:status)
            """)
    boolean existsByUser_UserIdAndTeam_Track_TrackIdAndTeam_StatusIgnoreCase(
            @Param("userId") Integer userId, @Param("trackId") Integer trackId, @Param("status") String status);

    /** Current memberships (with team) for a batch of users, restricted to one event's
     *  teams in the given status. Used to resolve manual-leftover-assign eligibility. */
    @Query("""
            SELECT tm
            FROM TeamMember tm
            JOIN TeamEventEntry te ON te.team = tm.team
            WHERE te.event.eventId = :eventId
              AND te.status = :status
              AND tm.user.userId IN :userIds
            """)
    List<TeamMember> findByTeam_Event_EventIdAndTeam_StatusAndUser_UserIdIn(
            @Param("eventId") Integer eventId, @Param("status") String status, @Param("userIds") List<Integer> userIds);
}
