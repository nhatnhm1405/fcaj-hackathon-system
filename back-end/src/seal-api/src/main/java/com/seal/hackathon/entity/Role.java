package com.seal.hackathon.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Maps to the `Role` table.
 * Seed data (staff only): EVENT_COORDINATOR, MENTOR, JUDGE
 * Participants are identified by User.user_type, not this table.
 */
@Entity
@Table(name = "Role")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "role_id")
    private Integer roleId;

    @Column(name = "role_name", nullable = false, unique = true, length = 50)
    private String roleName;

    @Column(name = "description", length = 255)
    private String description;
}
