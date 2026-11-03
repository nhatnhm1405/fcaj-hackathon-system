package com.seal.hackathon.service;

import com.seal.hackathon.dto.request.ApplyLeftoverGroupingRequest;
import com.seal.hackathon.dto.request.ManualAssignLeftoverRequest;
import com.seal.hackathon.dto.response.GroupingCommitResponse;
import com.seal.hackathon.dto.response.GroupingPreviewResponse;
import com.seal.hackathon.dto.response.GroupingPreviewResponse.MemberView;
import com.seal.hackathon.dto.response.GroupingPreviewResponse.ProposedTeamView;
import com.seal.hackathon.dto.response.GroupingPreviewResponse.WarningView;
import com.seal.hackathon.entity.HackathonEvent;
import com.seal.hackathon.entity.JoinRequest;
import com.seal.hackathon.entity.Team;
import com.seal.hackathon.entity.TeamEventEntry;
import com.seal.hackathon.entity.TeamInvite;
import com.seal.hackathon.entity.TeamMember;
import com.seal.hackathon.entity.User;
import com.seal.hackathon.exception.BadRequestException;
import com.seal.hackathon.exception.ResourceNotFoundException;
import com.seal.hackathon.repository.HackathonEventRepository;
import com.seal.hackathon.repository.JoinRequestRepository;
import com.seal.hackathon.repository.TeamEventEntryRepository;
import com.seal.hackathon.repository.TeamInviteRepository;
import com.seal.hackathon.repository.TeamMemberRepository;
import com.seal.hackathon.repository.TeamRepository;
import com.seal.hackathon.repository.UserRepository;
import com.seal.hackathon.service.grouping.Atom;
import com.seal.hackathon.service.grouping.GroupingPlan;
import com.seal.hackathon.service.grouping.GroupingWarning;
import com.seal.hackathon.service.grouping.LeftoverGroupingPlanner;
import com.seal.hackathon.service.grouping.ProposedTeam;
import com.seal.hackathon.service.grouping.ProposedTeam.TeamOrigin;
import com.seal.hackathon.service.grouping.SettledTeam;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Groups an event's leftover people into valid teams during SETUP, just before the
 * track draw. "Leftover people" are the members of under-sized teams plus registrants
 * who never joined a squad:
 * <ul>
 *   <li>a team of 1 → a movable free agent (its one-person team is dissolved on commit);</li>
 *   <li>a team of 2 → kept together and grown in place;</li>
 *   <li>a team of {@value LeftoverGroupingPlanner#DEFAULT_MIN}+ → left alone (a spill target only);</li>
 *   <li>an active, approved student on no team of this event → a teamless free agent
 *       (gains a membership only when placed on commit).</li>
 * </ul>
 *
 * The heavy lifting is the pure {@link LeftoverGroupingPlanner}; this service maps
 * entities to/from it and applies the plan. Writes go straight through the
 * repositories — an intentional SETUP-scoped path that bypasses the OPEN-only guard
 * on normal team creation (the roster is otherwise frozen once SETUP begins).
 */
@Service
@RequiredArgsConstructor
public class LeftoverGroupingService {

    private static final int MIN = LeftoverGroupingPlanner.DEFAULT_MIN;
    private static final int MAX = LeftoverGroupingPlanner.DEFAULT_MAX;
    private static final String GROUPED_NOTIFICATION = "TEAM_GROUPED";

    private final TeamRepository teamRepository;
    private final TeamEventEntryRepository teamEventEntryRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final HackathonEventRepository eventRepository;
    private final JoinRequestRepository joinRequestRepository;
    private final TeamInviteRepository teamInviteRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;
    private final HackathonEventService hackathonEventService;

    private final LeftoverGroupingPlanner planner = new LeftoverGroupingPlanner(MIN, MAX);
    private final Random random = new Random();

    // ── Preview (read-only dry run) ──────────────────────────────────

    @Transactional(readOnly = true)
    public GroupingPreviewResponse preview(Integer eventId) {
        requireSetupEvent(eventId);
        Context ctx = buildContext(eventId);
        GroupingPlan plan = planner.plan(ctx.atoms, ctx.settled);
        return toPreview(plan, ctx);
    }

    // ── Commit (apply the plan) ──────────────────────────────────────

    @Transactional
    public GroupingCommitResponse commit(Integer eventId, Integer actorUserId, String reason) {
        HackathonEvent event = requireSetupEvent(eventId);
        Context ctx = buildContext(eventId);
        GroupingPlan plan = planner.plan(ctx.atoms, ctx.settled);

        int created = 0;
        int grown = 0;
        int placed = 0;
        for (ProposedTeam pt : plan.teams()) {
            if (pt.origin() == TeamOrigin.NEW) {
                placed += applyNewTeam(event, pt, ctx);
                created++;
            } else {
                placed += growExistingTeam(pt, ctx);
                grown++;
            }
        }

        // The roster changed mid-SETUP, so slot counts frozen on SETUP entry are stale.
        hackathonEventService.recomputeSetupTrackCapacities(eventId);

        auditLogService.record(actorUserId, "GROUP_LEFTOVERS", "EVENT", eventId,
                (reason != null && !reason.isBlank()) ? reason.trim() : null,
                Map.of("teams_created", created, "teams_grown", grown,
                        "people_placed", placed, "warnings", plan.warnings().size()));

        return GroupingCommitResponse.builder()
                .teamsCreated(created)
                .teamsGrown(grown)
                .peoplePlaced(placed)
                .unresolvedWarnings(plan.warnings().size())
                .warnings(plan.warnings().stream().map(w -> toWarningView(w, ctx)).toList())
                .build();
    }

    // ── Manual override (force-place / force-approve a leftover team) ─

    /**
     * Coordinator escape hatch for leftover people the automatic planner could not
     * place: places {@code userIds} onto an existing team ({@code targetTeamId} set)
     * or force-creates a brand-new team from them ({@code targetTeamId} null — a
     * single-element list is the intended way to force-approve a lone straggler as
     * their own team). Bypasses the recommended MIN entirely (unlike {@link #applyPlan},
     * this is exactly the point of this endpoint); the hard MAX is always enforced.
     * Only touches people already in this event's leftover pool — a teamless free
     * agent, or a member of an existing APPROVED team below MIN — pulling someone
     * off an already-valid (&gt;= MIN) team is rejected.
     *
     * <p><b>Caveat (documented, not fixed):</b> a manually force-approved team of
     * size 1 or 2 is indistinguishable from an organically-leftover under-sized team
     * to {@link #buildContext} (size 1 folds back in as a free-agent atom; size
     * 2-under-MIN as an existing-team atom). Running {@link #preview}/{@link #commit}
     * again afterwards can sweep that team back into the automatic grouping and
     * dissolve it. Coordinators should do manual force-approvals only after they are
     * done running automatic commit passes for a SETUP session.
     */
    @Transactional
    public GroupingCommitResponse manualAssign(Integer eventId, Integer actorUserId,
                                                ManualAssignLeftoverRequest request) {
        HackathonEvent event = requireSetupEvent(eventId);

        CompositionResult result = applyComposition(event, request.getTargetTeamId(), request.getUserIds());

        // The roster changed mid-SETUP, so slot counts frozen on SETUP entry are stale.
        hackathonEventService.recomputeSetupTrackCapacities(eventId);

        auditLogService.record(actorUserId, "MANUAL_ASSIGN_SETUP_LEFTOVERS", "EVENT", eventId,
                (request.getReason() != null && !request.getReason().isBlank())
                        ? request.getReason().trim() : null,
                Map.<String, Object>of("userIds", request.getUserIds(),
                        "targetTeamId", result.newTeamCreated() ? "NEW" : String.valueOf(request.getTargetTeamId()),
                        "teamId", result.team().getTeamId()));

        return GroupingCommitResponse.builder()
                .teamsCreated(result.newTeamCreated() ? 1 : 0)
                .teamsGrown(result.newTeamCreated() ? 0 : 1)
                .peoplePlaced(result.newlyPlacedCount())
                .unresolvedWarnings(0)
                .warnings(List.of())
                .build();
    }

    // ── Apply an edited preview (coordinator rearranged Proposed Teams) ──

    /**
     * Applies a coordinator-edited version of the leftover-grouping preview: the
     * Proposed Teams cards shown by {@link #preview}, after the coordinator freely
     * dragged people between them. Unlike {@link #manualAssign} (a deliberate,
     * one-off bypass of the recommended MIN for a single straggler), every team this
     * touches must land on either 0 members (dissolved/left empty — simply omit it,
     * or submit it with an empty roster) or at least {@value #MIN}; violating that
     * rejects the whole request and rolls back every placement already applied in
     * this call, so a coordinator's edit is all-or-nothing.
     *
     * <p>Each {@code TeamComposition} carries a team's <em>complete</em> final
     * roster (not just newcomers) — people already exactly on that team are a no-op
     * (their row, and any existing LEADER role, is left untouched); anyone dragged
     * elsewhere is moved there instead, dissolving their now-empty source team.
     * Eligibility is the same leftover-pool rule as {@link #manualAssign}, checked
     * per team as it's applied — a coordinator can only redistribute the people the
     * planner itself considered, never pull someone off an already-valid team.
     */
    @Transactional
    public GroupingCommitResponse applyPlan(Integer eventId, Integer actorUserId,
                                             ApplyLeftoverGroupingRequest request) {
        HackathonEvent event = requireSetupEvent(eventId);

        Set<Integer> seen = new LinkedHashSet<>();
        for (ApplyLeftoverGroupingRequest.TeamComposition c : request.getTeams()) {
            for (Integer userId : c.getMemberUserIds()) {
                if (!seen.add(userId)) {
                    throw new BadRequestException("User " + userId + " appears in more than one team.");
                }
            }
        }

        int created = 0;
        int grown = 0;
        int placed = 0;
        List<Integer> touchedTeamIds = new ArrayList<>();
        for (ApplyLeftoverGroupingRequest.TeamComposition c : request.getTeams()) {
            if (c.getMemberUserIds().isEmpty()) {
                continue; // untouched or fully emptied slot — nothing to place
            }
            CompositionResult result = applyComposition(event, c.getExistingTeamId(), c.getMemberUserIds());
            touchedTeamIds.add(result.team().getTeamId());
            if (result.newTeamCreated()) {
                created++;
            } else {
                grown++;
            }
            placed += result.newlyPlacedCount();
        }

        for (Integer teamId : touchedTeamIds) {
            long size = teamMemberRepository.countByTeam_TeamId(teamId);
            if (size > 0 && size < MIN) {
                throw new BadRequestException("Team '" + teamName(teamId) + "' would end up with only "
                        + size + " member(s) — must be either 0 or at least " + MIN + ".");
            }
            if (size > MAX) {
                throw new BadRequestException("Team '" + teamName(teamId) + "' would end up with "
                        + size + " members, above the maximum of " + MAX + ".");
            }
        }

        hackathonEventService.recomputeSetupTrackCapacities(eventId);

        auditLogService.record(actorUserId, "APPLY_EDITED_LEFTOVER_GROUPING", "EVENT", eventId,
                (request.getReason() != null && !request.getReason().isBlank())
                        ? request.getReason().trim() : null,
                Map.<String, Object>of("teamsCreated", created, "teamsGrown", grown, "peoplePlaced", placed));

        return GroupingCommitResponse.builder()
                .teamsCreated(created)
                .teamsGrown(grown)
                .peoplePlaced(placed)
                .unresolvedWarnings(0)
                .warnings(List.of())
                .build();
    }

    private String teamName(Integer teamId) {
        return teamRepository.findById(teamId).map(Team::getName).orElse("#" + teamId);
    }

    // ── Shared placement core (manualAssign + applyPlan) ─────────────

    /**
     * Validates and applies one team's final roster: every userId in
     * {@code memberUserIds} must currently be a teamless free agent or a member of
     * an under-{@value #MIN} APPROVED team of this event (else rejected, naming the
     * offending user) — then each is placed onto {@code existingTeamId} (or a fresh
     * team when null), notifying everyone affected. A user already exactly on the
     * target team is a no-op.
     */
    private CompositionResult applyComposition(HackathonEvent event, Integer existingTeamId,
                                                List<Integer> memberUserIds) {
        Integer eventId = event.getEventId();

        if (new LinkedHashSet<>(memberUserIds).size() != memberUserIds.size()) {
            throw new BadRequestException("A team's memberUserIds must not contain duplicates.");
        }

        Map<Integer, TeamMember> membershipByUser = teamMemberRepository
                .findByTeam_Event_EventIdAndTeam_StatusAndUser_UserIdIn(eventId, "APPROVED", memberUserIds)
                .stream()
                .collect(Collectors.toMap(tm -> tm.getUser().getUserId(), tm -> tm));
        Map<Integer, User> freeAgentByUser = userRepository.findGroupableFreeAgents(eventId).stream()
                .filter(u -> memberUserIds.contains(u.getUserId()))
                .collect(Collectors.toMap(User::getUserId, u -> u));

        Team targetTeam;
        List<Integer> originalMemberIds;
        boolean newTeamCreated;
        if (existingTeamId != null) {
            Team existing = teamRepository.findById(existingTeamId)
                    .orElseThrow(() -> new ResourceNotFoundException("Team not found: " + existingTeamId));
            TeamEventEntry existingEntry = teamEventEntryRepository
                    .findByTeam_TeamIdAndEvent_EventId(existingTeamId, eventId)
                    .orElseThrow(() -> new ResourceNotFoundException("Team not found: " + existingTeamId));
            if (!"APPROVED".equalsIgnoreCase(existingEntry.getStatus())) {
                throw new BadRequestException("Target team must be an approved team.");
            }
            List<TeamMember> currentMembers = teamMemberRepository.findByTeam_TeamId(existing.getTeamId());
            Set<Integer> currentIds = currentMembers.stream()
                    .map(m -> m.getUser().getUserId()).collect(Collectors.toSet());
            long newcomers = memberUserIds.stream().filter(uid -> !currentIds.contains(uid)).count();
            if (currentMembers.size() + newcomers > MAX) {
                throw new BadRequestException("Placing these user(s) would grow team '"
                        + existing.getName() + "' beyond the maximum size of " + MAX + ".");
            }
            targetTeam = existing;
            originalMemberIds = List.copyOf(currentIds);
            newTeamCreated = false;
        } else {
            targetTeam = teamRepository.save(Team.builder()
                    .name(uniqueAutoName(event))
                    .build());
            teamEventEntryRepository.save(TeamEventEntry.builder()
                    .team(targetTeam)
                    .event(event)
                    .status("APPROVED")
                    .build());
            originalMemberIds = List.of();
            newTeamCreated = true;
        }

        // Eligibility is checked here, per person, AFTER the no-op check below — not
        // up front — because a person merely re-submitted as already exactly on
        // targetTeam has no state actually changing, so the leftover-pool rule
        // doesn't apply to them (e.g. the other real members of a settled team that
        // absorbed a spilled straggler: they must be resubmittable as-is even
        // though they were never "leftover" themselves; only genuinely moving
        // someone off an already-valid team is rejected).
        List<TeamMember> newlyPlaced = new ArrayList<>();
        for (Integer userId : memberUserIds) {
            TeamMember membership = membershipByUser.get(userId);
            if (membership != null && membership.getTeam().getTeamId().equals(targetTeam.getTeamId())) {
                continue; // already exactly here — leave their row (and any LEADER role) untouched
            }
            if (membership != null) {
                long size = teamMemberRepository.countByTeam_TeamId(membership.getTeam().getTeamId());
                if (size >= MIN) {
                    throw new BadRequestException("User " + userId + " belongs to a team that "
                            + "already meets the recommended minimum size and cannot be "
                            + "reassigned by this override.");
                }
            } else if (!freeAgentByUser.containsKey(userId)) {
                throw new BadRequestException("User " + userId + " is not part of this event's "
                        + "leftover pool (not a free agent and not on an under-sized team).");
            }
            String role = (newTeamCreated && newlyPlaced.isEmpty()) ? "LEADER" : "MEMBER";
            newlyPlaced.add(placeUser(targetTeam, eventId, userId, role, membershipByUser, freeAgentByUser));
        }

        if (!newlyPlaced.isEmpty()) {
            notifyManualAssign(targetTeam, originalMemberIds, newlyPlaced, newTeamCreated, event.getName());
        }
        return new CompositionResult(targetTeam, newTeamCreated, newlyPlaced.size());
    }

    private record CompositionResult(Team team, boolean newTeamCreated, int newlyPlacedCount) {
    }

    /**
     * Places one user onto {@code target} with the given role: moves their existing
     * TeamMember row if they're currently on an under-sized team (dissolving that
     * source team if the move empties it — reuses {@link #dissolve}), or creates a
     * fresh row if they were a teamless free agent.
     */
    private TeamMember placeUser(Team target, Integer eventId, Integer userId, String role,
                                  Map<Integer, TeamMember> membershipByUser,
                                  Map<Integer, User> freeAgentByUser) {
        TeamMember membership = membershipByUser.get(userId);
        if (membership != null) {
            Team source = membership.getTeam();
            membership.setTeam(target);
            membership.setMemberRole(role);
            teamMemberRepository.save(membership);
            if (!source.getTeamId().equals(target.getTeamId())
                    && teamMemberRepository.countByTeam_TeamId(source.getTeamId()) == 0) {
                dissolve(source, eventId);
            }
            return membership;
        }
        return teamMemberRepository.save(TeamMember.builder()
                .team(target).user(freeAgentByUser.get(userId)).memberRole(role).build());
    }

    /** Notifies every affected user, matching the wording used by growExistingTeam/applyNewTeam. */
    private void notifyManualAssign(Team target, List<Integer> originalMemberIds, List<TeamMember> placed,
                                     boolean newTeamCreated, String eventName) {
        String teamName = target.getName();

        originalMemberIds.forEach(uid -> notificationService.createNotification(uid,
                "New teammate(s) added",
                "Your team '" + teamName + "' gained new member(s) during setup grouping for "
                        + eventName + ".", GROUPED_NOTIFICATION));

        TeamMember leader = newTeamCreated
                ? placed.stream().filter(m -> "LEADER".equals(m.getMemberRole())).findFirst().orElse(null)
                : null;
        for (TeamMember m : placed) {
            String title = newTeamCreated
                    ? "You were placed into a new team"
                    : "You were placed into team '" + teamName + "'";
            String content = newTeamCreated
                    ? "You've been grouped into '" + teamName + "' for " + eventName
                            + (m == leader ? " as the team leader." : ".") + " Say hi to your teammates!"
                    : "You were placed into '" + teamName + "' for " + eventName
                            + " by the event coordinator.";
            notificationService.createNotification(m.getUser().getUserId(), title, content, GROUPED_NOTIFICATION);
        }
    }

    // ── Building the planner input from entities ─────────────────────

    private Context buildContext(Integer eventId) {
        List<Atom> atoms = new ArrayList<>();
        List<SettledTeam> settled = new ArrayList<>();
        Map<String, Source> byRef = new LinkedHashMap<>();

        for (TeamEventEntry entry : teamEventEntryRepository.findAllByEvent_EventIdAndStatus(eventId, "APPROVED")) {
            Team team = entry.getTeam();
            List<TeamMember> members = teamMemberRepository.findByTeam_TeamId(team.getTeamId());
            Source src = Source.ofTeam(team, entry, members);
            byRef.put(src.ref(), src);

            int size = members.size();
            if (size <= 0) {
                continue; // defensive: an empty approved team is not a grouping unit
            }
            if (size == 1) {
                atoms.add(Atom.freeAgent(src.ref()));
            } else if (size < MIN) {
                atoms.add(Atom.existingTeam(src.ref(), size));
            } else if (size < MAX) {
                settled.add(new SettledTeam(src.ref(), MAX - size));
            }
            // size >= MAX: full, no room to absorb — ignored as neither atom nor absorber.
        }

        // Registrants approved & active for the current season but on no team of this
        // event: teamless free agents. The query already excludes anyone on an event team,
        // so these never double-count the solo-team members handled above.
        for (User freeAgent : userRepository.findGroupableFreeAgents(eventId)) {
            Source src = Source.ofUser(freeAgent);
            byRef.put(src.ref(), src);
            atoms.add(Atom.freeAgent(src.ref()));
        }

        return new Context(atoms, settled, byRef);
    }

    // ── Applying the plan ────────────────────────────────────────────

    /** Grows an existing team (a kept pair, or a settled team absorbing stragglers). */
    private int growExistingTeam(ProposedTeam pt, Context ctx) {
        Source target = ctx.byRef.get(pt.existingTeamRef());
        List<Integer> originalMemberIds = target.userIds();

        int moved = 0;
        for (String addedRef : pt.addedRefs()) {
            moved += absorbInto(target.team(), target.entry().getEvent().getEventId(), ctx.byRef.get(addedRef));
        }

        String teamName = target.team().getName();
        String eventName = target.entry().getEvent().getName();
        // Notify the members already on the team that it grew.
        originalMemberIds.forEach(uid -> notificationService.createNotification(uid,
                "New teammate(s) added",
                "Your team '" + teamName + "' gained new member(s) during setup grouping for "
                        + eventName + ".", GROUPED_NOTIFICATION));
        return moved;
    }

    /** Creates a brand-new team from lone free agents and gives it a random leader. */
    private int applyNewTeam(HackathonEvent event, ProposedTeam pt, Context ctx) {
        Team newTeam = teamRepository.save(Team.builder()
                .name(uniqueAutoName(event))
                .build());
        teamEventEntryRepository.save(TeamEventEntry.builder()
                .team(newTeam)
                .event(event)
                .status("APPROVED")
                .build());

        List<TeamMember> moved = new ArrayList<>();
        for (String ref : pt.memberRefs()) {
            moved.addAll(reassignMembers(newTeam, event.getEventId(), ctx.byRef.get(ref)));
        }

        TeamMember leader = moved.get(random.nextInt(moved.size()));
        leader.setMemberRole("LEADER");
        teamMemberRepository.save(leader);

        String eventName = event.getName();
        moved.forEach(m -> notificationService.createNotification(m.getUser().getUserId(),
                "You were placed into a new team",
                "You've been grouped into '" + newTeam.getName() + "' for " + eventName
                        + (m == leader ? " as the team leader." : ".") + " Say hi to your teammates!",
                GROUPED_NOTIFICATION));
        return moved.size();
    }

    /** Moves every member of {@code src} into {@code target} as MEMBER, then dissolves src. */
    private int absorbInto(Team target, Integer eventId, Source src) {
        int moved = reassignMembers(target, eventId, src).size();
        return moved;
    }

    /**
     * Places src's people onto {@code target} as MEMBERs. A team source has its members
     * reassigned and its now-empty team's season entry dissolved; a teamless free-agent
     * user has no membership yet, so one is created and there is nothing to dissolve.
     */
    private List<TeamMember> reassignMembers(Team target, Integer eventId, Source src) {
        if (src.isFreeAgentUser()) {
            TeamMember created = teamMemberRepository.save(TeamMember.builder()
                    .team(target).user(src.freeAgentUser()).memberRole("MEMBER").build());
            return new ArrayList<>(List.of(created));
        }
        List<TeamMember> moved = new ArrayList<>(src.members());
        for (TeamMember m : moved) {
            m.setTeam(target);
            m.setMemberRole("MEMBER");
            teamMemberRepository.save(m);
        }
        dissolve(src.team(), eventId);
        return moved;
    }

    /**
     * Removes an emptied source team's participation in this season — only the
     * {@link TeamEventEntry}, never the {@code Team} identity, which may have
     * competed (or will compete) in other seasons. FKs to Team do not cascade, so
     * the only child rows that can exist at SETUP (join requests, invites) are
     * cleared first; submissions/scores/results/prizes only appear once the event
     * is IN_PROGRESS.
     */
    private void dissolve(Team team, Integer eventId) {
        List<JoinRequest> requests = joinRequestRepository.findByTeam_TeamId(team.getTeamId());
        if (!requests.isEmpty()) {
            joinRequestRepository.deleteAll(requests);
        }
        List<TeamInvite> invites = teamInviteRepository.findByTeam_TeamId(team.getTeamId());
        if (!invites.isEmpty()) {
            teamInviteRepository.deleteAll(invites);
        }
        teamEventEntryRepository.findByTeam_TeamIdAndEvent_EventId(team.getTeamId(), eventId)
                .ifPresent(teamEventEntryRepository::delete);
    }

    private String uniqueAutoName(HackathonEvent event) {
        int n = 1;
        String name;
        do {
            name = "Auto Team " + n;
            n++;
        } while (teamEventEntryRepository.existsByEventIdAndNormalizedName(
                event.getEventId(), name.trim().toUpperCase(Locale.ROOT)));
        return name;
    }

    // ── Mapping the plan to the API response ─────────────────────────

    private GroupingPreviewResponse toPreview(GroupingPlan plan, Context ctx) {
        int solo = (int) ctx.atoms.stream().filter(a -> !a.existingTeam()).count();
        int pairs = (int) ctx.atoms.stream().filter(Atom::existingTeam).count();
        int leftoverPeople = ctx.atoms.stream().mapToInt(Atom::size).sum();

        List<ProposedTeamView> teams = plan.teams().stream().map(pt -> {
            boolean existing = pt.origin() == TeamOrigin.EXISTING;
            return ProposedTeamView.builder()
                    .origin(pt.origin().name())
                    .existingTeamId(existing ? teamId(pt.existingTeamRef()) : null)
                    .teamName(existing
                            ? ctx.byRef.get(pt.existingTeamRef()).team().getName()
                            : "New team (auto-named on commit)")
                    .size(pt.size())
                    .members(membersOf(pt.memberRefs(), ctx))
                    .addedMembers(membersOf(pt.addedRefs(), ctx))
                    .build();
        }).toList();

        List<WarningView> warnings = plan.warnings().stream()
                .map(w -> toWarningView(w, ctx)).toList();

        return GroupingPreviewResponse.builder()
                .leftoverPeople(leftoverPeople)
                .soloCount(solo)
                .pairCount(pairs)
                .proposedTeams(teams)
                .warnings(warnings)
                .build();
    }

    private WarningView toWarningView(GroupingWarning w, Context ctx) {
        return WarningView.builder()
                .type(w.type().name())
                .peopleCount(w.peopleCount())
                .message(w.message())
                .people(membersOf(w.atomRefs(), ctx))
                .build();
    }

    private List<MemberView> membersOf(List<String> refs, Context ctx) {
        List<MemberView> views = new ArrayList<>();
        for (String ref : refs) {
            Source src = ctx.byRef.get(ref);
            if (src == null) {
                continue;
            }
            if (src.isFreeAgentUser()) {
                User u = src.freeAgentUser();
                views.add(MemberView.builder().userId(u.getUserId()).fullName(u.getFullName()).build());
                continue;
            }
            for (TeamMember m : src.members()) {
                views.add(MemberView.builder()
                        .userId(m.getUser().getUserId())
                        .fullName(m.getUser().getFullName())
                        .build());
            }
        }
        return views;
    }

    private static Integer teamId(String ref) {
        return Integer.valueOf(ref.substring(1)); // refs are "T{teamId}"
    }

    // ── Guards & holders ─────────────────────────────────────────────

    private HackathonEvent requireSetupEvent(Integer eventId) {
        HackathonEvent event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + eventId));
        if (!"SETUP".equalsIgnoreCase(event.getStatus())) {
            throw new BadRequestException(
                    "Leftover grouping is only available while the event is in SETUP.");
        }
        return event;
    }

    /**
     * A grouping unit: either an approved team snapshotted with its members (ref
     * "T{teamId}") or a teamless free-agent user (ref "U{userId}"). A user source has no
     * TeamMember rows yet — commit creates one when the user is placed onto a team.
     */
    private record Source(Team team, TeamEventEntry entry, List<TeamMember> members, User freeAgentUser) {
        static Source ofTeam(Team team, TeamEventEntry entry, List<TeamMember> members) {
            return new Source(team, entry, members, null);
        }

        static Source ofUser(User user) {
            return new Source(null, null, List.of(), user);
        }

        boolean isFreeAgentUser() {
            return freeAgentUser != null;
        }

        String ref() {
            return team != null ? "T" + team.getTeamId() : "U" + freeAgentUser.getUserId();
        }

        List<Integer> userIds() {
            return team != null
                    ? members.stream().map(m -> m.getUser().getUserId()).toList()
                    : List.of(freeAgentUser.getUserId());
        }
    }

    private record Context(List<Atom> atoms, List<SettledTeam> settled, Map<String, Source> byRef) {
    }
}
