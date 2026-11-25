package com.seal.hackathon.repository;

import com.seal.hackathon.entity.Role;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RoleRepository extends JpaRepository<Role, Integer> {

    Optional<Role> findByRoleName(String roleName);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Role r where r.roleName = :roleName")
    Optional<Role> lockByRoleName(@Param("roleName") String roleName);

    boolean existsByRoleName(String roleName);
}
