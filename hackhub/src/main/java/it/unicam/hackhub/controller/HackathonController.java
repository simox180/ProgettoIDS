package it.unicam.hackhub.controller;

import it.unicam.hackhub.external.PaymentSystem;
import it.unicam.hackhub.model.Evaluation;
import it.unicam.hackhub.model.Hackathon;
import it.unicam.hackhub.model.StaffAssignment;
import it.unicam.hackhub.model.StaffMember;
import it.unicam.hackhub.model.Submission;
import it.unicam.hackhub.model.Team;
import it.unicam.hackhub.model.TeamRegistration;
import it.unicam.hackhub.model.enums.HackathonStatus;
import it.unicam.hackhub.model.enums.StaffRole;
import it.unicam.hackhub.repository.EvaluationRepository;
import it.unicam.hackhub.repository.HackathonRepository;
import it.unicam.hackhub.repository.StaffAssignmentRepository;
import it.unicam.hackhub.repository.StaffMemberRepository;
import it.unicam.hackhub.repository.SubmissionRepository;
import it.unicam.hackhub.repository.TeamRegistrationRepository;
import it.unicam.hackhub.repository.TeamRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class HackathonController {
    private final HackathonRepository hackathonRepository;
    private final StaffAssignmentRepository staffAssignmentRepository;
    private final StaffMemberRepository staffMemberRepository;
    private final TeamRegistrationRepository teamRegistrationRepository;
    private final SubmissionRepository submissionRepository;
    private final EvaluationRepository evaluationRepository;
    private final TeamRepository teamRepository;
    private final PaymentSystem paymentSystem;

    public HackathonController(HackathonRepository hackathonRepository,
                               StaffAssignmentRepository staffAssignmentRepository,
                               StaffMemberRepository staffMemberRepository,
                               TeamRegistrationRepository teamRegistrationRepository,
                               SubmissionRepository submissionRepository,
                               EvaluationRepository evaluationRepository,
                               TeamRepository teamRepository,
                               PaymentSystem paymentSystem) {
        this.hackathonRepository = hackathonRepository;
        this.staffAssignmentRepository = staffAssignmentRepository;
        this.staffMemberRepository = staffMemberRepository;
        this.teamRegistrationRepository = teamRegistrationRepository;
        this.submissionRepository = submissionRepository;
        this.evaluationRepository = evaluationRepository;
        this.teamRepository = teamRepository;
        this.paymentSystem = paymentSystem;
    }

    // Restituisce tutti gli hackathon.
    public List<Hackathon> listHackathons() {
        return hackathonRepository.findAll();
    }

    // Restituisce il dettaglio di un hackathon.
    public Optional<Hackathon> getHackathonDetails(long hackathonId) {
        return hackathonRepository.findById(hackathonId);
    }

    // Elenca lo staff selezionabile durante la creazione hackathon.
    public List<StaffSelectView> listSelectableStaff(long currentStaffId) {
        if (currentStaffId <= 0) {
            throw new IllegalArgumentException("Staff non valido");
        }
        ensureCanCreateHackathon(currentStaffId);

        List<StaffSelectView> result = new ArrayList<>();
        for (StaffMember staffMember : staffMemberRepository.findAll()) {
            if (staffMember.getStaffId() == currentStaffId) {
                continue;
            }
            result.add(new StaffSelectView(
                    staffMember.getStaffId(),
                    safe(staffMember.getStaffUsername()),
                    safe(staffMember.getStaffName())
            ));
        }
        return result;
    }

    // Elenca lo staff selezionabile filtrato per ruolo.
    public List<StaffSelectView> listSelectableStaffByRole(long currentStaffId, StaffRole role) {
        if (currentStaffId <= 0) {
            throw new IllegalArgumentException("Staff non valido");
        }
        if (role == null) {
            throw new IllegalArgumentException("Ruolo non valido");
        }
        ensureCanCreateHackathon(currentStaffId);

        List<StaffSelectView> result = new ArrayList<>();
        for (StaffMember staffMember : staffMemberRepository.findAll()) {
            long staffId = staffMember.getStaffId();
            if (staffId == currentStaffId) {
                continue;
            }
            if (!hasAnyAssignmentWithRole(staffId, role)) {
                continue;
            }
            result.add(new StaffSelectView(
                    staffId,
                    safe(staffMember.getStaffUsername()),
                    safe(staffMember.getStaffName())
            ));
        }
        return result;
    }

    // Crea un hackathon nuovo con staff iniziale organizer/judge/mentor.
    public Hackathon createHackathon(long currentStaffId,
                                     String name,
                                     String regulation,
                                     LocalDate regDeadline,
                                     LocalDate startDate,
                                     LocalDate endDate,
                                     LocalDate submissionDeadline,
                                     String location,
                                     double prizeAmount,
                                     int maxTeamSize,
                                     long judgeStaffId,
                                     List<Long> mentorStaffIds) {
        if (currentStaffId <= 0) {
            throw new IllegalArgumentException("Staff non valido");
        }
        ensureCanCreateHackathon(currentStaffId);
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Nome hackathon non valido");
        }
        String normalizedName = name.trim();
        if (hackathonRepository.findByName(normalizedName).isPresent()) {
            throw new IllegalArgumentException("Nome hackathon gia esistente");
        }
        if (location == null || location.trim().isEmpty()) {
            throw new IllegalArgumentException("Location non valida");
        }
        if (maxTeamSize <= 0) {
            throw new IllegalArgumentException("Max team size non valido");
        }
        if (Double.isNaN(prizeAmount) || Double.isInfinite(prizeAmount) || prizeAmount < 0) {
            throw new IllegalArgumentException("Premio non valido");
        }

        // Le date devono essere coerenti prima di creare l'hackathon.
        if (regDeadline == null || startDate == null || endDate == null) {
            throw new IllegalArgumentException("Date hackathon non valide");
        }
        if (!regDeadline.isBefore(startDate)) {
            throw new IllegalArgumentException("Registration deadline deve essere prima dello start date");
        }
        if (!startDate.isBefore(endDate)) {
            throw new IllegalArgumentException("Start date deve essere prima di end date");
        }
        if (submissionDeadline == null) {
            throw new IllegalArgumentException("Submission deadline non valida");
        }
        if (submissionDeadline.isBefore(startDate)) {
            throw new IllegalArgumentException("Submission deadline non puo essere prima della start date");
        }
        if (mentorStaffIds == null || mentorStaffIds.isEmpty()) {
            throw new IllegalArgumentException("Almeno un mentor e richiesto");
        }
        // Judge e organizer devono essere persone diverse.
        if (judgeStaffId <= 0) {
            throw new IllegalArgumentException("Judge non valido");
        }
        if (judgeStaffId == currentStaffId) {
            throw new IllegalArgumentException("Judge deve essere diverso dall'organizer");
        }
        // Judge e mentor devono avere davvero quel ruolo.
        if (!hasAnyAssignmentWithRole(judgeStaffId, StaffRole.JUDGE)) {
            throw new IllegalArgumentException("Judge non valido: staff non e un giudice");
        }

        Set<Long> mentorIds = new LinkedHashSet<>();
        for (Long mentorId : mentorStaffIds) {
            if (mentorId == null || mentorId <= 0) {
                throw new IllegalArgumentException("Mentor non valido");
            }
            if (!hasAnyAssignmentWithRole(mentorId, StaffRole.MENTOR)) {
                throw new IllegalArgumentException("Mentor non valido: staff non e un mentor");
            }
            mentorIds.add(mentorId);
        }
        if (mentorIds.isEmpty()) {
            throw new IllegalArgumentException("Almeno un mentor e richiesto");
        }
        // Evita che la stessa persona venga messa sia judge sia mentor.
        if (mentorIds.contains(judgeStaffId)) {
            throw new IllegalArgumentException("Judge non puo essere anche mentor");
        }

        staffMemberRepository.findById(judgeStaffId)
                .orElseThrow(() -> new IllegalArgumentException("Judge non trovato"));
        for (Long mentorId : mentorIds) {
            staffMemberRepository.findById(mentorId)
                    .orElseThrow(() -> new IllegalArgumentException("Mentor non trovato: " + mentorId));
        }

        LocalDateTime registrationDeadline = regDeadline.atTime(23, 59);
        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(23, 59);
        LocalDateTime submissionDeadlineTime = submissionDeadline.atTime(23, 59);

        Hackathon hackathon = Hackathon.builder()
                .hackathonId(0L)
                .hackathonName(normalizedName)
                .regulation(regulation == null ? "" : regulation.trim())
                .registrationDeadline(registrationDeadline)
                .startDate(startDateTime)
                .endDate(endDateTime)
                .submissionDeadline(submissionDeadlineTime)
                .location(location.trim())
                .prizeAmount(BigDecimal.valueOf(prizeAmount))
                .maxTeamSize(maxTeamSize)
                .status(HackathonStatus.REGISTRATION)
                .winnerTeamId(null)
                .build();
        Hackathon savedHackathon = hackathonRepository.save(hackathon);

        // Le assegnazioni vengono salvate subito dopo la creazione.
        List<StaffAssignment> assignments = new ArrayList<>();
        assignments.add(new StaffAssignment(0L, currentStaffId, savedHackathon.getHackathonId(), StaffRole.ORGANIZER));
        assignments.add(new StaffAssignment(0L, judgeStaffId, savedHackathon.getHackathonId(), StaffRole.JUDGE));
        for (Long mentorId : mentorIds) {
            assignments.add(new StaffAssignment(0L, mentorId, savedHackathon.getHackathonId(), StaffRole.MENTOR));
        }
        for (StaffAssignment assignment : assignments) {
            staffAssignmentRepository.save(assignment);
        }

        return savedHackathon;
    }

    // Porta l'hackathon allo stato successivo.
    public Hackathon advanceStatus(long currentStaffId, long hackathonId) {
        Hackathon hackathon = hackathonRepository.findById(hackathonId)
                .orElseThrow(() -> new IllegalArgumentException("Hackathon not found"));
        ensureOrganizer(currentStaffId, hackathonId, "Not authorized: only organizer can advance status");

        // La sequenza ammessa e': REGISTRATION -> RUNNING -> REVIEW -> CLOSED.
        HackathonStatus nextStatus = switch (hackathon.getStatus()) {
            case REGISTRATION -> HackathonStatus.RUNNING;
            case RUNNING -> HackathonStatus.REVIEW;
            case REVIEW -> HackathonStatus.CLOSED;
            case CLOSED -> throw new IllegalStateException("Hackathon already closed");
        };

        hackathon.changeStatus(nextStatus);
        return hackathonRepository.save(hackathon);
    }

    // Imposta il winner e tenta il pagamento del premio.
    public Hackathon setWinner(long currentStaffId, long hackathonId, long teamId) {
        Hackathon hackathon = hackathonRepository.findById(hackathonId)
                .orElseThrow(() -> new IllegalArgumentException("Hackathon not found"));
        ensureOrganizer(currentStaffId, hackathonId, "Not authorized: only organizer can manage prize");

        Long existingWinnerTeamId = hackathon.getWinnerTeamId();
        // Se e' gia' pagato lo stesso team, l'operazione non cambia nulla.
        if (existingWinnerTeamId != null) {
            if (existingWinnerTeamId == teamId && hackathon.isPrizePaid()) {
                return hackathon;
            }
            if (existingWinnerTeamId != teamId) {
                throw new IllegalStateException("Winner already set to another team");
            }
        }

        if (!hackathon.canEvaluate()) {
            throw new IllegalStateException("Hackathon not in REVIEW");
        }

        // Il winner deve essere un team registrato e non espulso.
        TeamRegistration registration = teamRegistrationRepository
                .findByTeamIdAndHackathonId(teamId, hackathonId)
                .orElseThrow(() -> new IllegalArgumentException("Team not registered to this hackathon"));
        if (registration.isExpelled()) {
            throw new IllegalStateException("Cannot set winner for expelled team");
        }

        Submission winnerSubmission = submissionRepository.findByRegistrationId(registration.getRegistrationId())
                .orElseThrow(() -> new IllegalStateException(
                        "Cannot set winner: selected team has no submission"
                ));
        evaluationRepository.findBySubmissionId(winnerSubmission.getSubmissionId())
                .orElseThrow(() -> new IllegalStateException(
                        "Cannot set winner: selected team submission not evaluated"
                ));

        // Prima del winner tutti i team non espulsi devono avere submission e valutazione.
        ensureAllTeamsEvaluated(hackathonId);

        if (hackathon.getPrizeAmount() == null) {
            throw new IllegalStateException("Prize amount not configured");
        }

        Team winnerTeam = teamRepository.findById(teamId)
                .orElseThrow(() -> new IllegalStateException("Winner team not found"));
        String winnerTeamName = winnerTeam.getTeamName();
        if (winnerTeamName == null || winnerTeamName.trim().isEmpty()) {
            throw new IllegalStateException("Winner team name not configured");
        }

        // Se il pagamento fallisce non salviamo winner/prizePaid.
        boolean paid = paymentSystem.payPrize(hackathon.getPrizeAmount(), winnerTeamName);
        if (!paid) {
            String paymentError = paymentSystem.getLastErrorMessage();
            if (paymentError != null && !paymentError.trim().isEmpty()) {
                throw new IllegalStateException("PAYMENT_FAILED: " + paymentError);
            }
            throw new IllegalStateException("PAYMENT_FAILED: pagamento del premio non riuscito");
        }

        hackathon.setWinnerTeamId(teamId);
        hackathon.markPrizePaid();
        return hackathonRepository.save(hackathon);
    }

    // Eroga il premio se il winner e' gia' stato deciso.
    public String payPrize(long currentStaffId, long hackathonId) {
        Hackathon hackathon = hackathonRepository.findById(hackathonId)
                .orElseThrow(() -> new IllegalArgumentException("Hackathon not found"));
        ensureOrganizer(currentStaffId, hackathonId, "Not authorized: only organizer can manage prize");

        if (hackathon.isPrizePaid()) {
            return "Premio gia erogato: teamId=" + hackathon.getWinnerTeamId()
                    + ", amount=" + hackathon.getPrizeAmount();
        }
        // Il pagamento manuale e' ammesso solo dopo la chiusura.
        if (!hackathon.isClosed()) {
            throw new IllegalStateException("Hackathon must be CLOSED");
        }
        Long winnerTeamId = hackathon.getWinnerTeamId();
        if (winnerTeamId == null) {
            throw new IllegalStateException("Winner not set");
        }

        teamRegistrationRepository.findByTeamIdAndHackathonId(winnerTeamId, hackathonId)
                .orElseThrow(() -> new IllegalStateException("Winner team not registered to this hackathon"));
        if (hackathon.getPrizeAmount() == null) {
            throw new IllegalStateException("Prize amount not configured");
        }

        Team winnerTeam = teamRepository.findById(winnerTeamId)
                .orElseThrow(() -> new IllegalStateException("Winner team not found"));
        String winnerTeamName = winnerTeam.getTeamName();
        if (winnerTeamName == null || winnerTeamName.trim().isEmpty()) {
            throw new IllegalStateException("Winner team name not configured");
        }

        boolean paid = paymentSystem.payPrize(hackathon.getPrizeAmount(), winnerTeamName);
        if (paid) {
            hackathon.markPrizePaid();
            hackathonRepository.save(hackathon);
            return "Pagamento premio completato: teamId=" + winnerTeamId
                    + ", teamName=" + winnerTeamName
                    + ", amount=" + hackathon.getPrizeAmount();
        }
        return "Pagamento premio fallito: teamId=" + winnerTeamId
                + ", teamName=" + winnerTeamName
                + ", amount=" + hackathon.getPrizeAmount();
    }

    // Verifica che lo staff sia organizer dell'hackathon.
    private void ensureOrganizer(long currentStaffId, long hackathonId, String notAuthorizedMessage) {
        boolean isOrganizer = staffAssignmentRepository.findByHackathonIdAndRole(hackathonId, StaffRole.ORGANIZER)
                .stream()
                .anyMatch(assignment -> assignment.getStaffId() == currentStaffId);
        if (!isOrganizer) {
            throw new IllegalArgumentException(notAuthorizedMessage);
        }
    }

    // Prima di proclamare il winner controlla che tutti i team attivi siano valutati.
    private void ensureAllTeamsEvaluated(long hackathonId) {
        List<TeamRegistration> registrations = teamRegistrationRepository.findByHackathonId(hackathonId);
        for (TeamRegistration registration : registrations) {
            if (registration.isExpelled()) {
                continue;
            }
            Submission submission = submissionRepository.findByRegistrationId(registration.getRegistrationId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Cannot set winner: team " + registration.getTeamId() + " has no submission"
                    ));
            Evaluation evaluation = evaluationRepository.findBySubmissionId(submission.getSubmissionId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Cannot set winner: submission " + submission.getSubmissionId() + " not evaluated"
                    ));
            if (evaluation.getEvaluationId() <= 0) {
                throw new IllegalStateException(
                        "Cannot set winner: submission " + submission.getSubmissionId() + " not evaluated"
                );
            }
        }
    }

    // Permesso creazione: organizer gia' assegnato o caso iniziale senza organizer.
    private void ensureCanCreateHackathon(long currentStaffId) {
        staffMemberRepository.findById(currentStaffId)
                .orElseThrow(() -> new IllegalArgumentException("Organizer non trovato"));

        boolean isOrganizer = staffAssignmentRepository.findByStaffId(currentStaffId).stream()
                .anyMatch(assignment -> assignment.getRole() == StaffRole.ORGANIZER);
        if (isOrganizer) {
            return;
        }

        // Eccezione iniziale: il primo organizer puo' creare il primo hackathon.
        boolean anyOrganizerExists = hackathonRepository.findAll().stream()
                .map(Hackathon::getHackathonId)
                .anyMatch(hackathonId ->
                        !staffAssignmentRepository.findByHackathonIdAndRole(hackathonId, StaffRole.ORGANIZER).isEmpty()
                );
        if (anyOrganizerExists) {
            throw new IllegalArgumentException("Not authorized: only organizer can create hackathon");
        }
    }

    // Controlla se lo staff ha almeno un'assegnazione con quel ruolo.
    private boolean hasAnyAssignmentWithRole(long staffId, StaffRole role) {
        return staffAssignmentRepository.findByStaffId(staffId).stream()
                .anyMatch(assignment -> assignment.getRole() == role);
    }

    private String safe(String value) {
        return value == null ? "-" : value;
    }

    public record StaffSelectView(long staffId, String username, String name) {
    }
}
