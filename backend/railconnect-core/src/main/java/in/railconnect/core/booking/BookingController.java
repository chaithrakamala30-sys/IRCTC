package in.railconnect.core.booking;

import in.railconnect.core.api.ApiEnvelope;
import in.railconnect.core.api.RequestIds;
import in.railconnect.core.booking.BookingModels.BookingDetails;
import in.railconnect.core.booking.BookingModels.BookingFare;
import in.railconnect.core.booking.BookingModels.BookingPassenger;
import in.railconnect.core.booking.BookingModels.BookingStatus;
import in.railconnect.core.booking.BookingModels.BookingSummary;
import in.railconnect.core.booking.BookingModels.CreateBookingRequest;
import in.railconnect.core.booking.BookingModels.Page;
import in.railconnect.core.identity.RailConnectPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Validated
@RestController
public class BookingController {
    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping("/api/v1/bookings")
    @PreAuthorize("hasRole('PASSENGER') and hasAuthority('bookings:own:create')")
    public ApiEnvelope<BookingDetails> create(
            @Valid @RequestBody CreateBookingRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal RailConnectPrincipal principal
    ) {
        return success(bookingService.create(request, idempotencyKey, principal.userId()));
    }

    @GetMapping("/api/v1/bookings")
    @PreAuthorize("hasRole('PASSENGER') and hasAuthority('bookings:own:read')")
    public ApiEnvelope<Page<BookingSummary>> history(
            @RequestParam(required = false) @Size(max = 24) String status,
            @RequestParam(required = false) @Size(max = 24) String reference,
            @RequestParam(defaultValue = "0") @Min(0) @Max(100000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @AuthenticationPrincipal RailConnectPrincipal principal
    ) {
        return success(bookingService.history(principal.userId(), status, reference, page, size));
    }

    @GetMapping("/api/v1/bookings/{bookingId}")
    @PreAuthorize("hasRole('PASSENGER') and hasAuthority('bookings:own:read')")
    public ApiEnvelope<BookingDetails> read(
            @PathVariable UUID bookingId,
            @AuthenticationPrincipal RailConnectPrincipal principal
    ) {
        return success(bookingService.readOwn(bookingId, principal.userId()));
    }

    @GetMapping("/api/v1/bookings/reference/{bookingReference}")
    @PreAuthorize("hasRole('PASSENGER') and hasAuthority('bookings:own:read')")
    public ApiEnvelope<BookingDetails> readByReference(
            @PathVariable String bookingReference,
            @AuthenticationPrincipal RailConnectPrincipal principal
    ) {
        return success(bookingService.readOwnByReference(bookingReference, principal.userId()));
    }

    @GetMapping("/api/v1/bookings/{bookingId}/passengers")
    @PreAuthorize("hasRole('PASSENGER') and hasAuthority('bookings:own:read')")
    public ApiEnvelope<List<BookingPassenger>> passengers(
            @PathVariable UUID bookingId,
            @AuthenticationPrincipal RailConnectPrincipal principal
    ) {
        return success(bookingService.readOwn(bookingId, principal.userId()).passengers());
    }

    @GetMapping("/api/v1/bookings/{bookingId}/fare")
    @PreAuthorize("hasRole('PASSENGER') and hasAuthority('bookings:own:read')")
    public ApiEnvelope<BookingFare> fare(
            @PathVariable UUID bookingId,
            @AuthenticationPrincipal RailConnectPrincipal principal
    ) {
        BookingDetails booking = bookingService.readOwn(bookingId, principal.userId());
        return success(new BookingFare(
                booking.fareQuoteId(), booking.fareRuleVersionNumber(), booking.distanceKm(),
                booking.totalPayable(), booking.currency(), booking.passengers()));
    }

    @GetMapping("/api/v1/bookings/{bookingId}/status")
    @PreAuthorize("hasRole('PASSENGER') and hasAuthority('bookings:own:read')")
    public ApiEnvelope<BookingStatus> status(
            @PathVariable UUID bookingId,
            @AuthenticationPrincipal RailConnectPrincipal principal
    ) {
        BookingDetails booking = bookingService.readOwn(bookingId, principal.userId());
        return success(new BookingStatus(
                booking.bookingId(), booking.bookingReference(), booking.status(),
                booking.paymentDeadline(), booking.stateHistory()));
    }

    private static <T> ApiEnvelope<T> success(T data) {
        return ApiEnvelope.success(data, RequestIds.current());
    }
}
