package in.railconnect.core.reservation;

import in.railconnect.core.api.ApiResponse;
import in.railconnect.core.identity.AuthenticatedUser;
import in.railconnect.core.reservation.AdvancedReservationModels.*;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class AdvancedReservationController {

    private final AdvancedReservationService reservationService;

    public AdvancedReservationController(AdvancedReservationService reservationService) {
        this.reservationService = reservationService;
    }

    // =========================================================================
    // Public / Passenger APIs
    // =========================================================================

    @GetMapping("/quotas")
    public ApiResponse<List<QuotaView>> getPublicQuotas() {
        return ApiResponse.ok(reservationService.listQuotas());
    }

    @GetMapping("/bookings/{bookingId}/reservation-status")
    public ApiResponse<PassengerReservationStatusView> getBookingReservationStatus(
            @PathVariable UUID bookingId
    ) {
        return ApiResponse.ok(reservationService.getBookingReservationStatus(bookingId));
    }

    // =========================================================================
    // Admin Quotas & Queues APIs
    // =========================================================================

    @GetMapping("/admin/quotas")
    @PreAuthorize("hasAuthority('quotas:read')")
    public ApiResponse<List<QuotaView>> getAdminQuotas() {
        return ApiResponse.ok(reservationService.listQuotas());
    }

    @GetMapping("/admin/train-runs/{trainRunId}/quota-allocations")
    @PreAuthorize("hasAuthority('quotas:read')")
    public ApiResponse<List<QuotaAllocationView>> getQuotaAllocations(
            @PathVariable UUID trainRunId
    ) {
        return ApiResponse.ok(reservationService.listQuotaAllocations(trainRunId));
    }

    @PostMapping("/admin/quota-allocations")
    @PreAuthorize("hasAuthority('quotas:manage')")
    public ApiResponse<QuotaAllocationView> configureQuotaAllocation(
            @Valid @RequestBody QuotaAllocationInput input
    ) {
        return ApiResponse.ok(reservationService.configureQuotaAllocation(input));
    }

    @GetMapping("/admin/train-runs/{trainRunId}/queues")
    @PreAuthorize("hasAuthority('quotas:read')")
    public ApiResponse<List<ReservationQueueView>> getReservationQueues(
            @PathVariable UUID trainRunId
    ) {
        return ApiResponse.ok(reservationService.listQueues(trainRunId));
    }

    @PostMapping("/admin/train-runs/{trainRunId}/process-promotions")
    @PreAuthorize("hasAuthority('quotas:manage')")
    public ApiResponse<List<PromotionResult>> triggerPromotions(
            @PathVariable UUID trainRunId
    ) {
        return ApiResponse.ok(reservationService.processPromotionsForTrainRun(trainRunId, null));
    }

    // =========================================================================
    // Operations & Charting APIs
    // =========================================================================

    @GetMapping("/operations/charts")
    @PreAuthorize("hasAuthority('charts:read')")
    public ApiResponse<List<ChartView>> getOperationalCharts() {
        return ApiResponse.ok(reservationService.listCharts());
    }

    @GetMapping("/operations/charts/{chartId}")
    @PreAuthorize("hasAuthority('charts:read')")
    public ApiResponse<ChartView> getChartDetails(
            @PathVariable UUID chartId
    ) {
        return ApiResponse.ok(reservationService.getChart(chartId));
    }

    @PostMapping("/operations/train-runs/{trainRunId}/prepare-chart")
    @PreAuthorize("hasAuthority('charts:prepare')")
    public ApiResponse<ChartView> prepareChart(
            @PathVariable UUID trainRunId,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID operatorId = user != null ? user.userId() : null;
        return ApiResponse.ok(reservationService.prepareChart(trainRunId, operatorId));
    }

    @PostMapping("/operations/charts/{chartId}/finalize")
    @PreAuthorize("hasAuthority('charts:finalize')")
    public ApiResponse<ChartView> finalizeChart(
            @PathVariable UUID chartId,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID operatorId = user != null ? user.userId() : null;
        return ApiResponse.ok(reservationService.finalizeChart(chartId, operatorId));
    }

    @PostMapping("/operations/charts/{chartId}/corrections")
    @PreAuthorize("hasAuthority('charts:correct')")
    public ApiResponse<ChartView> authorizeChartCorrection(
            @PathVariable UUID chartId,
            @Valid @RequestBody ChartCorrectionRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID operatorId = user != null ? user.userId() : null;
        return ApiResponse.ok(reservationService.authorizeCorrection(chartId, request, operatorId));
    }
}
