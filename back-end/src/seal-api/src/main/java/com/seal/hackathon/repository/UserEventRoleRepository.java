package com.seal.hackathon.repository;

import com.seal.hackathon.entity.UserEventRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserEventRoleRepository extends JpaRepository<UserEventRole, Integer> {

    List<UserEventRole> findAllByUser_UserId(Integer userId);

    // Staff (JUDGE / MENTOR) holding a role for a specific event — used to address announcements.
    List<UserEventRole> findByRole_RoleNameAndEventId(String roleName, Integer eventId);

    // Check if a user already has a specific role (globally, i.e. event_id is null)
    boolean existsByUser_UserIdAndRole_RoleNameAndEventIdIsNull(Integer userId, String roleName);

    // Check if a user already has a specific role for a specific event
    boolean existsByUser_UserIdAndRole_RoleNameAndEventId(Integer userId, String roleName, Integer eventId);
}
