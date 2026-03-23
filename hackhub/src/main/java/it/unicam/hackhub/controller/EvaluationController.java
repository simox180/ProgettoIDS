package it.unicam.hackhub.controller;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import it.unicam.hackhub.model.Evaluation;
import it.unicam.hackhub.model.Hackathon;
import it.unicam.hackhub.model.StaffAssignment;
import it.unicam.hackhub.model.Submission;
import it.unicam.hackhub.model.TeamRegistration;
import it.unicam.hackhub.model.enums.StaffRole;
import it.unicam.hackhub.repository.EvaluationRepository;
import it.unicam.hackhub.repository.HackathonRepository;
import it.unicam.hackhub.repository.StaffAssignmentRepository;
import it.unicam.hackhub.repository.SubmissionRepository;
import it.unicam.hackhub.repository.TeamRegistrationRepository;

@Service
public class EvaluationController {
    private final StaffAssignmentRepository staffAssignmentRepository;
    private final HackathonRepository hackathonRepository;
    private final EvaluationRepository evaluationRepository;
    private final SubmissionRepository submissionRepository;
    private final TeamRegistrationRepository teamRegistrationRepository;

    public EvaluationController(StaffAssignmentRepository staffAssignmentRepository,
                                HackathonRepository hackathonRepository,
                                EvaluationRepository evaluationRepository,
                                SubmissionRepository submissionRepository,
                                TeamRegistrationRepository teamRegistrationRepository) {
        this.staffAssignmentRepository = staffAssignmentRepository;
        this.hackathonRepository = hackathonRepository;
        this.evaluationRepository = evaluationRepository;
        this.submissionRepository = submissionRepository;
        this.teamRegistrationRepository = teamRegistrationRepository;
    }

    // Recupera la valutazione della submission se lo staff e' assegnato all'hackathon.
    public Optional<EvaluationView> viewEvaluation(long currentStaffId, long submissionId) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new IllegalArgumentException("Submission non trovata."));
        TeamRegistration registration = teamRegistrationRepository.findById(submission.getRegistrationId())
                .orElseThrow(() -> new IllegalArgumentException("Registrazione non trovata."));

        List<StaffAssignment> assignments = staffAssignmentRepository.findByHackathonId(registration.getHackathonId());
        boolean assigned = assignments.stream().anyMatch(assignment -> assignment.getStaffId() == currentStaffId);
        if (!assigned) {
            throw new IllegalArgumentException("Non autorizzato: non sei assegnato a questo hackathon.");
        }

        return evaluationRepository.findBySubmissionId(submissionId)
                .map(evaluation -> new EvaluationView(
                        evaluation.getEvaluationId(),
                        evaluation.getSubmissionId(),
                        evaluation.getScore(),
                        evaluation.getComment(),
                        evaluation.getEvaluatedAt()
                ));
    }

    // Controlla che la submission possa essere valutata dal judge corrente.
    public void assertEvaluatable(long currentStaffId, long submissionId) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new IllegalArgumentException("Submission non trovata"));

        TeamRegistration registration = teamRegistrationRepository.findById(submission.getRegistrationId())
                .orElseThrow(() -> new IllegalStateException("Team registration not found"));
        long hackathonId = registration.getHackathonId();

        // Solo i judge assegnati possono valutare.
        boolean isJudge = staffAssignmentRepository.findByHackathonIdAndRole(hackathonId, StaffRole.JUDGE)
                .stream()
                .anyMatch(assignment -> assignment.getStaffId() == currentStaffId);
        if (!isJudge) {
            throw new IllegalArgumentException("Not authorized: only judge can evaluate");
        }

        Hackathon hackathon = hackathonRepository.findById(hackathonId)
                .orElseThrow(() -> new IllegalArgumentException("Hackathon not found"));
        // La valutazione e' ammessa solo nella fase REVIEW.
        if (!hackathon.canEvaluate()) {
            throw new IllegalStateException("Hackathon not in REVIEW");
        }
        // Da qui in poi il team non puo' piu' essere valutato se espulso.
        if (registration.isExpelled()) {
            throw new IllegalStateException("Cannot evaluate expelled team submission");
        }
    }

    // Verifica rapida usata quando il client ha gia' hackathon e judge.
    public void assertHackathonInReviewForJudge(long currentStaffId, long hackathonId) {
        boolean isJudge = staffAssignmentRepository.findByHackathonIdAndRole(hackathonId, StaffRole.JUDGE)
                .stream()
                .anyMatch(assignment -> assignment.getStaffId() == currentStaffId);
        if (!isJudge) {
            throw new IllegalArgumentException("Non sei giudice assegnato a questo hackathon.");
        }

        Hackathon hackathon = hackathonRepository.findById(hackathonId)
                .orElseThrow(() -> new IllegalArgumentException("Hackathon not found"));
        if (!hackathon.canEvaluate()) {
            throw new IllegalStateException("Hackathon not in REVIEW");
        }
    }

    // Salva il voto: aggiorna quello esistente oppure ne crea uno nuovo.
    public Evaluation evaluateSubmission(long currentStaffId, long submissionId, int score, String comment) {
        if (score < 0 || score > 10) {
            throw new IllegalArgumentException("Score must be between 0 and 10");
        }

        assertEvaluatable(currentStaffId, submissionId);

        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new IllegalArgumentException("Submission non trovata"));

        LocalDateTime now = LocalDateTime.now();
        // Aggiorno la valutazione esistente, altrimenti ne creo una nuova.
        Evaluation evaluation = evaluationRepository.findBySubmissionId(submissionId)
                .map(existing -> {
                    existing.setScore(score);
                    existing.setComment(comment);
                    existing.setEvaluatedAt(now);
                    return existing;
                })
                .orElseGet(() -> new Evaluation(
                        0L,
                        submissionId,
                        score,
                        comment,
                        now
                ));

        return evaluationRepository.save(evaluation);
    }

    // Recupera la valutazione legata alla submission, se presente.
    public Optional<Evaluation> findBySubmissionId(long submissionId) {
        return evaluationRepository.findBySubmissionId(submissionId);
    }

    public record EvaluationView(long evaluationId,
                                 long submissionId,
                                 int score,
                                 String comment,
                                 LocalDateTime evaluatedAt) {
    }
}
