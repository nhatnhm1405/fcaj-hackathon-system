package com.fcaj.hackathon.config;

import com.fcaj.hackathon.entity.Role;
import com.fcaj.hackathon.entity.User;
import com.fcaj.hackathon.entity.UserEventRole;
import com.fcaj.hackathon.repository.RoleRepository;
import com.fcaj.hackathon.repository.UserEventRoleRepository;
import com.fcaj.hackathon.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BootstrapAdminSeederTest {
    @Mock private RoleRepository roles;
    @Mock private UserRepository users;
    @Mock private UserEventRoleRepository grants;
    @Mock private PasswordEncoder encoder;

    @Test
    void bootstrapIsAbsentByDefaultEvenWithoutCredentialsOrRepositories() {
        new ApplicationContextRunner().withUserConfiguration(BootstrapAdminSeeder.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(BootstrapAdminSeeder.class);
                });
    }

    @Test
    void createsConfiguredLocalAdminAndGlobalGrantOnlyOnceAcrossRestarts() {
        User saved = User.builder().userId(7).email("operator@example.com").passwordHash("encoded-value").build();
        Role role = Role.builder().roleName("SYSTEM_ADMIN").build();
        when(users.findByEmail("operator@example.com")).thenReturn(Optional.empty(), Optional.of(saved));
        when(roles.findByRoleName("SYSTEM_ADMIN")).thenReturn(Optional.of(role));
        when(encoder.encode("external-password")).thenReturn("encoded-value");
        when(users.save(any(User.class))).thenReturn(saved);
        when(grants.existsByUser_UserIdAndRole_RoleNameAndEventIdIsNull(7, "SYSTEM_ADMIN")).thenReturn(true);
        BootstrapAdminSeeder seeder = seeder(" Operator@Example.com ", "external-password");

        seeder.run();
        seeder.run();

        ArgumentCaptor<User> created = ArgumentCaptor.forClass(User.class);
        verify(users, times(1)).save(created.capture());
        assertEquals("operator@example.com", created.getValue().getEmail());
        assertEquals("encoded-value", created.getValue().getPasswordHash());
        assertEquals("LOCAL", created.getValue().getProvider());
        assertEquals("STAFF", created.getValue().getUserType());
        assertTrue(created.getValue().getIsApproved());
        assertTrue(created.getValue().getIsActive());
        ArgumentCaptor<UserEventRole> granted = ArgumentCaptor.forClass(UserEventRole.class);
        verify(grants, times(1)).save(granted.capture());
        assertSame(saved, granted.getValue().getUser());
        assertSame(role, granted.getValue().getRole());
        assertNull(granted.getValue().getEventId());
        verify(encoder, times(1)).encode("external-password");
    }

    @Test
    void existingAdminIsNotReactivatedOrGivenANewPassword() {
        User existing = User.builder().userId(7).email("operator@example.com")
                .passwordHash("existing-hash").isApproved(false).isActive(false).build();
        when(users.findByEmail("operator@example.com")).thenReturn(Optional.of(existing));
        when(grants.existsByUser_UserIdAndRole_RoleNameAndEventIdIsNull(7, "SYSTEM_ADMIN")).thenReturn(true);

        seeder("operator@example.com", "different-password").run();

        assertEquals("existing-hash", existing.getPasswordHash());
        assertFalse(existing.getIsApproved());
        assertFalse(existing.getIsActive());
        verify(users, never()).save(any());
        verify(grants, never()).save(any());
        verifyNoInteractions(encoder, roles);
    }

    @Test
    void existingNonAdminCannotBePromotedByBootstrapConfiguration() {
        when(users.findByEmail("operator@example.com"))
                .thenReturn(Optional.of(User.builder().userId(7).build()));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> seeder("operator@example.com", "external-password").run());

        assertTrue(error.getMessage().contains("existing non-admin"));
        verify(users, never()).save(any());
        verify(grants, never()).save(any());
        verifyNoInteractions(encoder, roles);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "not-an-email", "Name <operator@example.com>", "a@example.com,b@example.com"})
    void invalidEmailFailsBeforeRepositoryAccess(String email) {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> seeder(email, "external-password"));
        assertTrue(error.getMessage().startsWith("BOOTSTRAP_ADMIN_EMAIL"));
        assertNull(error.getCause());
        verifyNoInteractions(users, grants, roles, encoder);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"        ", "short"})
    void missingOrShortPasswordFailsBeforeRepositoryAccess(String password) {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> seeder("operator@example.com", password));
        assertTrue(error.getMessage().startsWith("BOOTSTRAP_ADMIN_PASSWORD"));
        assertNull(error.getCause());
        verifyNoInteractions(users, grants, roles, encoder);
    }

    @Test
    void passwordLimitUsesUtf8BytesRequiredByBcrypt() {
        assertThrows(IllegalStateException.class, () -> seeder("operator@example.com", "á".repeat(37)));
        assertDoesNotThrow(() -> seeder("operator@example.com", "á".repeat(36)));
    }

    private BootstrapAdminSeeder seeder(String email, String password) {
        return new BootstrapAdminSeeder(roles, users, grants, encoder, email, password);
    }
}
