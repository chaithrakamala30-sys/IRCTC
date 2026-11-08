package in.railconnect.core.search;

import in.railconnect.core.api.ApiEnvelope;
import in.railconnect.core.api.RequestIds;
import in.railconnect.core.search.PublicSearchModels.SearchPage;
import in.railconnect.core.search.PublicSearchModels.SearchFilters;
import in.railconnect.core.search.PublicSearchModels.SearchConfig;
import in.railconnect.core.search.PublicSearchModels.RouteStop;
import in.railconnect.core.search.PublicSearchModels.StationDetail;
import in.railconnect.core.search.PublicSearchModels.StationSuggestion;
import in.railconnect.core.search.PublicSearchModels.TrainDetail;
import in.railconnect.core.search.PublicSearchModels.TrainSearchResult;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/v1/public")
public class PublicTrainSearchController {
    private final PublicTrainSearchService searchService;

    public PublicTrainSearchController(PublicTrainSearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping("/stations/search")
    @Operation(summary = "Search active stations for passenger autocomplete")
    public ApiEnvelope<List<StationSuggestion>> stationSearch(
            @RequestParam @Size(min = 2, max = 80) String q,
            @RequestParam(defaultValue = "10") @Min(1) @Max(20) int limit
    ) {
        return success(searchService.searchStations(q, limit));
    }

    @GetMapping("/stations/{stationId}")
    @Operation(summary = "Get public details for an active station")
    public ApiEnvelope<StationDetail> station(@PathVariable UUID stationId) {
        return success(searchService.station(stationId));
    }

    @GetMapping("/trains/filters")
    @Operation(summary = "List active train categories and coach classes used in published compositions")
    public ApiEnvelope<SearchFilters> searchFilters() {
        return success(searchService.searchFilters());
    }

    @GetMapping("/search-config")
    @Operation(summary = "Get public train search date-range limits")
    public ApiEnvelope<SearchConfig> searchConfig() {
        return success(searchService.searchConfig());
    }

    @GetMapping("/trains/search")
    @Operation(summary = "Search published direct train journeys between two active stations")
    public ApiEnvelope<SearchPage<TrainSearchResult>> trainSearch(
            @RequestParam UUID fromStationId,
            @RequestParam UUID toStationId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate journeyDate,
            @RequestParam(required = false) @Size(max = 120) String trainCategory,
            @RequestParam(required = false) @Size(max = 120) String coachClass,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime departureFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime departureTo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime arrivalFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime arrivalTo,
            @RequestParam(required = false) @Min(1) @Max(10080) Integer maximumDuration,
            @RequestParam(required = false) @Size(max = 80) String q,
            @RequestParam(defaultValue = "DEPARTURE_EARLIEST") @Size(max = 32) String sortBy,
            @RequestParam(defaultValue = "0") @Min(0) @Max(10_000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return success(searchService.search(
                fromStationId,
                toStationId,
                journeyDate,
                trainCategory,
                coachClass,
                departureFrom,
                departureTo,
                arrivalFrom,
                arrivalTo,
                maximumDuration,
                q,
                sortBy,
                page,
                size
        ));
    }

    @GetMapping("/trains/{trainId}")
    @Operation(summary = "Get a matching train journey and its complete published route")
    public ApiEnvelope<TrainDetail> trainDetails(
            @PathVariable UUID trainId,
            @RequestParam UUID fromStationId,
            @RequestParam UUID toStationId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate journeyDate
    ) {
        return success(searchService.detail(trainId, fromStationId, toStationId, journeyDate));
    }

    @GetMapping("/trains/{trainId}/route")
    @Operation(summary = "Get the ordered route for a matching train journey")
    public ApiEnvelope<List<RouteStop>> trainRoute(
            @PathVariable UUID trainId,
            @RequestParam UUID fromStationId,
            @RequestParam UUID toStationId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate journeyDate
    ) {
        return success(searchService.detail(trainId, fromStationId, toStationId, journeyDate).route());
    }

    private static <T> ApiEnvelope<T> success(T data) {
        return ApiEnvelope.success(data, RequestIds.current());
    }
}
