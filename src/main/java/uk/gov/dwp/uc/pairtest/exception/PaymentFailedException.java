package uk.gov.dwp.uc.pairtest.exception;

public class PaymentFailedException extends RuntimeException {
    public PaymentFailedException(Throwable cause) {
        super("Ticket payment failed", cause);
    }
}
