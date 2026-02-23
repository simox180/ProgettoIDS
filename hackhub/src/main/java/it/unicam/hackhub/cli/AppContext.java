package it.unicam.hackhub.cli;

import it.unicam.hackhub.controller.AuthController;
import it.unicam.hackhub.controller.EvaluationController;
import it.unicam.hackhub.controller.HackathonController;
import it.unicam.hackhub.controller.MentorManagementController;
import it.unicam.hackhub.controller.SubmissionAccessController;
import it.unicam.hackhub.controller.SubmissionController;
import it.unicam.hackhub.controller.SupportController;
import it.unicam.hackhub.controller.TeamController;
import it.unicam.hackhub.controller.TeamRegistrationController;
import it.unicam.hackhub.controller.ViolationReportController;
import it.unicam.hackhub.external.CalendarSystem;
import it.unicam.hackhub.external.CalendarSystemStub;
import it.unicam.hackhub.external.PaymentSystem;
import it.unicam.hackhub.external.PaymentSystemStub;
import it.unicam.hackhub.model.enums.StaffRole;
import it.unicam.hackhub.repository.CallProposalRepository;
import it.unicam.hackhub.repository.EvaluationRepository;
import it.unicam.hackhub.repository.HackathonRepository;
import it.unicam.hackhub.repository.InvitationRepository;
import it.unicam.hackhub.repository.StaffAssignmentRepository;
import it.unicam.hackhub.repository.StaffMemberRepository;
import it.unicam.hackhub.repository.SubmissionRepository;
import it.unicam.hackhub.repository.SupportRequestRepository;
import it.unicam.hackhub.repository.TeamRegistrationRepository;
import it.unicam.hackhub.repository.TeamRepository;
import it.unicam.hackhub.repository.UserRepository;
import it.unicam.hackhub.repository.ViolationReportRepository;
import it.unicam.hackhub.repository.inmemory.InMemoryCallProposalRepository;
import it.unicam.hackhub.repository.inmemory.InMemoryEvaluationRepository;
import it.unicam.hackhub.repository.inmemory.InMemoryHackathonRepository;
import it.unicam.hackhub.repository.inmemory.InMemoryInvitationRepository;
import it.unicam.hackhub.repository.inmemory.InMemoryStaffAssignmentRepository;
import it.unicam.hackhub.repository.inmemory.InMemoryStaffMemberRepository;
import it.unicam.hackhub.repository.inmemory.InMemorySubmissionRepository;
import it.unicam.hackhub.repository.inmemory.InMemorySupportRequestRepository;
import it.unicam.hackhub.repository.inmemory.InMemoryTeamRegistrationRepository;
import it.unicam.hackhub.repository.inmemory.InMemoryTeamRepository;
import it.unicam.hackhub.repository.inmemory.InMemoryUserRepository;
import it.unicam.hackhub.repository.inmemory.InMemoryViolationReportRepository;

import java.util.EnumSet;
import java.util.Set;
import java.util.Scanner;

public class AppContext {
    private final UserRepository userRepository;
    private final StaffMemberRepository staffMemberRepository;
    private final TeamRepository teamRepository;
    private final InvitationRepository invitationRepository;
    private final HackathonRepository hackathonRepository;
    private final TeamRegistrationRepository teamRegistrationRepository;
    private final SubmissionRepository submissionRepository;
    private final EvaluationRepository evaluationRepository;
    private final StaffAssignmentRepository staffAssignmentRepository;
    private final SupportRequestRepository supportRequestRepository;
    private final CallProposalRepository callProposalRepository;
    private final ViolationReportRepository violationReportRepository;

    private final CalendarSystem calendarSystem;
    private final PaymentSystem paymentSystem;

    private final AuthController authController;
    private final TeamController teamController;
    private final HackathonController hackathonController;
    private final MentorManagementController mentorManagementController;
    private final TeamRegistrationController teamRegistrationController;
    private final SubmissionController submissionController;
    private final SubmissionAccessController submissionAccessController;
    private final EvaluationController evaluationController;
    private final SupportController supportController;
    private final ViolationReportController violationReportController;

    private final DataSeeder dataSeeder;
    private final SessionContext sessionContext;

    public AppContext() {
        // Wiring manuale: qui costruiamo a mano le dipendenze; con Spring sarebbe demandato alla DI.
        this.userRepository = new InMemoryUserRepository();
        this.staffMemberRepository = new InMemoryStaffMemberRepository();
        this.teamRepository = new InMemoryTeamRepository();
        this.invitationRepository = new InMemoryInvitationRepository();
        this.hackathonRepository = new InMemoryHackathonRepository();
        this.teamRegistrationRepository = new InMemoryTeamRegistrationRepository();
        this.submissionRepository = new InMemorySubmissionRepository();
        this.evaluationRepository = new InMemoryEvaluationRepository();
        this.staffAssignmentRepository = new InMemoryStaffAssignmentRepository();
        this.supportRequestRepository = new InMemorySupportRequestRepository();
        this.callProposalRepository = new InMemoryCallProposalRepository();
        this.violationReportRepository = new InMemoryViolationReportRepository();

        this.calendarSystem = new CalendarSystemStub();
        this.paymentSystem = new PaymentSystemStub();

        this.authController = new AuthController(userRepository, staffMemberRepository, staffAssignmentRepository);
        this.teamController = new TeamController(
                teamRepository,
                invitationRepository,
                userRepository,
                teamRegistrationRepository,
                hackathonRepository
        );
        this.hackathonController = new HackathonController(
                hackathonRepository,
                staffAssignmentRepository,
                staffMemberRepository,
                teamRegistrationRepository,
                submissionRepository,
                evaluationRepository,
                teamRepository,
                paymentSystem
        );
        this.mentorManagementController = new MentorManagementController(
                hackathonRepository,
                staffMemberRepository,
                staffAssignmentRepository
        );
        this.teamRegistrationController = new TeamRegistrationController(
                teamRepository,
                userRepository,
                hackathonRepository,
                teamRegistrationRepository
        );
        this.submissionController = new SubmissionController(
                teamRegistrationRepository,
                hackathonRepository,
                submissionRepository,
                userRepository
        );
        this.submissionAccessController = new SubmissionAccessController(
                hackathonRepository,
                staffAssignmentRepository,
                teamRegistrationRepository,
                submissionRepository
        );
        this.evaluationController = new EvaluationController(
                staffAssignmentRepository,
                hackathonRepository,
                evaluationRepository,
                submissionRepository,
                teamRegistrationRepository
        );
        this.supportController = new SupportController(
                supportRequestRepository,
                callProposalRepository,
                userRepository,
                teamRegistrationRepository,
                hackathonRepository,
                staffAssignmentRepository,
                calendarSystem
        );
        this.violationReportController = new ViolationReportController(
                hackathonRepository,
                staffAssignmentRepository,
                teamRepository,
                teamRegistrationRepository,
                violationReportRepository
        );

        this.dataSeeder = new DataSeeder(staffMemberRepository, hackathonRepository, staffAssignmentRepository);
        this.sessionContext = new SessionContext();
    }

    public void init() {
        // Punto unico di bootstrap runtime.
        dataSeeder.seed();
    }

    public CommandRegistry createCommandRegistry(Scanner scanner) {
        CommandRegistry registry = new CommandRegistry();
        InputHelper inputHelper = new InputHelper(scanner);

        RegisterCommand registerCommand = new RegisterCommand(authController, sessionContext, inputHelper);
        LoginCommand loginCommand = new LoginCommand(
                authController,
                sessionContext,
                inputHelper,
                registerCommand
        );
        LogoutCommand logoutCommand = new LogoutCommand(sessionContext);

        EnumSet<CommandRegistry.Audience> guestOnly = EnumSet.of(CommandRegistry.Audience.GUEST);
        EnumSet<CommandRegistry.Audience> userOnly = EnumSet.of(CommandRegistry.Audience.USER);
        EnumSet<CommandRegistry.Audience> staffOnly = EnumSet.of(CommandRegistry.Audience.STAFF);
        EnumSet<CommandRegistry.Audience> userAndStaff = EnumSet.of(
                CommandRegistry.Audience.USER,
                CommandRegistry.Audience.STAFF
        );
        EnumSet<CommandRegistry.Audience> allAudiences = EnumSet.of(
                CommandRegistry.Audience.GUEST,
                CommandRegistry.Audience.USER,
                CommandRegistry.Audience.STAFF
        );

        registry.register(registerCommand, guestOnly, Set.of());
        registry.register(loginCommand, guestOnly, Set.of());
        registry.register(logoutCommand, userAndStaff, Set.of());
        registry.register(new ListHackathonsCommand(hackathonController), allAudiences, Set.of());
        registry.register(new ViewHackathonCommand(hackathonController, inputHelper), allAudiences, Set.of());
        registry.register(
                new CreateHackathonCommand(hackathonController, sessionContext, inputHelper),
                staffOnly,
                Set.of(StaffRole.ORGANIZER)
        );
        registry.register(
                new AddMentorCommand(
                        mentorManagementController,
                        sessionContext,
                        inputHelper
                ),
                staffOnly,
                Set.of(StaffRole.ORGANIZER)
        );

        registry.register(
                new AdvanceHackathonCommand(hackathonController, sessionContext, inputHelper),
                staffOnly,
                Set.of(StaffRole.ORGANIZER)
        );
        registry.register(
                new SetWinnerCommand(hackathonController, sessionContext, inputHelper),
                staffOnly,
                Set.of(StaffRole.ORGANIZER)
        );
        registry.register(
                new PayPrizeCommand(hackathonController, sessionContext, inputHelper),
                staffOnly,
                Set.of(StaffRole.ORGANIZER)
        );
        registry.register(
                new ProclaimWinnerCommand(hackathonController, sessionContext, inputHelper),
                staffOnly,
                Set.of(StaffRole.ORGANIZER)
        );
        registry.register(
                new ListSubmissionsCommand(
                        submissionAccessController,
                        sessionContext,
                        inputHelper
                ),
                staffOnly,
                Set.of()
        );
        registry.register(
                new EvaluateSubmissionCommand(
                        submissionAccessController,
                        evaluationController,
                        sessionContext,
                        inputHelper
                ),
                staffOnly,
                Set.of(StaffRole.JUDGE)
        );
        registry.register(new ViewEvaluationCommand(
                submissionAccessController,
                evaluationController,
                sessionContext,
                inputHelper
        ), staffOnly, Set.of());
        registry.register(
                new CreateSupportRequestCommand(
                        supportController,
                        sessionContext,
                        inputHelper
                ),
                userOnly,
                Set.of()
        );
        registry.register(
                new CreateCallProposalCommand(supportController, sessionContext, inputHelper),
                staffOnly,
                Set.of(StaffRole.MENTOR)
        );
        registry.register(
                new ListSupportRequestsCommand(supportController, sessionContext, inputHelper),
                staffOnly,
                Set.of(StaffRole.MENTOR)
        );
        registry.register(
                new ListCallProposalsCommand(
                        supportController,
                        sessionContext
                ),
                userOnly,
                Set.of()
        );
        registry.register(
                new BookCallCommand(
                        supportController,
                        sessionContext,
                        inputHelper
                ),
                userOnly,
                Set.of()
        );
        registry.register(
                new CreateReportCommand(
                        violationReportController,
                        sessionContext,
                        inputHelper
                ),
                staffOnly,
                Set.of(StaffRole.MENTOR)
        );
        registry.register(
                new ListReportsCommand(
                        violationReportController,
                        sessionContext,
                        inputHelper
                ),
                staffOnly,
                Set.of(StaffRole.ORGANIZER)
        );
        registry.register(
                new ManageReportCommand(violationReportController, sessionContext, inputHelper),
                staffOnly,
                Set.of(StaffRole.ORGANIZER)
        );
        registry.register(new RegisterTeamCommand(
                teamRegistrationController,
                sessionContext,
                inputHelper
        ), userOnly, Set.of());
        registry.register(new MyRegistrationCommand(teamRegistrationController, teamController, sessionContext), userOnly, Set.of());
        registry.register(new SubmitCommand(submissionController, teamController, sessionContext, inputHelper), userOnly, Set.of());
        registry.register(
                new UpdateSubmissionCommand(
                        submissionController,
                        teamController,
                        sessionContext,
                        inputHelper
                ),
                userOnly,
                Set.of()
        );
        registry.register(new MySubmissionCommand(submissionController, teamController, sessionContext), userOnly, Set.of());
        registry.register(new CreateTeamCommand(teamController, sessionContext, inputHelper), userOnly, Set.of());
        registry.register(new InviteUserCommand(teamController, sessionContext, inputHelper), userOnly, Set.of());
        registry.register(
                new ViewInvitesCommand(
                        teamController,
                        sessionContext,
                        inputHelper
                ),
                userOnly,
                Set.of()
        );

        return registry;
    }

    public AuthController getAuthController() { return authController; }
    public TeamController getTeamController() { return teamController; }
    public HackathonController getHackathonController() { return hackathonController; }
    public TeamRegistrationController getTeamRegistrationController() { return teamRegistrationController; }
    public SubmissionController getSubmissionController() { return submissionController; }
    public EvaluationController getEvaluationController() { return evaluationController; }
    public SupportController getSupportController() { return supportController; }
    public CalendarSystem getCalendarSystem() { return calendarSystem; }
    public SessionContext getSessionContext() { return sessionContext; }
}
