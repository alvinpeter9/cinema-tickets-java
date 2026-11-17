package uk.gov.dwp.uc.pairtest.exception;

public class SeatReservationFailedException extends RuntimeException {
    public SeatReservationFailedException(Throwable cause) {
        super("Seat reservation failed after payment", cause);
    }
}
