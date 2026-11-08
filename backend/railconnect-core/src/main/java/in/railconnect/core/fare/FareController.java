package in.railconnect.core.fare;

import in.railconnect.core.api.ApiEnvelope;
import in.railconnect.core.api.RequestIds;
import in.railconnect.core.fare.FareModels.FarePassengerCategory;
import in.railconnect.core.fare.FareModels.FareAdminOptions;
import in.railconnect.core.fare.FareModels.FareQuoteRequest;
import in.railconnect.core.fare.FareModels.FareQuoteResponse;
import in.railconnect.core.fare.FareModels.FareRuleAuditEvent;
import in.railconnect.core.fare.FareModels.FareRuleVersionRequest;
import in.railconnect.core.fare.FareModels.FareSimulationRequest;
import in.railconnect.core.fare.FareModels.FareVersionSummary;
import in.railconnect.core.fare.FareModels.FareVersionDetail;
import in.railconnect.core.fare.FareModels.PassengerCategoryRequest;
import in.railconnect.core.identity.RailConnectPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Validated
@RestController
public class FareController {
    private final FareService fareService;

    public FareController(FareService fareService) {
        this.fareService = fareService;
    }

    @GetMapping("/api/v1/admin/fares/rules")
    @PreAuthorize("hasAuthority('fares:rules:read')")
    public ApiEnvelope<List<FareVersionSummary>> rules() {
        return success(fareService.versions());
    }

    @GetMapping("/api/v1/admin/fares/options")
    @PreAuthorize("hasAuthority('fares:rules:read')")
    public ApiEnvelope<FareAdminOptions> options() {
        return success(fareService.adminOptions());
    }

    @PostMapping("/api/v1/admin/fares/rules")
    @PreAuthorize("hasAuthority('fares:rules:manage')")
    public ApiEnvelope<FareVersionSummary> createRule(
            @Valid @RequestBody FareRuleVersionRequest request,
            @AuthenticationPrincipal RailConnectPrincipal actor
    ) {
        return success(fareService.createRule(request, actor));
    }

    @PostMapping("/api/v1/admin/fares/rules/{ruleId}/versions")
    @PreAuthorize("hasAuthority('fares:rules:manage')")
    public ApiEnvelope<FareVersionSummary> createVersion(
            @PathVariable UUID ruleId,
            @Valid @RequestBody FareRuleVersionRequest request,
            @AuthenticationPrincipal RailConnectPrincipal actor
    ) {
        return success(fareService.createVersion(ruleId, request, actor));
    }

    @PutMapping("/api/v1/admin/fares/versions/{versionId}")
    @PreAuthorize("hasAuthority('fares:rules:manage')")
    public ApiEnvelope<FareVersionSummary> updateDraft(
            @PathVariable UUID versionId,
            @Valid @RequestBody FareRuleVersionRequest request,
            @AuthenticationPrincipal RailConnectPrincipal actor
    ) {
        return success(fareService.updateDraft(versionId, request, actor));
    }

    @GetMapping("/api/v1/admin/fares/versions/{versionId}")
    @PreAuthorize("hasAuthority('fares:rules:read')")
    public ApiEnvelope<FareVersionDetail> versionDetail(@PathVariable UUID versionId) {
        return success(fareService.versionDetail(versionId));
    }

    @PostMapping("/api/v1/admin/fares/versions/{versionId}/submit")
    @PreAuthorize("hasAuthority('fares:rules:manage')")
    public ApiEnvelope<FareVersionSummary> submit(
            @PathVariable UUID versionId,
            @AuthenticationPrincipal RailConnectPrincipal actor
    ) {
        return success(fareService.submit(versionId, actor));
    }

    @PostMapping("/api/v1/admin/fares/versions/{versionId}/approve")
    @PreAuthorize("hasAuthority('fares:rules:approve')")
    public ApiEnvelope<FareVersionSummary> approve(
            @PathVariable UUID versionId,
            @AuthenticationPrincipal RailConnectPrincipal actor
    ) {
        return success(fareService.approve(versionId, actor));
    }

    @PostMapping("/api/v1/admin/fares/versions/{versionId}/publish")
    @PreAuthorize("hasAuthority('fares:rules:publish')")
    public ApiEnvelope<FareVersionSummary> publish(
            @PathVariable UUID versionId,
            @AuthenticationPrincipal RailConnectPrincipal actor
    ) {
        return success(fareService.publish(versionId, actor));
    }

    @PostMapping("/api/v1/admin/fares/versions/{versionId}/retire")
    @PreAuthorize("hasAuthority('fares:rules:publish')")
    public ApiEnvelope<FareVersionSummary> retire(
            @PathVariable UUID versionId,
            @AuthenticationPrincipal RailConnectPrincipal actor
    ) {
        return success(fareService.retire(versionId, actor));
    }

    @GetMapping("/api/v1/admin/fares/versions/{versionId}/audit")
    @PreAuthorize("hasAuthority('fares:rules:read')")
    public ApiEnvelope<List<FareRuleAuditEvent>> audit(@PathVariable UUID versionId) {
        return success(fareService.auditEvents(versionId));
    }

    @GetMapping("/api/v1/admin/fares/passenger-categories")
    @PreAuthorize("hasAuthority('fares:rules:read')")
    public ApiEnvelope<List<FarePassengerCategory>> categories() {
        return success(fareService.passengerCategories());
    }

    @PostMapping("/api/v1/admin/fares/passenger-categories")
    @PreAuthorize("hasAuthority('fares:rules:manage')")
    public ApiEnvelope<FarePassengerCategory> createCategory(
            @Valid @RequestBody PassengerCategoryRequest request,
            @AuthenticationPrincipal RailConnectPrincipal actor
    ) {
        return success(fareService.createPassengerCategory(request, actor));
    }

    @PostMapping("/api/v1/admin/fares/simulate")
    @PreAuthorize("hasAuthority('fares:rules:read')")
    public ApiEnvelope<FareQuoteResponse> simulate(@Valid @RequestBody FareSimulationRequest request) {
        return success(fareService.simulate(request));
    }

    @GetMapping("/api/v1/public/fares/passenger-categories")
    public ApiEnvelope<List<FarePassengerCategory>> publicCategories() {
        return success(fareService.publicPassengerCategories());
    }

    @PostMapping("/api/v1/public/fares/quotes")
    public ApiEnvelope<FareQuoteResponse> createQuote(
            @Valid @RequestBody FareQuoteRequest request,
            @AuthenticationPrincipal(errorOnInvalidType = false) RailConnectPrincipal actor
    ) {
        return success(fareService.quote(request, actor));
    }

    @GetMapping("/api/v1/public/fares/quotes/{quoteId}")
    public ApiEnvelope<FareQuoteResponse> quote(
            @PathVariable UUID quoteId,
            @AuthenticationPrincipal(errorOnInvalidType = false) RailConnectPrincipal actor
    ) {
        return success(fareService.quote(quoteId, actor));
    }

    private static <T> ApiEnvelope<T> success(T data) {
        return ApiEnvelope.success(data, RequestIds.current());
    }
}
