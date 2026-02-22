package it.unicam.hackhub.external;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

@Component
public class PaymentSystemStub implements PaymentSystem {
    private String lastErrorMessage;
    private String lastReceiptId;
    private final AtomicLong seq = new AtomicLong(1);

    @Override
    public boolean payPrize(BigDecimal amount, String toTeamName) {
        lastErrorMessage = null;
        lastReceiptId = null;

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0
                || toTeamName == null || toTeamName.trim().isBlank()) {
            lastErrorMessage = "Pagamento non valido";
            return false;
        }

        long n = seq.getAndIncrement();
        lastReceiptId = String.format("PAY-%06d", n);
        return true;
    }

    @Override
    public String getLastErrorMessage() {
        return lastErrorMessage;
    }

    @Override
    public String getLastReceiptId() {
        return lastReceiptId;
    }
}
