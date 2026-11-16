package uk.gov.dwp.uc.pairtest;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest;
import uk.gov.dwp.uc.pairtest.exception.InvalidPurchaseException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest.Type.ADULT;
import static uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest.Type.CHILD;
import static uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest.Type.INFANT;

class TicketServiceImplTest {
    private final List<ProviderCall> calls = new ArrayList<>();
    private final TicketService service = new TicketServiceImpl(
            (accountId, amount) -> calls.add(new ProviderCall("payment", accountId, amount)),
            (accountId, seats) -> calls.add(new ProviderCall("reservation", accountId, seats)));

    @Test
    void combinesRepeatedTypesAndChargesOnlyForAdultsAndChildren() {
        service.purchaseTickets(42L, new TicketTypeRequest(ADULT, 1),
                new TicketTypeRequest(CHILD, 3), new TicketTypeRequest(ADULT, 1),
                new TicketTypeRequest(INFANT, 2), new TicketTypeRequest(CHILD, 0));

        assertEquals(List.of(new ProviderCall("payment", 42L, 95),
                new ProviderCall("reservation", 42L, 5)), calls);
    }

    @Test
    void acceptsExactlyTwentyFiveTicketsIncludingInfants() {
        service.purchaseTickets(1L, new TicketTypeRequest(ADULT, 20),
                new TicketTypeRequest(INFANT, 5));

        assertEquals(List.of(new ProviderCall("payment", 1L, 500),
                new ProviderCall("reservation", 1L, 20)), calls);
    }

    @Test
    void reportsPaymentFailureAndSkipsReservation() {
        var cause = new RuntimeException("Payment provider unavailable");
        TicketService failingService = new TicketServiceImpl(
                (accountId, amount) -> { throw cause; },
                (accountId, seats) -> calls.add(new ProviderCall("reservation", accountId, seats)));

        var failure = assertThrows(IllegalStateException.class,
                () -> failingService.purchaseTickets(1L, new TicketTypeRequest(ADULT, 1)));

        assertEquals("Ticket payment failed", failure.getMessage());
        assertSame(cause, failure.getCause());
        assertEquals(List.of(), calls);
    }

    @Test
    void reportsReservationFailureAfterPayment() {
        var cause = new RuntimeException("Reservation provider unavailable");
        TicketService failingService = new TicketServiceImpl(
                (accountId, amount) -> calls.add(new ProviderCall("payment", accountId, amount)),
                (accountId, seats) -> { throw cause; });

        var failure = assertThrows(IllegalStateException.class,
                () -> failingService.purchaseTickets(1L, new TicketTypeRequest(ADULT, 1)));

        assertEquals("Seat reservation failed after payment", failure.getMessage());
        assertSame(cause, failure.getCause());
        assertEquals(List.of(new ProviderCall("payment", 1L, 25)), calls);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidPurchases")
    void rejectsInvalidPurchasesBeforeCallingProviders(String scenario, Long accountId,
                                                       TicketTypeRequest[] requests) {
        assertThrows(InvalidPurchaseException.class, () -> service.purchaseTickets(accountId, requests));
        assertEquals(List.of(), calls);
    }

    private static Stream<Arguments> invalidPurchases() {
        var adult = new TicketTypeRequest(ADULT, 1);
        return Stream.of(
                invalid("null account", null, adult),
                invalid("zero account", 0L, adult),
                invalid("negative account", -1L, adult),
                invalid("null request array", 1L, (TicketTypeRequest[]) null),
                invalid("empty request array", 1L),
                invalid("null request entry", 1L, adult, null),
                invalid("null ticket type", 1L, adult, new TicketTypeRequest(null, 1)),
                invalid("negative quantity", 1L, adult, new TicketTypeRequest(CHILD, -1)),
                invalid("all quantities are zero", 1L, new TicketTypeRequest(ADULT, 0)),
                invalid("child without adult", 1L, new TicketTypeRequest(CHILD, 1)),
                invalid("infant without adult", 1L, new TicketTypeRequest(INFANT, 1)),
                invalid("more infants than adults", 1L, adult, new TicketTypeRequest(INFANT, 2)),
                invalid("26 tickets including infants", 1L,
                        new TicketTypeRequest(ADULT, 25), new TicketTypeRequest(INFANT, 1)),
                invalid("quantity that would overflow the running total", 1L,
                        adult, new TicketTypeRequest(ADULT, Integer.MAX_VALUE)));
    }

    private static Arguments invalid(String scenario, Long accountId, TicketTypeRequest... requests) {
        return Arguments.of(scenario, accountId, requests);
    }

    private record ProviderCall(String operation, long accountId, int value) {
    }
}
