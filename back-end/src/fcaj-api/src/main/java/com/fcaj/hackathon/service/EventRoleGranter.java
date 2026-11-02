package com.fcaj.hackathon.service;

import com.fcaj.hackathon.entity.Role;
import com.fcaj.hackathon.entity.User;
import com.fcaj.hackathon.entity.UserEventRole;
import com.fcaj.hackathon.exception.ResourceNotFoundException;
import com.fcaj.hackathon.repository.RoleRepository;
import com.fcaj.hackathon.repository.UserEventRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EventRoleGranter {

    private final RoleRepository roleRepository;
    private final UserEventRoleRepository userEventRoleRepository;

    /**
     * Grants {@code roleName} scoped to {@code eventId} if the user does not
     * already hold it, so a work assignment also confers the matching access role.
     */
    public void ensureRole(User user, String roleName, Integer eventId) {
        boolean has = userEventRoleRepository
                .existsByUser_UserIdAndRole_RoleNameAndEventId(user.getUserId(), roleName, eventId);
        if (has) {
            return;
        }
        Role role = roleRepository.findByRoleName(roleName)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + roleName));
        userEventRoleRepository.save(UserEventRole.builder()
                .user(user)
                .role(role)
                .eventId(eventId)
                .build());
    }
}
