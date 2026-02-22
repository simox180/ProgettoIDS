package it.unicam.hackhub.controller;

import it.unicam.hackhub.model.Hackathon;
import it.unicam.hackhub.model.StaffAssignment;
import it.unicam.hackhub.model.Team;
import it.unicam.hackhub.model.TeamRegistration;
import it.unicam.hackhub.model.ViolationReport;
import it.unicam.hackhub.model.enums.HackathonStatus;
import it.unicam.hackhub.model.enums.StaffRole;
import it.unicam.hackhub.repository.HackathonRepository;
import it.unicam.hackhub.repository.StaffAssignmentRepository;
import it.unicam.hackhub.repository.TeamRepository;
import it.unicam.hackhub.repository.TeamRegistrationRepository;
import it.unicam.hackhub.repository.ViolationReportRepository;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class ViolationReportController {
    private final HackathonRepository hackathonRepository;
    private final StaffAssignmentRepository staffAssignmentRepository;
    private final TeamRepository teamRepository;
    private final TeamRegistrationRepository teamRegistrationRepository;
    private final ViolationReportRepository violationReportRepository;

    public ViolationReportController(HackathonRepository hackathonRepository,
                                     StaffAssignmentRepository staffAssignmentRepository,
                                     TeamRepository teamRepository,
                                     TeamRegistrationRepository teamRegistrationRepository,
                                     ViolationReportRepository violationReportRepository) {
        this.hackathonRepository = hackathonRepository;
        this.staffAssignmentRepository = staffAssignmentRepository;
        this.teamRepository = teamRepository;
        this.teamRegistrationRepository = teamRegistrationRepository;
        this.violationReportRepository = violationReportRepository;
    }

    public List<MentorHackathonOption> listMentorHackathons(long currentStaffId) {
        Set<Long> hackathonIds = new LinkedHashSet<>();
        for (StaffAssignment assignment : staffAssignmentRepository.findByStaffId(currentStaffId)) {
            if (assignment.getRole() == StaffRole.MENTOR) {
                hackathonIds.add(assignment.getHackathonId());
            }
        }

        return hackathonIds.stream()
                .map(hackathonId -> {
                    Hackathon hackathon = hackathonRepository.findById(hackathonId).orElse(null);
                    return new MentorHackathonOption(
                            hackathonId,
                            hackathon == null ? "N/A" : safe(hackathon.getHackathonName())
                    );
                })
                .toList();
    }

    public List<ReportTeamOption> listReportableTeams(long currentStaffId, long hackathonId) {
        boolean mentorAssigned = false;
        for (StaffAssignment assignment : staffAssignmentRepository.findByHackathonId(hackathonId)) {
            if (assignment.getRole() == StaffRole.MENTOR && assignment.getStaffId() == currentStaffId) {
                mentorAssigned = true;
                break;
            }
        }
        if (!mentorAssigned) {
            throw new IllegalArgumentException("Comando disponibile solo per MENTOR.");
        }

        List<TeamRegistration> registrations = teamRegistrationRepository.findByHackathonId(hackathonId);
        return registrations.stream()
                .map(registration -> {
                    Team team = teamRepository.findById(registration.getTeamId()).orElse(null);
                    return new ReportTeamOption(
                            registration.getTeamId(),
                            team == null ? "N/A" : safe(team.getTeamName()),
                            registration.isExpelled()
                    );
                })
                .toList();
    }

    public List<OrganizerHackathonOption> listOrganizerHackathons(long currentStaffId) {
        return staffAssignmentRepository.findByStaffId(currentStaffId).stream()
                .filter(assignment -> assignment.getRole() == StaffRole.ORGANIZER)
                .map(StaffAssignment::getHackathonId)
                .distinct()
                .map(hackathonId -> {
                    Hackathon hackathon = hackathonRepository.findById(hackathonId).orElse(null);
                    return new OrganizerHackathonOption(
                            hackathonId,
                            hackathon == null ? "N/A" : safe(hackathon.getHackathonName())
                    );
                })
                .toList();
    }

    public List<ViolationReport> listReports(long currentStaffId, long hackathonId, boolean onlyPending) {
        boolean organizerAssigned = false;
        for (StaffAssignment assignment : staffAssignmentRepository.findByHackathonId(hackathonId)) {
            if (assignment.getRole() == StaffRole.ORGANIZER && assignment.getStaffId() == currentStaffId) {
                organizerAssigned = true;
                break;
            }
        }
        if (!organizerAssigned) {
            throw new IllegalArgumentException("Not authorized: only organizer can view reports");
        }

        if (onlyPending) {
            return violationReportRepository.findPendingByHackathonId(hackathonId);
        }
        return violationReportRepository.findByHackathonId(hackathonId);
    }

    public ViolationReport createReport(long currentStaffId, long hackathonId, long teamId, String description) {
        Hackathon hackathon = hackathonRepository.findById(hackathonId)
                .orElseThrow(() -> new IllegalArgumentException("Hackathon non trovato"));
        if (hackathon.getStatus() != HackathonStatus.RUNNING) {
            throw new IllegalStateException("Segnalazioni consentite solo in stato RUNNING");
        }

        boolean mentorAssigned = false;
        for (StaffAssignment assignment : staffAssignmentRepository.findByHackathonId(hackathonId)) {
            if (assignment.getRole() == StaffRole.MENTOR && assignment.getStaffId() == currentStaffId) {
                mentorAssigned = true;
                break;
            }
        }
        if (!mentorAssigned) {
            throw new IllegalArgumentException("Not authorized: only mentor can create report");
        }

        if (teamRegistrationRepository.findByTeamIdAndHackathonId(teamId, hackathonId).isEmpty()) {
            throw new IllegalArgumentException("Team not registered to this hackathon");
        }

        ViolationReport report = new ViolationReport(
                0L,
                hackathonId,
                teamId,
                currentStaffId,
                description,
                LocalDateTime.now(),
                null
        );
        return violationReportRepository.save(report);
    }

    public ViolationReport manageReport(long currentStaffId, long reportId, String action) {
        ViolationReport report = violationReportRepository.findById(reportId)
                .orElseThrow(() -> new IllegalArgumentException("Report not found"));

        boolean organizerAssigned = false;
        for (StaffAssignment assignment : staffAssignmentRepository.findByHackathonId(report.getHackathonId())) {
            if (assignment.getRole() == StaffRole.ORGANIZER && assignment.getStaffId() == currentStaffId) {
                organizerAssigned = true;
                break;
            }
        }
        if (!organizerAssigned) {
            throw new IllegalArgumentException("Not authorized: only organizer can manage reports");
        }

        if (report.getDecision() != null) {
            throw new IllegalStateException("Report already managed");
        }

        if ("REJECT".equalsIgnoreCase(action)) {
            report.setDecision("REJECTED");
            return violationReportRepository.save(report);
        }

        if ("EXPEL".equalsIgnoreCase(action)) {
            TeamRegistration registration = teamRegistrationRepository
                    .findByTeamIdAndHackathonId(report.getTeamId(), report.getHackathonId())
                    .orElseThrow(() -> new IllegalArgumentException("Team not registered to this hackathon"));
            registration.setExpelled(true);
            teamRegistrationRepository.save(registration);

            report.setDecision("TEAM_EXPELLED");
            return violationReportRepository.save(report);
        }

        throw new IllegalArgumentException("Invalid action");
    }

    private String safe(String value) {
        return value == null ? "-" : value;
    }

    public record MentorHackathonOption(long hackathonId, String hackathonName) {
    }

    public record ReportTeamOption(long teamId, String teamName, boolean expelled) {
    }

    public record OrganizerHackathonOption(long hackathonId, String hackathonName) {
    }
}
