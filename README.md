# Cinema Tickets

A Java 21 service for a DWP coding exercise. It validates a cinema ticket purchase, calculates the amount to charge, and calls the supplied payment and seat reservation services. The supplied providers are stubs for the exercise.

## Build and test

Install JDK 21 and Maven. Check that Maven is using Java 21 with `mvn -version`, then run:

```sh
mvn clean test
```

The tests cover pricing, seat allocation, the 25-ticket limit, invalid requests, and provider failures.

## Ticket rules

| Ticket type | Price (GBP) | Seat reserved |
| --- | ---: | --- |
| Adult | 25 | Yes |
| Child | 15 | Yes |
| Infant | 0 | No |

- The account ID must be positive.
- A purchase must contain at least one adult and at most 25 tickets, including infants.
- Each infant needs an adult to sit with. Infant tickets cannot outnumber adult tickets.
- Multiple requests for the same ticket type are added together. Individual quantities may be zero, but the purchase must include at least one ticket. Negative quantities, missing requests, and missing ticket types are rejected.

## Usage

```java
import uk.gov.dwp.uc.pairtest.TicketService;
import uk.gov.dwp.uc.pairtest.TicketServiceImpl;
import uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest;
import uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest.Type;

TicketService service = new TicketServiceImpl();
service.purchaseTickets(42L,
        new TicketTypeRequest(Type.ADULT, 2),
        new TicketTypeRequest(Type.CHILD, 1),
        new TicketTypeRequest(Type.INFANT, 1));
```

This requests a payment of 65 GBP and reserves three seats. Invalid requests throw `InvalidPurchaseException` before either provider is called. Payment failures throw `PaymentFailedException`, and reservation failures throw `SeatReservationFailedException`. Both exceptions preserve the original cause. Payment runs first, so a reservation failure can occur after payment has completed.
