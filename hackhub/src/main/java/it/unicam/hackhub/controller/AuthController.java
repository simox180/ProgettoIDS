package it.unicam.hackhub.controller;

import it.unicam.hackhub.controller.auth.LoginResult;
import it.unicam.hackhub.model.StaffAssignment;
import it.unicam.hackhub.model.StaffMember;
import it.unicam.hackhub.model.User;
import it.unicam.hackhub.model.enums.StaffRole;
import it.unicam.hackhub.repository.StaffAssignmentRepository;
import it.unicam.hackhub.repository.StaffMemberRepository;
import it.unicam.hackhub.repository.UserRepository;
import it.unicam.hackhub.security.PasswordHasher;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class AuthController {
    private final UserRepository userRepository;
    private final StaffMemberRepository staffMemberRepository;
    private final StaffAssignmentRepository staffAssignmentRepository;

    public AuthController(UserRepository userRepository,
                          StaffMemberRepository staffMemberRepository,
                          StaffAssignmentRepository staffAssignmentRepository) {
        this.userRepository = userRepository;
        this.staffMemberRepository = staffMemberRepository;
        this.staffAssignmentRepository = staffAssignmentRepository;
    }

    // Gestisce il login scegliendo il flusso USER o STAFF.
    public LoginResult login(String loginType, String identifier, String password) {
        if (isBlank(loginType) || isBlank(identifier) || isBlank(password)) {
            return LoginResult.invalidInput();
        }

        // USER e STAFF hanno archivi diversi, quindi qui separiamo subito i due flussi.
        if ("USER".equalsIgnoreCase(loginType)) {
            return loginUser(identifier, password);
        }
        if ("STAFF".equalsIgnoreCase(loginType)) {
            return loginStaff(identifier, password);
        }
        return LoginResult.invalidLoginType();
    }

    // Registra un nuovo utente applicativo con username univoco.
    public long registerUser(String userName, String password) {
        if (isBlank(userName) || isBlank(password)) {
            throw new IllegalArgumentException("Username and password are required");
        }
        // Evita account duplicati gia' in fase di registrazione.
        if (userRepository.findByUserName(userName).isPresent()) {
            throw new IllegalStateException("Username already used");
        }

        String passwordHash = PasswordHasher.hashPassword(password);
        User savedUser = userRepository.save(new User(0L, userName, passwordHash, null));
        return savedUser.getUserId();
    }

    // Raccoglie tutti i ruoli trovati nelle assegnazioni dello staff.
    public Set<StaffRole> loadStaffRoles(long staffId) {
        Set<StaffRole> roles = EnumSet.noneOf(StaffRole.class);
        // Uno staff puo' avere ruoli diversi in hackathon diversi: qui li raccogliamo tutti.
        for (StaffAssignment assignment : staffAssignmentRepository.findByStaffId(staffId)) {
            roles.add(assignment.getRole());
        }
        return roles;
    }

    private LoginResult loginUser(String userName, String password) {
        Optional<User> userOpt = userRepository.findByUserName(userName);
        if (userOpt.isEmpty()) {
            return LoginResult.userNotFound();
        }

        User user = userOpt.get();
        if (!PasswordHasher.verifyPassword(password, user.getPasswordHash())) {
            return LoginResult.invalidPassword();
        }
        return LoginResult.userAuthenticated(user.getUserId());
    }

    private LoginResult loginStaff(String username, String password) {
        Optional<StaffMember> staffOpt = staffMemberRepository.findByUsername(username);
        if (staffOpt.isEmpty()) {
            return LoginResult.staffNotFound();
        }

        StaffMember staff = staffOpt.get();
        if (!PasswordHasher.verifyPassword(password, staff.getPasswordHash())) {
            return LoginResult.invalidPassword();
        }
        return LoginResult.staffAuthenticated(staff.getStaffId());
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
