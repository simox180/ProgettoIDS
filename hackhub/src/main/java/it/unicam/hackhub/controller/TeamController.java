package it.unicam.hackhub.controller;

import it.unicam.hackhub.model.Hackathon;
import it.unicam.hackhub.model.Invitation;
import it.unicam.hackhub.model.Team;
import it.unicam.hackhub.model.TeamRegistration;
import it.unicam.hackhub.model.User;
import it.unicam.hackhub.model.enums.InvitationStatus;
import it.unicam.hackhub.repository.HackathonRepository;
import it.unicam.hackhub.repository.InvitationRepository;
import it.unicam.hackhub.repository.TeamRegistrationRepository;
import it.unicam.hackhub.repository.TeamRepository;
import it.unicam.hackhub.repository.UserRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class TeamController {
    private final TeamRepository teamRepository;
    private final InvitationRepository invitationRepository;
    private final UserRepository userRepository;
    private final TeamRegistrationRepository teamRegistrationRepository;
    private final HackathonRepository hackathonRepository;

    public TeamController(TeamRepository teamRepository,
                          InvitationRepository invitationRepository,
                          UserRepository userRepository,
                          TeamRegistrationRepository teamRegistrationRepository,
                          HackathonRepository hackathonRepository) {
        this.teamRepository = teamRepository;
        this.invitationRepository = invitationRepository;
        this.userRepository = userRepository;
        this.teamRegistrationRepository = teamRegistrationRepository;
        this.hackathonRepository = hackathonRepository;
    }

    public Team createTeam(long creatorUserId, String teamName) {
        User creator = userRepository.findById(creatorUserId)
                .orElseThrow(() -> new IllegalArgumentException("Creator user not found"));
        if (creator.getTeamId() != null) {
            throw new IllegalStateException("User already belongs to a team");
        }
        if (teamName == null || teamName.isBlank()) {
            throw new IllegalArgumentException("Nome team non valido");
        }
        String normalizedName = teamName.trim();
        if (teamRepository.findByName(normalizedName).isPresent()) {
            throw new IllegalArgumentException("Nome team gia in uso");
        }

        Team team = teamRepository.save(new Team(0L, normalizedName));
        creator.setTeamId(team.getTeamId());
        userRepository.save(creator);
        return team;
    }

    public Invitation inviteUser(long teamId, long invitedUserId) {
        if (teamRepository.findById(teamId).isEmpty()) {
            throw new IllegalArgumentException("Team not found");
        }

        User invitedUser = userRepository.findById(invitedUserId)
                .orElseThrow(() -> new IllegalArgumentException("Invited user not found"));
        if (invitedUser.getTeamId() != null) {
            throw new IllegalStateException("Invited user already in a team");
        }
        if (invitationRepository.findPendingByTeamAndUser(teamId, invitedUserId).isPresent()) {
            throw new IllegalStateException("Pending invitation already exists");
        }

        Invitation invitation = new Invitation(0L, teamId, invitedUserId, InvitationStatus.PENDING);
        return invitationRepository.save(invitation);
    }

    public Long getTeamIdOfUser(long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        return user.getTeamId();
    }

    public List<Invitation> viewInvites(long userId) {
        if (userRepository.findById(userId).isEmpty()) {
            throw new IllegalArgumentException("User not found");
        }
        return invitationRepository.findByInvitedUserId(userId);
    }

    public List<InvitationView> viewInvitesForUser(long userId) {
        if (userRepository.findById(userId).isEmpty()) {
            throw new IllegalArgumentException("User not found");
        }

        Map<Long, Integer> memberCountByTeam = buildMemberCountByTeam();
        return invitationRepository.findByInvitedUserId(userId).stream()
                .map(invitation -> {
                    long teamId = invitation.getTeamId();
                    String teamName = teamRepository.findById(teamId)
                            .map(Team::getTeamName)
                            .orElse("N/A");
                    String hackathonLabel = teamRegistrationRepository.findByTeamId(teamId)
                            .map(TeamRegistration::getHackathonId)
                            .map(hackathonId -> hackathonRepository.findById(hackathonId)
                                    .map(Hackathon::getHackathonName)
                                    .orElse("ID:" + hackathonId))
                            .orElse("N/A");

                    return new InvitationView(
                            invitation.getInvitationId(),
                            teamId,
                            teamName,
                            memberCountByTeam.getOrDefault(teamId, 0),
                            hackathonLabel,
                            String.valueOf(invitation.getStatus())
                    );
                })
                .toList();
    }

    public boolean acceptInvitation(long invitationId, long currentUserId) {
        Optional<Invitation> invitationOpt = invitationRepository.findById(invitationId);
        if (invitationOpt.isEmpty()) {
            return false;
        }

        Invitation invitation = invitationOpt.get();
        if (invitation.getStatus() != InvitationStatus.PENDING) {
            return false;
        }
        if (invitation.getInvitedUserId() != currentUserId) {
            return false;
        }

        Optional<User> invitedUserOpt = userRepository.findById(invitation.getInvitedUserId());
        if (invitedUserOpt.isEmpty()) {
            return false;
        }

        User invitedUser = invitedUserOpt.get();
        if (invitedUser.getTeamId() != null) {
            return false;
        }

        Optional<TeamRegistration> registrationOpt = teamRegistrationRepository.findByTeamId(invitation.getTeamId());
        if (registrationOpt.isPresent()) {
            TeamRegistration registration = registrationOpt.get();
            Hackathon hackathon = hackathonRepository.findById(registration.getHackathonId())
                    .orElseThrow(() -> new IllegalStateException("Hackathon associato al team non trovato."));
            int currentMembers = userRepository.findByTeamId(invitation.getTeamId()).size();
            if (currentMembers + 1 > hackathon.getMaxTeamSize()) {
                throw new IllegalStateException("Team pieno: supererebbe la dimensione massima dell'hackathon.");
            }
        }

        invitedUser.setTeamId(invitation.getTeamId());
        userRepository.save(invitedUser);
        invitation.setStatus(InvitationStatus.ACCEPTED);
        invitationRepository.save(invitation);
        return true;
    }

    public boolean declineInvitation(long invitationId, long currentUserId) {
        Optional<Invitation> invitationOpt = invitationRepository.findById(invitationId);
        if (invitationOpt.isEmpty()) {
            return false;
        }

        Invitation invitation = invitationOpt.get();
        if (invitation.getStatus() != InvitationStatus.PENDING) {
            return false;
        }
        if (invitation.getInvitedUserId() != currentUserId) {
            return false;
        }

        invitation.setStatus(InvitationStatus.DECLINED);
        invitationRepository.save(invitation);
        return true;
    }

    private Map<Long, Integer> buildMemberCountByTeam() {
        Map<Long, Integer> counts = new HashMap<>();
        for (User user : userRepository.findAll()) {
            Long teamId = user.getTeamId();
            if (teamId == null) {
                continue;
            }
            counts.put(teamId, counts.getOrDefault(teamId, 0) + 1);
        }
        return counts;
    }

    public record InvitationView(long invitationId,
                                 long teamId,
                                 String teamName,
                                 int members,
                                 String hackathon,
                                 String status) {
    }
}
