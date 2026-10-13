package com.seal.hackathon.service;

import com.seal.hackathon.entity.HackathonEvent;
import com.seal.hackathon.entity.JudgeAssignment;
import com.seal.hackathon.entity.MentorAssignment;
import com.seal.hackathon.entity.Prize;
import com.seal.hackathon.entity.Round;
import com.seal.hackathon.entity.RoundResult;
import com.seal.hackathon.entity.Submission;
import com.seal.hackathon.entity.Team;
import com.seal.hackathon.entity.TeamEventEntry;
import com.seal.hackathon.entity.TeamMember;
import com.seal.hackathon.entity.Track;
import com.seal.hackathon.entity.User;
import com.seal.hackathon.exception.BadRequestException;
import com.seal.hackathon.exception.ResourceNotFoundException;
import com.seal.hackathon.repository.HackathonEventRepository;
import com.seal.hackathon.repository.JudgeAssignmentRepository;
import com.seal.hackathon.repository.MentorAssignmentRepository;
import com.seal.hackathon.repository.PrizeRepository;
import com.seal.hackathon.repository.RoundRepository;
import com.seal.hackathon.repository.RoundResultRepository;
import com.seal.hackathon.repository.SubmissionRepository;
import com.seal.hackathon.repository.TeamEventEntryRepository;
import com.seal.hackathon.repository.TeamMemberRepository;
import com.seal.hackathon.repository.TrackRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminEventCsvExportService {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final HackathonEventRepository eventRepository;
    private final RoundRepository roundRepository;
    private final TrackRepository trackRepository;
    private final MentorAssignmentRepository mentorAssignmentRepository;
    private final JudgeAssignmentRepository judgeAssignmentRepository;
    private final TeamEventEntryRepository teamEventEntryRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final SubmissionRepository submissionRepository;
    private final RoundResultRepository roundResultRepository;
    private final PrizeRepository prizeRepository;

    @Transactional(readOnly = true)
    public ExportedCsv exportCompletedEvent(Integer eventId) {
        HackathonEvent event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + eventId));
        if (!"COMPLETED".equalsIgnoreCase(event.getStatus())) {
            throw new BadRequestException("CSV export is only available after the event is COMPLETED.");
        }

        List<CsvRow> rows = new ArrayList<>();
        addEventRow(rows, event);

        List<Round> rounds = roundRepository.findAllByEvent_EventIdOrderByOrderNumber(eventId);
        Map<Integer, Round> roundById = rounds.stream()
                .collect(Collectors.toMap(Round::getRoundId, round -> round));
        for (Round round : rounds) {
            addRoundRow(rows, event, round);
        }

        List<Track> tracks = trackRepository.findAllByEvent_EventId(eventId);
        Map<Integer, String> trackNameById = tracks.stream()
                .collect(Collectors.toMap(Track::getTrackId, Track::getName));
        for (Track track : tracks) {
            addTrackRow(rows, event, track);
        }

        for (MentorAssignment assignment : mentorAssignmentRepository.findActiveByEvent(eventId)) {
            addMentorRow(rows, event, assignment);
        }
        for (JudgeAssignment assignment : judgeAssignmentRepository.findActiveByEvent(eventId)) {
            addJudgeRow(rows, event, assignment);
        }

        List<Prize> prizes = prizeRepository.findAllByEvent_EventIdOrderByRankPosition(eventId);
        Map<Integer, Prize> prizeByTeamId = new HashMap<>();
        for (Prize prize : prizes) {
            if (prize.getTeam() != null) {
                prizeByTeamId.putIfAbsent(prize.getTeam().getTeamId(), prize);
            }
        }

        List<RoundResult> results = new ArrayList<>();
        for (Round round : rounds) {
            results.addAll(roundResultRepository.findAllByRound_RoundIdOrderByRankPosition(round.getRoundId()));
        }
        Map<Integer, List<RoundResult>> resultsByTeamId = results.stream()
                .collect(Collectors.groupingBy(result -> result.getTeam().getTeamId()));

        List<Submission> submissions = submissionRepository.findAllByEventIdForExport(eventId);
        Map<Integer, List<Submission>> submissionsByTeamId = submissions.stream()
                .collect(Collectors.groupingBy(submission -> submission.getTeam().getTeamId()));

        List<TeamEventEntry> entries = teamEventEntryRepository.findAllByEvent_EventId(eventId).stream()
                .sorted(Comparator
                        .comparing((TeamEventEntry entry) -> trackName(entry.getTrack()), Comparator.nullsLast(String::compareToIgnoreCase))
                        .thenComparing(entry -> entry.getTeam().getName(), String.CASE_INSENSITIVE_ORDER))
                .toList();
        for (TeamEventEntry entry : entries) {
            Team team = entry.getTeam();
            List<TeamMember> members = teamMemberRepository.findByTeam_TeamId(team.getTeamId()).stream()
                    .sorted(Comparator
                            .comparing((TeamMember member) -> "LEADER".equalsIgnoreCase(member.getMemberRole()) ? 0 : 1)
                            .thenComparing(member -> member.getUser().getFullName(), String.CASE_INSENSITIVE_ORDER))
                    .toList();
            addTeamRow(rows, event, entry, members.size(), resultsByTeamId.getOrDefault(team.getTeamId(), List.of()),
                    submissionsByTeamId.getOrDefault(team.getTeamId(), List.of()), prizeByTeamId.get(team.getTeamId()));
            for (TeamMember member : members) {
                addMemberRow(rows, event, entry, member);
            }
        }

        for (Submission submission : submissions) {
            addSubmissionRow(rows, event, submission);
        }
        for (RoundResult result : results) {
            Round round = roundById.get(result.getRound().getRoundId());
            addResultRow(rows, event, result, round);
        }
        for (Prize prize : prizes) {
            addPrizeRow(rows, event, prize);
        }

        String csv = toCsv(rows);
        return new ExportedCsv(fileName(event), csv.getBytes(StandardCharsets.UTF_8));
    }

    private void addEventRow(List<CsvRow> rows, HackathonEvent event) {
        rows.add(new CsvRow(
                "EVENT",
                event.getName(),
                value(event.getStatus()),
                event.getSeason() + " " + event.getYear(),
                details(
                        pair("registration", range(event.getRegistrationStart(), event.getRegistrationEnd())),
                        pair("event", range(event.getStartDate(), event.getEndDate())),
                        pair("mode", event.getTrackSelectionMode()),
                        pair("topic", event.getTopic()),
                        pair("description", event.getDescription()))));
    }

    private void addRoundRow(List<CsvRow> rows, HackathonEvent event, Round round) {
        rows.add(new CsvRow(
                "ROUND",
                round.getName(),
                value(round.getStatus()),
                event.getName(),
                details(
                        pair("order", round.getOrderNumber()),
                        pair("window", range(round.getStartTime(), round.getEndTime())),
                        pair("deadline", format(round.getSubmissionDeadline())),
                        pair("top_n", round.getTopNAdvance()),
                        Boolean.TRUE.equals(round.getIsFinal()) ? "final round" : "preliminary round")));
    }

    private void addTrackRow(List<CsvRow> rows, HackathonEvent event, Track track) {
        rows.add(new CsvRow(
                "TRACK",
                track.getName(),
                "",
                event.getName(),
                details(
                        pair("capacity", track.getCapacity()),
                        pair("problem", track.getProblemFileName()),
                        pair("problem_released", track.getProblemReleased()),
                        pair("uploaded_at", format(track.getProblemUploadedAt())),
                        pair("released_at", format(track.getProblemReleasedAt())),
                        pair("description", track.getDescription()))));
    }

    private void addMentorRow(List<CsvRow> rows, HackathonEvent event, MentorAssignment assignment) {
        User mentor = assignment.getMentor();
        rows.add(new CsvRow(
                "MENTOR",
                mentor.getFullName(),
                activeStatus(assignment.getIsActive()),
                trackName(assignment.getTrack()),
                details(
                        pair("event", event.getName()),
                        pair("email", mentor.getEmail()),
                        pair("user_type", mentor.getUserType()))));
    }

    private void addJudgeRow(List<CsvRow> rows, HackathonEvent event, JudgeAssignment assignment) {
        User judge = assignment.getJudge();
        rows.add(new CsvRow(
                "JUDGE",
                judge.getFullName(),
                activeStatus(assignment.getIsActive()),
                details(assignment.getRound().getName(), trackName(assignment.getTrack())),
                details(
                        pair("event", event.getName()),
                        pair("email", judge.getEmail()),
                        pair("judge_type", judge.getJudgeType()),
                        pair("scope", assignment.getTrack() == null ? "ALL_TRACKS" : "TRACK"))));
    }

    private void addTeamRow(List<CsvRow> rows, HackathonEvent event, TeamEventEntry entry, int memberCount,
                            List<RoundResult> results, List<Submission> submissions, Prize prize) {
        Team team = entry.getTeam();
        String ranking = results.stream()
                .sorted(Comparator.comparing(result -> result.getRound().getOrderNumber()))
                .map(result -> result.getRound().getName() + " #" + result.getRankPosition()
                        + " (" + result.getTotalScore() + ")")
                .collect(Collectors.joining(" | "));
        rows.add(new CsvRow(
                "TEAM",
                team.getName(),
                value(entry.getStatus()),
                trackName(entry.getTrack()),
                details(
                        pair("event", event.getName()),
                        pair("members", memberCount),
                        pair("active", team.getIsActive()),
                        pair("submissions", submissions.size()),
                        pair("ranking", ranking),
                        pair("prize", prize != null ? prize.getName() : null),
                        pair("disqualified_at", format(entry.getDisqualifiedAt())),
                        pair("disqualified_reason", entry.getDisqualifiedReason()),
                        pair("description", team.getDescription()))));
    }

    private void addMemberRow(List<CsvRow> rows, HackathonEvent event, TeamEventEntry entry, TeamMember member) {
        User user = member.getUser();
        rows.add(new CsvRow(
                "MEMBER",
                user.getFullName(),
                value(member.getMemberRole()),
                entry.getTeam().getName(),
                details(
                        pair("event", event.getName()),
                        pair("track", trackName(entry.getTrack())),
                        pair("email", user.getEmail()),
                        pair("student_id", user.getStudentId()),
                        pair("university", user.getUniversity()),
                        pair("user_type", user.getUserType()),
                        pair("joined_at", format(member.getJoinedAt())))));
    }

    private void addSubmissionRow(List<CsvRow> rows, HackathonEvent event, Submission submission) {
        rows.add(new CsvRow(
                "SUBMISSION",
                submission.getTeam().getName(),
                value(submission.getStatus()),
                submission.getRound().getName(),
                details(
                        pair("event", event.getName()),
                        pair("submitted_by", submission.getSubmittedBy().getFullName()),
                        pair("submitted_at", format(submission.getSubmittedAt())),
                        pair("repo", submission.getRepoUrl()),
                        pair("demo", submission.getDemoUrl()),
                        pair("slide", submission.getSlideUrl()),
                        pair("description", submission.getDescription()))));
    }

    private void addResultRow(List<CsvRow> rows, HackathonEvent event, RoundResult result, Round round) {
        rows.add(new CsvRow(
                "RESULT",
                result.getTeam().getName(),
                Boolean.TRUE.equals(result.getIsPublished()) ? "PUBLISHED" : "DRAFT",
                round != null ? round.getName() : result.getRound().getName(),
                details(
                        pair("event", event.getName()),
                        pair("rank", result.getRankPosition()),
                        pair("total_score", result.getTotalScore()),
                        pair("finalized_by", result.getFinalizedBy() != null ? result.getFinalizedBy().getFullName() : null),
                        pair("finalized_at", format(result.getFinalizedAt())))));
    }

    private void addPrizeRow(List<CsvRow> rows, HackathonEvent event, Prize prize) {
        rows.add(new CsvRow(
                "PRIZE",
                prize.getName(),
                prize.getAwardedAt() == null ? "DRAFT" : "ANNOUNCED",
                prize.getTeam() != null ? prize.getTeam().getName() : "",
                details(
                        pair("event", event.getName()),
                        pair("rank", prize.getRankPosition()),
                        pair("track", trackName(prize.getTrack())),
                        pair("awarded_at", format(prize.getAwardedAt())),
                        pair("description", prize.getDescription()))));
    }

    private String toCsv(List<CsvRow> rows) {
        StringBuilder out = new StringBuilder("\uFEFF");
        out.append("section,name,status_or_role,related,details\r\n");
        for (CsvRow row : rows) {
            out.append(csv(row.section())).append(',')
                    .append(csv(row.name())).append(',')
                    .append(csv(row.statusOrRole())).append(',')
                    .append(csv(row.related())).append(',')
                    .append(csv(row.details())).append("\r\n");
        }
        return out.toString();
    }

    private String csv(String value) {
        String text = value(value);
        if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }

    private String details(String... parts) {
        return Arrays.stream(parts)
                .filter(part -> part != null && !part.isBlank())
                .collect(Collectors.joining("; "));
    }

    private String pair(String label, Object value) {
        String text = value(value);
        return text.isBlank() ? "" : label + ": " + text;
    }

    private String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String range(LocalDateTime start, LocalDateTime end) {
        String left = format(start);
        String right = format(end);
        if (left.isBlank() && right.isBlank()) {
            return "";
        }
        return left + " to " + right;
    }

    private String format(LocalDateTime value) {
        return value == null ? "" : DATE_TIME.format(value);
    }

    private String trackName(Track track) {
        return track == null ? "" : track.getName();
    }

    private String activeStatus(Boolean active) {
        return Boolean.TRUE.equals(active) ? "ACTIVE" : "INACTIVE";
    }

    private String fileName(HackathonEvent event) {
        String slug = event.getName() == null ? "event" : event.getName().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
        if (slug.isBlank()) {
            slug = "event";
        }
        return slug + "-export.csv";
    }

    public record ExportedCsv(String fileName, byte[] content) {
    }

    private record CsvRow(String section, String name, String statusOrRole, String related, String details) {
    }
}
