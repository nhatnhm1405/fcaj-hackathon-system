package com.fcaj.hackathon.config.seed;

import com.fcaj.hackathon.entity.User;
import com.fcaj.hackathon.repository.UserEventRoleRepository;
import com.fcaj.hackathon.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DemoFixturesAdminTest {
    @Mock private UserRepository userRepo;
    @Mock private UserEventRoleRepository grantRepo;
    @InjectMocks private DemoFixtures fixtures;

    @Test
    void usesConfiguredAdminInsteadOfFixedLegacyEmail() {
        ReflectionTestUtils.setField(fixtures, "bootstrapAdminEmail", " Operator@Example.com ");
        User admin = User.builder().userId(9).email("operator@example.com").build();
        when(userRepo.findByEmail("operator@example.com")).thenReturn(Optional.of(admin));
        when(grantRepo.existsByUser_UserIdAndRole_RoleNameAndEventIdIsNull(9, "SYSTEM_ADMIN")).thenReturn(true);

        assertSame(admin, fixtures.adminActor());
        verify(userRepo, never()).findByEmail("admin@fpt.edu.vn");
    }

    @Test
    void missingConfigurationDoesNotFallBackToLegacyAdmin() {
        assertThrows(IllegalStateException.class, fixtures::adminActor);
        verifyNoInteractions(userRepo, grantRepo);
    }

    @Test
    void configuredAccountMustAlreadyHaveGlobalAdminRole() {
        ReflectionTestUtils.setField(fixtures, "bootstrapAdminEmail", "operator@example.com");
        when(userRepo.findByEmail("operator@example.com"))
                .thenReturn(Optional.of(User.builder().userId(9).build()));
        assertThrows(IllegalStateException.class, fixtures::adminActor);
        verify(grantRepo, never()).save(any());
    }
}
