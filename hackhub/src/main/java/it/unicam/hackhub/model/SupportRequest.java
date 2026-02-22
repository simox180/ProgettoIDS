package it.unicam.hackhub.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "support_requests")
public class SupportRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long requestId;
    private long teamId;
    private long hackathonId;
    private String message;
    private LocalDateTime createdAt;

    public SupportRequest() {
    }

    public SupportRequest(long requestId, long teamId, long hackathonId, String message, LocalDateTime createdAt) {
        if (requestId < 0) {
            throw new IllegalArgumentException("Request id non valido");
        }
        if (teamId <= 0) {
            throw new IllegalArgumentException("Team id non valido");
        }
        if (hackathonId <= 0) {
            throw new IllegalArgumentException("Hackathon id non valido");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("Messaggio non valido");
        }
        this.requestId = requestId;
        this.teamId = teamId;
        this.hackathonId = hackathonId;
        this.message = message.trim();
        this.createdAt = createdAt;
    }

    public long getRequestId() { return requestId; }
    public void setRequestId(long requestId) { this.requestId = requestId; }
    public long getTeamId() { return teamId; }
    public void setTeamId(long teamId) { this.teamId = teamId; }
    public long getHackathonId() { return hackathonId; }
    public void setHackathonId(long hackathonId) { this.hackathonId = hackathonId; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
