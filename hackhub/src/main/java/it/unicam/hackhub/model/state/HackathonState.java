package it.unicam.hackhub.model.state;

import it.unicam.hackhub.model.enums.HackathonStatus;

public interface HackathonState {
    boolean canRegister();

    boolean canSubmit();

    boolean canEvaluate();

    boolean isClosed();

    HackathonStatus status();

    HackathonStatus nextStatus(HackathonStatus target);
}
