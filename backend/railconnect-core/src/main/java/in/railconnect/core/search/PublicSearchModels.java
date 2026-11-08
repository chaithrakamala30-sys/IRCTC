package in.railconnect.core.search;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

public final class PublicSearchModels {
    private PublicSearchModels() {
    }

    public record StationSuggestion(
            UUID stationId,
            String stationCode,
            String stationName,
            String city,
            String state,
            String timezone
    ) {
    }

    public record StationDetail(
            UUID stationId,
            String stationCode,
            String stationName,
            String city,
            String state,
            String country,
            String timezone
    ) {
    }

    public record CoachClass(String code, String name, int coachCount, List<String> coachTypes) {
    }

    public record TrainCategoryOption(String code, String name) {
    }

    public record SearchFilters(List<TrainCategoryOption> trainCategories, List<CoachClass> coachClasses) {
    }

    public record SearchConfig(int maximumDays) {
    }

    public record TrainSearchResult(
            UUID trainId,
            String trainNumber,
            String trainName,
            String categoryCode,
            String categoryName,
            StationSuggestion origin,
            StationSuggestion destination,
            LocalDate journeyDate,
            LocalDate arrivalDate,
            ZonedDateTime departureAt,
            ZonedDateTime arrivalAt,
            long durationMinutes,
            BigDecimal distanceKm,
            int intermediateStops,
            List<Integer> operatingDays,
            List<CoachClass> coachClasses
    ) {
    }

    public record RouteStop(
            UUID stationId,
            String stationCode,
            String stationName,
            String city,
            String timezone,
            int sequence,
            LocalTime arrivalTime,
            LocalTime departureTime,
            LocalDate arrivalDate,
            LocalDate departureDate,
            int arrivalDayOffset,
            int departureDayOffset,
            int haltMinutes,
            BigDecimal distanceFromSourceKm,
            boolean selectedOrigin,
            boolean selectedDestination
    ) {
    }

    public record TrainDetail(TrainSearchResult journey, List<RouteStop> route) {
    }

    public record SearchPage<T>(List<T> items, int page, int size, long totalItems) {
    }
}
