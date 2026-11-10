package in.railconnect.core.reservation;

import in.railconnect.core.api.ApiEnvelope;
import in.railconnect.core.api.RequestIds;
import in.railconnect.core.identity.RailConnectPrincipal;
import in.railconnect.core.reservation.AdvancedReservationModels.*;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/v1")
public class AdvancedReservationController {

    private final AdvancedReservationService reservationService;

    public AdvancedReservationController(AdvancedReservationService reservationService) {
        this.reservationService = reservationService;
    }

    private static <T> ApiEnvelope<T> success(T data) {
        return ApiEnvelope.success(data, RequestIds.current());
    }

    // =========================================================================
    // Public / Passenger APIs
    // =========================================================================

    @GetMapping("/quotas")
    public ApiEnvelope<List<QuotaView>> getPublicQuotas() {
        return success(reservationService.listQuotas());
    }

    @GetMapping("/bookings/{bookingId}/reservation-status")
    public ApiEnvelope<PassengerReservationStatusView> getBookingReservationStatus(
            @PathVariable UUID bookingId
    ) {
        return success(reservationService.getBookingReservationStatus(bookingId));
    }

    // =========================================================================
    // Admin Quotas & Queues APIs
    // =========================================================================

    @GetMapping("/admin/quotas")
    @PreAuthorize("hasAuthority('quotas:read')")
    public ApiEnvelope<List<QuotaView>> getAdminQuotas() {
        return success(reservationService.listQuotas());
    }

    @GetMapping("/admin/train-runs/{trainRunId}/quota-allocations")
    @PreAuthorize("hasAuthority('quotas:read')")
    public ApiEnvelope<List<QuotaAllocationView>> getQuotaAllocations(
            @PathVariable UUID trainRunId
    ) {
        return success(reservationService.listQuotaAllocations(trainRunId));
    }

    @PostMapping("/admin/quota-allocations")
    @PreAuthorize("hasAuthority('quotas:manage')")
    public ApiEnvelope<QuotaAllocationView> configureQuotaAllocation(
            @Valid @RequestBody QuotaAllocationInput input
    ) {
        return success(reservationService.configureQuotaAllocation(input));
    }

    @GetMapping("/admin/train-runs/{trainRunId}/queues")
    @PreAuthorize("hasAuthority('quotas:read')")
    public ApiEnvelope<List<ReservationQueueView>> getReservationQueues(
            @PathVariable UUID trainRunId
    ) {
        return success(reservationService.listQueues(trainRunId));
    }

    @PostMapping("/admin/train-runs/{trainRunId}/process-promotions")
    @PreAuthorize("hasAuthority('quotas:manage')")
    public ApiEnvelope<List<PromotionResult>> triggerPromotions(
            @PathVariable UUID trainRunId
    ) {
        return success(reservationService.processPromotionsForTrainRun(trainRunId, null));
    }

    // =========================================================================
    // Operations & Charting APIs
    // =========================================================================

    @GetMapping("/operations/charts")
    @PreAuthorize("hasAuthority('charts:read')")
    public ApiEnvelope<List<ChartView>> getOperationalCharts() {
        return success(reservationService.listCharts());
    }

    @GetMapping("/operations/charts/{chartId}")
    @PreAuthorize("hasAuthority('charts:read')")
    public ApiEnvelope<ChartView> getChartDetails(
            @PathVariable UUID chartId
    ) {
        return success(reservationService.getChart(chartId));
    }

    @PostMapping("/operations/train-runs/{trainRunId}/prepare-chart")
    @PreAuthorize("hasAuthority('charts:prepare')")
    public ApiEnvelope<ChartView> prepareChart(
            @PathVariable UUID trainRunId,
            @AuthenticationPrincipal RailConnectPrincipal principal
    ) {
        UUID operatorId = principal != null ? principal.userId() : null;
        return success(reservationService.prepareChart(trainRunId, operatorId));
    }

    @PostMapping("/operations/charts/{chartId}/finalize")
    @PreAuthorize("hasAuthority('charts:finalize')")
    public ApiEnvelope<ChartView> finalizeChart(
            @PathVariable UUID chartId,
            @AuthenticationPrincipal RailConnectPrincipal principal
    ) {
        UUID operatorId = principal != null ? principal.userId() : null;
        return success(reservationService.finalizeChart(chartId, operatorId));
    }

    @PostMapping("/operations/charts/{chartId}/corrections")
    @PreAuthorize("hasAuthority('charts:correct')")
    public ApiEnvelope<ChartView> authorizeChartCorrection(
            @PathVariable UUID chartId,
            @Valid @RequestBody ChartCorrectionRequest request,
            @AuthenticationPrincipal RailConnectPrincipal principal
    ) {
        UUID operatorId = principal != null ? principal.userId() : null;
        return success(reservationService.authorizeCorrection(chartId, request, operatorId));
    }
}
