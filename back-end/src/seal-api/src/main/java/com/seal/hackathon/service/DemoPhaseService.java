package com.seal.hackathon.service;

import com.seal.hackathon.config.seed.DemoScenario;
import com.seal.hackathon.config.seed.DemoScenario.Phase;
import com.seal.hackathon.entity.AuditLog;
import com.seal.hackathon.entity.User;
import com.seal.hackathon.exception.ResourceNotFoundException;
import com.seal.hackathon.repository.AuditLogRepository;
import com.seal.hackathon.repository.HackathonEventRepository;
import com.seal.hackathon.repository.RoleRepository;
import com.seal.hackathon.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DemoPhaseService {

    private final DemoScenario demoScenario;
    private final HackathonEventRepository eventRepository;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;
    private final DemoContestProblems contestProblems;

    @Transactional
    public int openPhase(Phase phase, int actorId, boolean fresh) {
        // Serialize snapshot creation across requests and app instances.
        roleRepository.lockByRoleName("EVENT_COORDINATOR")
                .orElseThrow(() -> new IllegalStateException("Coordinator role is missing."));
        int eventId = fresh ? seedPhase(phase, actorId)
                : eventRepository.findDemoEventIds(phase.name(), phase.status(), actorId, PageRequest.of(0, 1))
                        .stream().findFirst().orElseGet(() -> seedPhase(phase, actorId));
        var event = eventRepository.getReferenceById(eventId);
        if (event.getName().startsWith("SEAL Summer Demo — ")) {
            event.setName(DemoScenario.DEMO_PHASE_EVENT_NAME);
        }
        if (phase == Phase.CONTEST) {
            contestProblems.seedMissing(eventId, actorId);
        }
        return eventId;
    }

    private int seedPhase(Phase phase, int actorId) {
        User coordinator = userRepository.findById(actorId)
                .orElseThrow(() -> new ResourceNotFoundException("Coordinator does not exist."));
        int eventId = demoScenario.seedPhase(phase, coordinator);
        auditLogRepository.save(AuditLog.builder()
                .actor(coordinator).action(HackathonEventRepository.DEMO_PHASE_ACTION)
                .targetType("EVENT").targetId(eventId).reason(phase.name()).build());
        return eventId;
    }
}
