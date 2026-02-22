package it.unicam.hackhub.external;

import java.math.BigDecimal;

public interface PaymentSystem {
    boolean payPrize(BigDecimal amount, String toTeamName);

    default String getLastErrorMessage() {
        return null;
    }

    default String getLastReceiptId() {
        return null;
    }
}
