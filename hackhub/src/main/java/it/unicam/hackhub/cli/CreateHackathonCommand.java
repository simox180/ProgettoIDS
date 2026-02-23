package it.unicam.hackhub.cli;

import it.unicam.hackhub.controller.HackathonController;
import it.unicam.hackhub.model.Hackathon;
import it.unicam.hackhub.model.enums.StaffRole;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class CreateHackathonCommand implements Command {
    private final HackathonController hackathonController;
    private final SessionContext sessionContext;
    private final InputHelper inputHelper;

    public CreateHackathonCommand(HackathonController hackathonController,
                                  SessionContext sessionContext,
                                  InputHelper inputHelper) {
        this.hackathonController = hackathonController;
        this.sessionContext = sessionContext;
        this.inputHelper = inputHelper;
    }

    @Override
    public String name() {
        return "create-hackathon";
    }

    @Override
    public void execute() {
        try {
            if (!sessionContext.isStaffLoggedIn()) {
                System.out.println("Devi effettuare login STAFF per creare un hackathon.");
                return;
            }
            if (!sessionContext.getStaffRoles().contains(StaffRole.ORGANIZER)) {
                System.out.println("Comando disponibile solo per ORGANIZER.");
                return;
            }

            Optional<Long> currentStaffId = sessionContext.getCurrentStaffId();
            if (currentStaffId.isEmpty()) {
                System.out.println("Sessione staff non valida.");
                return;
            }

            String name = inputHelper.readNonBlank("Nome hackathon");
            String regulation = inputHelper.readLine("Regolamento");
            LocalDate startDate = readDate("Start date (yyyy-MM-dd)");
            LocalDate endDate = readEndDateAfterStart(startDate);
            LocalDate regDeadline = readRegistrationDeadlineBeforeStart(startDate);
            LocalDate submissionDeadline = readSubmissionDeadlineInWindow(startDate, endDate);
            String location = inputHelper.readNonBlank("Location");
            double prizeAmount = readNonNegativeDouble("Prize amount");
            int maxTeamSize = readPositiveInt("Max team size");

            List<HackathonController.StaffSelectView> selectableJudges =
                    hackathonController.listSelectableStaffByRole(currentStaffId.get(), StaffRole.JUDGE);
            if (selectableJudges.isEmpty()) {
                System.out.println("Nessun judge selezionabile disponibile.");
                return;
            }

            Set<Long> selectableJudgeIds = new LinkedHashSet<>();
            List<List<String>> judgeRows = new ArrayList<>();
            for (HackathonController.StaffSelectView staff : selectableJudges) {
                long staffId = staff.staffId();
                selectableJudgeIds.add(staffId);
                judgeRows.add(List.of(
                        String.valueOf(staffId),
                        staff.username(),
                        staff.name()
                ));
            }
            TablePrinter.print(List.of("STAFF_ID", "USERNAME", "NAME"), judgeRows);
            long judgeStaffId = readJudgeId("Judge id", selectableJudgeIds);

            List<HackathonController.StaffSelectView> selectableMentors =
                    hackathonController.listSelectableStaffByRole(currentStaffId.get(), StaffRole.MENTOR);
            Set<Long> selectableMentorIds = new LinkedHashSet<>();
            List<List<String>> mentorRows = new ArrayList<>();
            for (HackathonController.StaffSelectView staff : selectableMentors) {
                long staffId = staff.staffId();
                if (staffId == judgeStaffId) {
                    continue;
                }
                selectableMentorIds.add(staffId);
                mentorRows.add(List.of(
                        String.valueOf(staffId),
                        staff.username(),
                        staff.name()
                ));
            }
            if (selectableMentorIds.isEmpty()) {
                System.out.println("Nessun mentor selezionabile disponibile.");
                return;
            }
            TablePrinter.print(List.of("STAFF_ID", "USERNAME", "NAME"), mentorRows);
            List<Long> mentorStaffIds = readStaffIdsCsv("Mentor ids (comma separated)", selectableMentorIds);

            Hackathon created = hackathonController.createHackathon(
                    currentStaffId.get(),
                    name,
                    regulation,
                    regDeadline,
                    startDate,
                    endDate,
                    submissionDeadline,
                    location,
                    prizeAmount,
                    maxTeamSize,
                    judgeStaffId,
                    mentorStaffIds
            );

            System.out.println(
                    "Hackathon creato. hackathonId=" + created.getHackathonId()
                            + " | organizer/judge/mentor assegnati."
            );
        } catch (OperationCancelledException ex) {
            System.out.println("Operazione annullata.");
        } catch (IllegalArgumentException ex) {
            System.out.println("Errore creazione hackathon: " + ex.getMessage());
        }
    }

    private LocalDate readDate(String prompt) {
        while (true) {
            String raw = inputHelper.readNonBlank(prompt).trim();
            try {
                return LocalDate.parse(raw);
            } catch (DateTimeParseException ex) {
                System.out.println("Formato data non valido. Usa yyyy-MM-dd");
            }
        }
    }

    private LocalDate readEndDateAfterStart(LocalDate startDate) {
        while (true) {
            LocalDate endDate = readDate("End date (yyyy-MM-dd)");
            if (!endDate.isAfter(startDate)) {
                System.out.println("End date deve essere dopo Start date");
                continue;
            }
            return endDate;
        }
    }

    private LocalDate readRegistrationDeadlineBeforeStart(LocalDate startDate) {
        while (true) {
            LocalDate registrationDeadline = readDate("Registration deadline (yyyy-MM-dd)");
            if (!registrationDeadline.isBefore(startDate)) {
                System.out.println("Registration deadline deve essere prima dello Start date");
                continue;
            }
            return registrationDeadline;
        }
    }

    private LocalDate readSubmissionDeadlineInWindow(LocalDate startDate, LocalDate endDate) {
        while (true) {
            LocalDate submissionDeadline = readDate("Submission deadline (yyyy-MM-dd)");
            if (submissionDeadline.isBefore(startDate) || submissionDeadline.isAfter(endDate)) {
                System.out.println("Submission deadline deve essere compresa tra Start date ed End date");
                continue;
            }
            return submissionDeadline;
        }
    }

    private double readNonNegativeDouble(String prompt) {
        while (true) {
            String raw = inputHelper.readNonBlank(prompt).trim();
            try {
                double value = Double.parseDouble(raw);
                if (Double.isNaN(value) || Double.isInfinite(value) || value < 0) {
                    System.out.println("Valore non valido.");
                    continue;
                }
                return value;
            } catch (NumberFormatException ex) {
                System.out.println("Valore numerico non valido.");
            }
        }
    }

    private int readPositiveInt(String prompt) {
        while (true) {
            long raw = inputHelper.readLong(prompt);
            if (raw <= 0 || raw > Integer.MAX_VALUE) {
                System.out.println("Valore non valido.");
                continue;
            }
            return (int) raw;
        }
    }

    private long readJudgeId(String prompt, Set<Long> allowedIds) {
        while (true) {
            long staffId = inputHelper.readLong(prompt);
            if (allowedIds.contains(staffId)) {
                return staffId;
            }
            System.out.println("ID judge non valido");
        }
    }

    private List<Long> readStaffIdsCsv(String prompt, Set<Long> allowedIds) {
        while (true) {
            String raw = inputHelper.readNonBlank(prompt).trim();
            String[] tokens = raw.split(",");
            Set<Long> selected = new LinkedHashSet<>();
            boolean valid = true;
            for (String token : tokens) {
                String cleaned = token.trim();
                if (cleaned.isEmpty()) {
                    valid = false;
                    break;
                }
                long staffId;
                try {
                    staffId = Long.parseLong(cleaned);
                } catch (NumberFormatException ex) {
                    valid = false;
                    break;
                }
                if (!allowedIds.contains(staffId)) {
                    valid = false;
                    break;
                }
                selected.add(staffId);
            }

            if (!valid || selected.isEmpty()) {
                System.out.println("Mentor ids non validi.");
                continue;
            }
            return new ArrayList<>(selected);
        }
    }

}
