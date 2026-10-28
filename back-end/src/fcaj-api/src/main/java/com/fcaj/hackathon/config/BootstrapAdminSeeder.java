package com.fcaj.hackathon.config;

import com.fcaj.hackathon.entity.Role;
import com.fcaj.hackathon.entity.User;
import com.fcaj.hackathon.entity.UserEventRole;
import com.fcaj.hackathon.repository.RoleRepository;
import com.fcaj.hackathon.repository.UserEventRoleRepository;
import com.fcaj.hackathon.repository.UserRepository;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Creates an initial administrator only when explicitly enabled in runtime configuration. */
@Component
@Order(2)
@ConditionalOnProperty(name = "app.bootstrap.admin.enabled", havingValue = "true")
public class BootstrapAdminSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminSeeder.class);
    private static final String ADMIN_ROLE = "SYSTEM_ADMIN";

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final UserEventRoleRepository grantRepository;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String password;

    public BootstrapAdminSeeder(RoleRepository roleRepository, UserRepository userRepository,
                                UserEventRoleRepository grantRepository, PasswordEncoder passwordEncoder,
                                @Value("${app.bootstrap.admin.email:}") String email,
                                @Value("${app.bootstrap.admin.password:}") String password) {
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.grantRepository = grantRepository;
        this.passwordEncoder = passwordEncoder;
        this.email = validateEmail(email);
        if (password == null || password.isBlank() || password.length() < 8
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalStateException("BOOTSTRAP_ADMIN_PASSWORD must contain at least 8 characters "
                    + "and at most 72 UTF-8 bytes when bootstrap is enabled");
        }
        this.password = password;
    }

    @Override
    @Transactional
    public void run(String... args) {
        var existing = userRepository.findByEmail(email);
        if (existing.isPresent()) {
            if (!grantRepository.existsByUser_UserIdAndRole_RoleNameAndEventIdIsNull(
                    existing.get().getUserId(), ADMIN_ROLE)) {
                throw new IllegalStateException("Bootstrap email belongs to an existing non-admin account; "
                        + "bootstrap will not change its password or grant privileges");
            }
            log.info("[seed] bootstrap administrator already exists; account and grants unchanged");
            return;
        }

        Role adminRole = roleRepository.findByRoleName(ADMIN_ROLE)
                .orElseThrow(() -> new IllegalStateException("SYSTEM_ADMIN role must exist before bootstrap"));
        User admin = userRepository.save(User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(password))
                .fullName("FCAJ System Admin")
                .userType("STAFF")
                .provider("LOCAL")
                .isApproved(true)
                .isActive(true)
                .build());
        grantRepository.save(UserEventRole.builder().user(admin).role(adminRole).eventId(null).build());
        log.info("[seed] created configured bootstrap administrator");
    }

    private static String validateEmail(String configured) {
        String email = configured == null ? "" : configured.trim().toLowerCase(Locale.ROOT);
        try {
            InternetAddress address = new InternetAddress(email, true);
            address.validate();
            if (email.length() > 255 || !email.equals(address.getAddress())) {
                throw new AddressException();
            }
        } catch (AddressException exception) {
            // Do not attach a cause: mail validation exceptions can contain the configured value.
            throw new IllegalStateException("BOOTSTRAP_ADMIN_EMAIL must be a valid email address "
                    + "when bootstrap is enabled");
        }
        return email;
    }
}
