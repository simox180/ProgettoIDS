package it.unicam.hackhub.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(
        name = "evaluations",
        uniqueConstraints = @UniqueConstraint(columnNames = {"submissionId"})
)
public class Evaluation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long evaluationId;
    private long submissionId;
    private int score;
    private String comment;
    private LocalDateTime evaluatedAt;

    public Evaluation() {
    }

    public Evaluation(long evaluationId, long submissionId, int score, String comment, LocalDateTime evaluatedAt) {
        this.evaluationId = evaluationId;
        this.submissionId = submissionId;
        this.score = score;
        this.comment = comment;
        this.evaluatedAt = evaluatedAt;
    }

    public long getEvaluationId() { return evaluationId; }
    public void setEvaluationId(long evaluationId) { this.evaluationId = evaluationId; }
    public long getSubmissionId() { return submissionId; }
    public void setSubmissionId(long submissionId) { this.submissionId = submissionId; }
    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public LocalDateTime getEvaluatedAt() { return evaluatedAt; }
    public void setEvaluatedAt(LocalDateTime evaluatedAt) { this.evaluatedAt = evaluatedAt; }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Evaluation other)) {
            return false;
        }
        return evaluationId == other.evaluationId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(evaluationId);
    }
}
