package com.fcaj.hackathon.config;

import com.fcaj.hackathon.entity.Role;
import com.fcaj.hackathon.entity.ScoringCriteria;
import com.fcaj.hackathon.entity.ScoringCriteriaTemplate;
import com.fcaj.hackathon.repository.RoleRepository;
import com.fcaj.hackathon.repository.ScoringCriteriaRepository;
import com.fcaj.hackathon.repository.ScoringCriteriaTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Seeds the <b>essential reference data</b> a code-first database needs:
 * the four staff roles and default scoring template. Runs on every
 * boot but is fully idempotent (only inserts what is missing), so it is safe with
 * {@code ddl-auto=update} and after a drop-and-recreate.
 *
 * <p>This replaces the "must-have" rows that used to live in seal_schema.sql /
 * seal_seed.sql. Sample/demo data (events, teams, submissions…) is intentionally NOT
 * seeded here — that belongs in a separate, toggleable demo seeder. Admin account
 * creation is explicitly enabled through {@link BootstrapAdminSeeder}.
 */
@Component
@Order(1) // roles must exist before optional bootstrap and demo runners
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final RoleRepository roleRepository;
    private final ScoringCriteriaTemplateRepository templateRepository;
    private final ScoringCriteriaRepository criteriaRepository;

    @Override
    @Transactional
    public void run(String... args) {
        seedRoles();
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
