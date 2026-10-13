package com.seal.hackathon.config;

import com.seal.hackathon.entity.Role;
import com.seal.hackathon.entity.ScoringCriteria;
import com.seal.hackathon.entity.ScoringCriteriaTemplate;
import com.seal.hackathon.entity.User;
import com.seal.hackathon.entity.UserEventRole;
import com.seal.hackathon.repository.RoleRepository;
import com.seal.hackathon.repository.ScoringCriteriaRepository;
import com.seal.hackathon.repository.ScoringCriteriaTemplateRepository;
import com.seal.hackathon.repository.UserEventRoleRepository;
import com.seal.hackathon.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Seeds the <b>essential bootstrap data</b> a code-first database needs to be usable:
 * the four staff roles and one SYSTEM_ADMIN account to log in with. Runs on every
 * boot but is fully idempotent (only inserts what is missing), so it is safe with
 * {@code ddl-auto=update} and after a drop-and-recreate.
 *
 * <p>This replaces the "must-have" rows that used to live in seal_schema.sql /
 * seal_seed.sql. Sample/demo data (events, teams, submissions…) is intentionally NOT
 * seeded here — that belongs in a separate, toggleable demo seeder.
 */
@Component
@Order(1) // essential roles/admin must exist before the optional DemoSeeder (@Order 2)
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    // Bootstrap admin — the chicken-and-egg breaker: SYSTEM_ADMIN is granted, never
    // self-registered, so a fresh DB needs one seeded account to administer from.
    // Public: the demo seeder looks this account up to act as the SystemLog actor.
    public static final String ADMIN_EMAIL = "admin@fpt.edu.vn";
    private static final String ADMIN_PASSWORD = "Test@1234";

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final UserEventRoleRepository userEventRoleRepository;
    private final PasswordEncoder passwordEncoder;
    private final ScoringCriteriaTemplateRepository templateRepository;
    private final ScoringCriteriaRepository criteriaRepository;

    @Override
    @Transactional
    public void run(String... args) {
        seedRoles();
        seedBootstrapAdmin();
        seedDefaultCriteriaTemplate();
    }

    private void seedRoles() {
        List<RoleDef> roles = List.of(
                new RoleDef("SYSTEM_ADMIN", "Platform operator — accounts, role grants, templates, system logs"),
                new RoleDef("EVENT_COORDINATOR", "SE Dept / PDP staff — runs a hackathon event"),
                new RoleDef("MENTOR", "Supports teams within an assigned track"),
                new RoleDef("JUDGE", "Scores submissions for assigned rounds/tracks"));

        for (RoleDef def : roles) {
            if (!roleRepository.existsByRoleName(def.name())) {
                roleRepository.save(Role.builder()
                        .roleName(def.name())
                        .description(def.description())
                        .build());
                log.info("[seed] created role {}", def.name());
            }
        }
    }

    private void seedBootstrapAdmin() {
        if (userRepository.existsByEmail(ADMIN_EMAIL)) {
            return;
        }
        Role adminRole = roleRepository.findByRoleName("SYSTEM_ADMIN")
                .orElseThrow(() -> new IllegalStateException("SYSTEM_ADMIN role must be seeded before the admin account"));

        User admin = userRepository.save(User.builder()
                .email(ADMIN_EMAIL)
                .passwordHash(passwordEncoder.encode(ADMIN_PASSWORD))
                .fullName("SEAL System Admin")
                .userType("STAFF")
                .provider("LOCAL")
                .isApproved(true)
                .isActive(true)
                .build());

        // System-wide role → eventId is null.
        userEventRoleRepository.save(UserEventRole.builder()
                .user(admin)
                .role(adminRole)
                .eventId(null)
                .build());

        log.info("[seed] created bootstrap SYSTEM_ADMIN account: {}", ADMIN_EMAIL);
    }

    // A ready-to-apply rubric so coordinators aren't stuck with an empty
    // TEMPLATE dropdown on the Criteria tab. Idempotent: skipped once any
    // default template already exists (isDefault=true).
    private void seedDefaultCriteriaTemplate() {
        if (templateRepository.findFirstByIsDefaultTrue().isPresent()) {
            return;
        }

        ScoringCriteriaTemplate template = templateRepository.save(ScoringCriteriaTemplate.builder()
                .name("Standard Rubric")
                .description("Default 5-criteria rubric: idea, technical, UI/UX, completeness, presentation.")
                .isDefault(true)
                .build());

        List<CriteriaDef> criteria = List.of(
                new CriteriaDef("Idea", "Originality and creativity of the solution.", "1.0", 1),
                new CriteriaDef("Technical", "Code quality, architecture, and technical execution.", "1.5", 2),
                new CriteriaDef("UI/UX", "Usability and visual design of the interface.", "1.0", 3),
                new CriteriaDef("Completeness", "How fully the solution is implemented and working end-to-end.", "1.0", 4),
                new CriteriaDef("Presentation", "Clarity and quality of the pitch/demo.", "0.5", 5));

        for (CriteriaDef def : criteria) {
            criteriaRepository.save(ScoringCriteria.builder()
                    .template(template) // template-only item: no event / round
                    .name(def.name())
                    .description(def.description())
                    .weight(new BigDecimal(def.weight()))
                    .maxScore(BigDecimal.TEN)
                    .orderNumber(def.orderNumber())
                    .build());
        }

        log.info("[seed] created default criteria template '{}' with {} items", template.getName(), criteria.size());
    }

    private record RoleDef(String name, String description) {
    }

    private record CriteriaDef(String name, String description, String weight, int orderNumber) {
    }
}
