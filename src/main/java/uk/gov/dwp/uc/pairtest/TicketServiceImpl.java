package uk.gov.dwp.uc.pairtest;

import java.util.Objects;
import thirdparty.paymentgateway.TicketPaymentService;
import thirdparty.paymentgateway.TicketPaymentServiceImpl;
import thirdparty.seatbooking.SeatReservationService;
import thirdparty.seatbooking.SeatReservationServiceImpl;
import uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest;
import uk.gov.dwp.uc.pairtest.exception.InvalidPurchaseException;
import uk.gov.dwp.uc.pairtest.exception.PaymentFailedException;
import uk.gov.dwp.uc.pairtest.exception.SeatReservationFailedException;

public class TicketServiceImpl implements TicketService {
    private static final System.Logger LOGGER = System.getLogger(TicketServiceImpl.class.getName());
    private static final int MAX_TICKETS = 25;
    private static final int ADULT_PRICE = 25;
    private static final int CHILD_PRICE = 15;

    private final TicketPaymentService paymentService;
    private final SeatReservationService seatService;

    public TicketServiceImpl() {
        this(new TicketPaymentServiceImpl(), new SeatReservationServiceImpl());
    }

    TicketServiceImpl(TicketPaymentService paymentService, SeatReservationService seatService) {
        this.paymentService = Objects.requireNonNull(paymentService, "paymentService must not be null");
        this.seatService = Objects.requireNonNull(seatService, "seatService must not be null");
    }

    @Override
    public void purchaseTickets(Long accountId, TicketTypeRequest... ticketTypeRequests) throws InvalidPurchaseException {
        PurchaseTotals totals;
        try {
            totals = validateAndCalculateTotals(accountId, ticketTypeRequests);
        } catch (InvalidPurchaseException failure) {
            LOGGER.log(System.Logger.Level.DEBUG, "Ticket purchase rejected ({0})", failure.getMessage());
            throw failure;
        }

        try {
            paymentService.makePayment(accountId, totals.amountToPay());
        } catch (RuntimeException cause) {
            LOGGER.log(System.Logger.Level.ERROR, "Ticket payment failed");
            throw new PaymentFailedException(cause);
        }

        try {
            seatService.reserveSeat(accountId, totals.seatsToReserve());
        } catch (RuntimeException cause) {
            LOGGER.log(System.Logger.Level.ERROR, "Seat reservation failed after payment");
            throw new SeatReservationFailedException(cause);
        }

        LOGGER.log(System.Logger.Level.INFO,
                "Ticket purchase completed ({0} tickets, {1} seats)", totals.ticketCount(), totals.seatsToReserve());
    }

    private static PurchaseTotals validateAndCalculateTotals(Long accountId, TicketTypeRequest[] ticketTypeRequests) {
        if (accountId == null || accountId <= 0) {
            throw new InvalidPurchaseException("Account ID must be positive");
        }
        if (ticketTypeRequests == null || ticketTypeRequests.length == 0) {
            throw new InvalidPurchaseException("At least one ticket request is required");
        }

        int totalTickets = 0;
        int adults = 0;
        int children = 0;
        int infants = 0;
        for (TicketTypeRequest request : ticketTypeRequests) {
            if (request == null || request.getTicketType() == null || request.getNoOfTickets() < 0) {
                throw new InvalidPurchaseException("Each request requires a ticket type and a non-negative quantity");
            }
            int quantity = request.getNoOfTickets();
            if (quantity > MAX_TICKETS - totalTickets) {
                throw new InvalidPurchaseException("Cannot purchase more than " + MAX_TICKETS + " tickets");
            }
            totalTickets += quantity;
            switch (request.getTicketType()) {
                case ADULT -> adults += quantity;
                case CHILD -> children += quantity;
                case INFANT -> infants += quantity;
            }
        }

        if (totalTickets == 0) {
            throw new InvalidPurchaseException("At least one ticket must be purchased");
        }
        if (adults == 0) {
            throw new InvalidPurchaseException("At least one adult ticket is required");
        }
        if (infants > adults) {
            throw new InvalidPurchaseException("Each infant requires an accompanying adult");
        }

        int amountToPay = adults * ADULT_PRICE + children * CHILD_PRICE;
        int seatsToReserve = adults + children;
        return new PurchaseTotals(totalTickets, amountToPay, seatsToReserve);
    }

    private record PurchaseTotals(int ticketCount, int amountToPay, int seatsToReserve) {
    }
}
