package it.unicam.hackhub.controller;

import it.unicam.hackhub.model.Hackathon;
import it.unicam.hackhub.model.StaffAssignment;
import it.unicam.hackhub.model.Submission;
import it.unicam.hackhub.model.TeamRegistration;
import it.unicam.hackhub.model.enums.StaffRole;
import it.unicam.hackhub.repository.HackathonRepository;
import it.unicam.hackhub.repository.StaffAssignmentRepository;
import it.unicam.hackhub.repository.SubmissionRepository;
import it.unicam.hackhub.repository.TeamRegistrationRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class SubmissionAccessController {
    private final HackathonRepository hackathonRepository;
    private final StaffAssignmentRepository staffAssignmentRepository;
    private final TeamRegistrationRepository teamRegistrationRepository;
    private final SubmissionRepository submissionRepository;

    public SubmissionAccessController(HackathonRepository hackathonRepository,
                                      StaffAssignmentRepository staffAssignmentRepository,
                                      TeamRegistrationRepository teamRegistrationRepository,
                                      SubmissionRepository submissionRepository) {
        this.hackathonRepository = hackathonRepository;
        this.staffAssignmentRepository = staffAssignmentRepository;
        this.teamRegistrationRepository = teamRegistrationRepository;
        this.submissionRepository = submissionRepository;
    }

    public List<AssignedHackathonView> listAssignedHackathons(long currentStaffId) {
        Map<Long, List<StaffAssignment>> assignmentsByHackathon = staffAssignmentRepository.findByStaffId(currentStaffId).stream()
                .collect(Collectors.groupingBy(StaffAssignment::getHackathonId));

        return assignmentsByHackathon.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    long hackathonId = entry.getKey();
                    Hackathon hackathon = hackathonRepository.findById(hackathonId).orElse(null);
                    Set<StaffRole> roles = entry.getValue().stream()
                            .map(StaffAssignment::getRole)
                            .distinct()
                            .sorted(Comparator.comparing(Enum::name))
                            .collect(Collectors.toCollection(LinkedHashSet::new));
                    return new AssignedHackathonView(
                            hackathonId,
                            hackathon == null ? "N/A" : safe(hackathon.getHackathonName()),
                            hackathon == null || hackathon.getStatus() == null ? "-" : hackathon.getStatus().name(),
                            hackathon == null ? "-" : safe(hackathon.getLocation()),
                            roles
                    );
                })
                .toList();
    }

    public List<Submission> listSubmissionsForHackathon(long currentStaffId, long hackathonId) {
        List<StaffAssignment> assignments = staffAssignmentRepository.findByHackathonId(hackathonId);
        boolean assigned = assignments.stream()
                .anyMatch(assignment -> assignment.getStaffId() == currentStaffId);
        if (!assigned) {
            throw new IllegalArgumentException("Not authorized: staff not assigned to this hackathon");
        }

        List<TeamRegistration> registrations = teamRegistrationRepository.findByHackathonId(hackathonId);
        List<Submission> submissions = new ArrayList<>();
        for (TeamRegistration registration : registrations) {
            submissionRepository.findByRegistrationId(registration.getRegistrationId())
                    .ifPresent(submissions::add);
        }
        return submissions;
    }

    public List<SubmissionView> listSubmissionViewsForHackathon(long currentStaffId, long hackathonId) {
        return listSubmissionsForHackathon(currentStaffId, hackathonId).stream()
                .sorted(Comparator.comparingLong(Submission::getSubmissionId))
                .map(submission -> new SubmissionView(
                        submission.getSubmissionId(),
                        submission.getRegistrationId(),
                        submission.getLastUpdatedAt(),
                        submission.getContent()
                ))
                .toList();
    }

    public Optional<SubmissionDetailView> getSubmissionDetailForHackathon(long currentStaffId,
                                                                           long hackathonId,
                                                                           long submissionId) {
        if (submissionId <= 0) {
            return Optional.empty();
        }

        return listSubmissionsForHackathon(currentStaffId, hackathonId).stream()
                .filter(submission -> submission.getSubmissionId() == submissionId)
                .findFirst()
                .map(submission -> new SubmissionDetailView(
                        submission.getSubmissionId(),
                        submission.getRegistrationId(),
                        submission.getLastUpdatedAt(),
                        submission.getContent()
                ));
    }

    private String safe(String value) {
        return value == null ? "-" : value;
    }

    public record AssignedHackathonView(long hackathonId,
                                        String name,
                                        String status,
                                        String location,
                                        Set<StaffRole> roles) {
        public String rolesLabel() {
            if (roles == null || roles.isEmpty()) {
                return "-";
            }
            return roles.stream()
                    .map(Enum::name)
                    .sorted()
                    .collect(Collectors.joining(","));
        }
    }

    public record SubmissionView(long submissionId,
                                 long registrationId,
                                 LocalDateTime updatedAt,
                                 String content) {
    }

    public record SubmissionDetailView(long submissionId,
                                       long registrationId,
                                       LocalDateTime updatedAt,
                                       String content) {
    }
}
