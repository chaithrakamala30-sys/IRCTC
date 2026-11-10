package in.railconnect.core.ticket;

import in.railconnect.core.api.ApiEnvelope;
import in.railconnect.core.api.RequestIds;
import in.railconnect.core.identity.RailConnectPrincipal;
import in.railconnect.core.ticket.TicketModels.TicketPage;
import in.railconnect.core.ticket.TicketModels.TicketDiagnostics;
import in.railconnect.core.ticket.TicketModels.TicketOperationsVerification;
import in.railconnect.core.ticket.TicketModels.TicketIssuanceFailure;
import in.railconnect.core.ticket.TicketModels.TicketVerification;
import in.railconnect.core.ticket.TicketModels.TicketVerificationRequest;
import in.railconnect.core.ticket.TicketModels.TicketView;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Validated
@RestController
public class TicketController {
    private final TicketIssuanceService tickets;
    private final TicketPdfService pdfService;

    public TicketController(TicketIssuanceService tickets, TicketPdfService pdfService) {
        this.tickets = tickets;
        this.pdfService = pdfService;
    }

    @GetMapping("/api/v1/tickets")
    @PreAuthorize("hasRole('PASSENGER') and hasAuthority('tickets:own:read')")
    public ApiEnvelope<TicketPage> ownHistory(
            @RequestParam(defaultValue = "0") @Min(0) @Max(100000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) @Pattern(regexp = "UPCOMING|PAST") String journey,
            @AuthenticationPrincipal RailConnectPrincipal principal
    ) {
        return success(tickets.ownTickets(principal.userId(), page, size, journey));
    }

    @GetMapping("/api/v1/tickets/{ticketId}")
    @PreAuthorize("hasRole('PASSENGER') and hasAuthority('tickets:own:read')")
    public ApiEnvelope<TicketView> ownTicket(
            @PathVariable UUID ticketId,
            @AuthenticationPrincipal RailConnectPrincipal principal
    ) {
        return success(tickets.ownTicket(ticketId, principal.userId()));
    }

    @GetMapping("/api/v1/tickets/pnr/{pnr}")
    @PreAuthorize("hasRole('PASSENGER') and hasAuthority('tickets:own:read')")
    public ApiEnvelope<TicketView> ownTicketByPnr(
            @PathVariable @Pattern(regexp = "RC-TK-[A-Z2-7]{16}") String pnr,
            @AuthenticationPrincipal RailConnectPrincipal principal
    ) {
        return success(tickets.ownTicketByPnr(pnr, principal.userId()));
    }

    @GetMapping("/api/v1/bookings/{bookingId}/ticket")
    @PreAuthorize("hasRole('PASSENGER') and hasAuthority('tickets:own:read')")
    public ApiEnvelope<TicketView> ownTicketByBooking(
            @PathVariable UUID bookingId,
            @AuthenticationPrincipal RailConnectPrincipal principal
    ) {
        return success(tickets.ownTicketByBooking(bookingId, principal.userId()));
    }

    @GetMapping("/api/v1/tickets/{ticketId}/download")
    @PreAuthorize("hasRole('PASSENGER') and hasAuthority('tickets:own:read')")
    public ResponseEntity<byte[]> download(
            @PathVariable UUID ticketId,
            @AuthenticationPrincipal RailConnectPrincipal principal
    ) {
        TicketView ticket = tickets.readForPdf(ticketId, principal.userId());
        byte[] pdf = pdfService.generate(ticket, principal.userId());
        return pdfResponse(ticket, pdf);
    }

    @GetMapping("/api/v1/ticket-verification/{token}")
    public ApiEnvelope<TicketVerification> verifyPublic(
            @PathVariable @Size(max = 200) String token,
            HttpServletRequest request
    ) {
        return success(tickets.verifyPublicToken(token, request.getRemoteAddr()));
    }

    @PostMapping("/api/v1/ticket-verification/pnr")
    @PreAuthorize("hasAuthority('tickets:operations:verify')")
    public ApiEnvelope<TicketOperationsVerification> verifyPnr(
            @Valid @RequestBody TicketVerificationRequest request,
            @AuthenticationPrincipal RailConnectPrincipal principal
    ) {
        return success(tickets.verifyPnrForOperationsDetailed(request.pnr(), principal.userId()));
    }

    @GetMapping("/api/v1/operations/tickets/verify/{token}")
    @PreAuthorize("hasAuthority('tickets:operations:verify')")
    public ApiEnvelope<TicketOperationsVerification> verifyOperationsToken(
            @PathVariable @Size(max = 200) String token,
            @AuthenticationPrincipal RailConnectPrincipal principal
    ) {
        return success(tickets.verifyTokenForOperations(token, principal.userId()));
    }

    @GetMapping("/api/v1/admin/tickets")
    @PreAuthorize("hasAuthority('tickets:admin:read')")
    public ApiEnvelope<TicketPage> adminSearch(
            @RequestParam(required = false) @Size(max = 80) String query,
            @RequestParam(defaultValue = "0") @Min(0) @Max(100000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @AuthenticationPrincipal RailConnectPrincipal principal
    ) {
        return success(tickets.adminSearch(query, page, size, principal.userId()));
    }

    @GetMapping("/api/v1/admin/tickets/{ticketId}")
    @PreAuthorize("hasAuthority('tickets:admin:read')")
    public ApiEnvelope<TicketView> adminTicket(
            @PathVariable UUID ticketId,
            @AuthenticationPrincipal RailConnectPrincipal principal
    ) {
        return success(tickets.adminTicket(ticketId, principal.userId()));
    }

    @GetMapping("/api/v1/admin/tickets/pnr/{pnr}")
    @PreAuthorize("hasAuthority('tickets:admin:read')")
    public ApiEnvelope<TicketView> adminTicketByPnr(
            @PathVariable @Pattern(regexp = "RC-TK-[A-Z2-7]{16}") String pnr,
            @AuthenticationPrincipal RailConnectPrincipal principal
    ) {
        return success(tickets.adminTicketByPnr(pnr, principal.userId()));
    }

    @GetMapping("/api/v1/admin/tickets/{ticketId}/diagnostics")
    @PreAuthorize("hasAuthority('tickets:admin:read')")
    public ApiEnvelope<TicketDiagnostics> diagnostics(
            @PathVariable UUID ticketId,
            @AuthenticationPrincipal RailConnectPrincipal principal
    ) {
        return success(tickets.diagnostics(ticketId, principal.userId()));
    }

    @GetMapping("/api/v1/admin/tickets/issuance-failures")
    @PreAuthorize("hasAuthority('tickets:admin:read')")
    public ApiEnvelope<java.util.List<TicketIssuanceFailure>> issuanceFailures(
            @AuthenticationPrincipal RailConnectPrincipal principal
    ) {
        return success(tickets.issuanceFailures(principal.userId()));
    }

    @PostMapping("/api/v1/admin/tickets/issuance-failures/{bookingReference}/retry")
    @PreAuthorize("hasAuthority('tickets:admin:retry')")
    public ApiEnvelope<Void> retryIssuance(
            @PathVariable @Pattern(regexp = "RC-[23456789A-HJ-NP-Z]{12}") String bookingReference,
            @AuthenticationPrincipal RailConnectPrincipal principal
    ) {
        tickets.retryIssuance(bookingReference, principal.userId());
        return success(null);
    }

    private static ResponseEntity<byte[]> pdfResponse(TicketView ticket, byte[] pdf) {
        String filename = ticket.pnr() + ".pdf";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(pdf);
    }

    private static <T> ApiEnvelope<T> success(T data) {
        return ApiEnvelope.success(data, RequestIds.current());
    }
}
