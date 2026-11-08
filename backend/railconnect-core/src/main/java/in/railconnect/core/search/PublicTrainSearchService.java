package in.railconnect.core.search;

import in.railconnect.core.masterdata.MasterDataException;
import in.railconnect.core.search.JourneyTimeCalculator.JourneyTimes;
import in.railconnect.core.search.PublicSearchModels.CoachClass;
import in.railconnect.core.search.PublicSearchModels.RouteStop;
import in.railconnect.core.search.PublicSearchModels.SearchPage;
import in.railconnect.core.search.PublicSearchModels.SearchFilters;
import in.railconnect.core.search.PublicSearchModels.SearchConfig;
import in.railconnect.core.search.PublicSearchModels.StationDetail;
import in.railconnect.core.search.PublicSearchModels.StationSuggestion;
import in.railconnect.core.search.PublicSearchModels.TrainCategoryOption;
import in.railconnect.core.search.PublicSearchModels.TrainDetail;
import in.railconnect.core.search.PublicSearchModels.TrainSearchResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PublicTrainSearchService {
    private static final String CANDIDATE_CTE = """
            WITH raw AS (
                SELECT s.schedule_id,
                       rv.route_version_id,
                       t.train_id,
                       t.train_number,
                       t.train_name,
                       category.category_code,
                       category.category_name,
                       CAST(:journeyDate AS date) - origin_stop.departure_day_offset AS service_date,
                       origin_stop.departure_day_offset,
                       destination_stop.arrival_day_offset,
                       origin_stop.departure_time,
                       destination_stop.arrival_time,
                       destination_stop.distance_from_source_km - origin_stop.distance_from_source_km AS distance_km,
                       destination_stop.stop_sequence - origin_stop.stop_sequence - 1 AS intermediate_stops,
                       origin_station.station_id AS origin_id,
                       origin_station.station_code AS origin_code,
                       origin_station.station_name AS origin_name,
                       origin_station.city AS origin_city,
                       origin_station.state AS origin_state,
                       origin_station.timezone AS origin_timezone,
                       destination_station.station_id AS destination_id,
                       destination_station.station_code AS destination_code,
                       destination_station.station_name AS destination_name,
                       destination_station.city AS destination_city,
                       destination_station.state AS destination_state,
                       destination_station.timezone AS destination_timezone,
                       COUNT(*) OVER (PARTITION BY t.train_id) AS schedule_match_count
                FROM train_route_stops origin_stop
                JOIN train_route_stops destination_stop
                  ON destination_stop.route_version_id = origin_stop.route_version_id
                 AND origin_stop.stop_sequence < destination_stop.stop_sequence
                JOIN train_route_versions rv ON rv.route_version_id = origin_stop.route_version_id
                JOIN train_routes route ON route.route_id = rv.route_id
                JOIN train_schedules s ON s.route_version_id = rv.route_version_id
                JOIN trains t ON t.train_id = s.train_id
                JOIN train_categories category ON category.category_id = t.category_id
                JOIN stations origin_station ON origin_station.station_id = origin_stop.station_id
                JOIN stations destination_station ON destination_station.station_id = destination_stop.station_id
                WHERE origin_stop.station_id = :fromStationId
                  AND destination_stop.station_id = :toStationId
                  AND origin_station.status = 'ACTIVE'
                  AND destination_station.status = 'ACTIVE'
                  AND NOT EXISTS (
                      SELECT 1
                      FROM train_route_stops route_stop
                      JOIN stations route_station ON route_station.station_id = route_stop.station_id
                      WHERE route_stop.route_version_id = rv.route_version_id
                        AND route_station.status <> 'ACTIVE'
                  )
                  AND t.status = 'ACTIVE'
                  AND category.active = TRUE
                  AND route.status = 'PUBLISHED'
                  AND rv.status IN ('PUBLISHED', 'SUPERSEDED')
                  AND rv.published_at IS NOT NULL
                  AND s.status IN ('PUBLISHED', 'SUPERSEDED')
                  AND s.published_at IS NOT NULL
                  AND origin_stop.departure_time IS NOT NULL
                  AND destination_stop.arrival_time IS NOT NULL
                  AND t.effective_from <= CAST(:journeyDate AS date) - origin_stop.departure_day_offset
                  AND (t.effective_to IS NULL OR t.effective_to >=
                       CAST(:journeyDate AS date) - origin_stop.departure_day_offset)
                  AND rv.effective_from <= CAST(:journeyDate AS date) - origin_stop.departure_day_offset
                  AND (rv.effective_to IS NULL OR rv.effective_to >=
                       CAST(:journeyDate AS date) - origin_stop.departure_day_offset)
                  AND s.effective_from <= CAST(:journeyDate AS date) - origin_stop.departure_day_offset
                  AND (s.effective_to IS NULL OR s.effective_to >=
                       CAST(:journeyDate AS date) - origin_stop.departure_day_offset)
                  AND (
                       EXISTS (
                           SELECT 1 FROM train_schedule_exceptions exception
                           WHERE exception.schedule_id = s.schedule_id
                             AND exception.service_date =
                                 CAST(:journeyDate AS date) - origin_stop.departure_day_offset
                             AND exception.service_status = 'RUN'
                       )
                       OR (
                           NOT EXISTS (
                               SELECT 1 FROM train_schedule_exceptions exception
                               WHERE exception.schedule_id = s.schedule_id
                                 AND exception.service_date =
                                     CAST(:journeyDate AS date) - origin_stop.departure_day_offset
                           )
                           AND EXISTS (
                               SELECT 1 FROM train_schedule_days operating_day
                               WHERE operating_day.schedule_id = s.schedule_id
                                 AND operating_day.day_of_week = EXTRACT(ISODOW FROM
                                     (CAST(:journeyDate AS date) - origin_stop.departure_day_offset))
                           )
                       )
                  )
            ),
            candidates AS (
                SELECT raw.*,
                       ((service_date + departure_day_offset) + departure_time)
                           AT TIME ZONE origin_timezone AS departure_instant,
                       ((service_date + arrival_day_offset) + arrival_time)
                           AT TIME ZONE destination_timezone AS arrival_instant,
                       EXTRACT(EPOCH FROM (
                           (((service_date + arrival_day_offset) + arrival_time)
                               AT TIME ZONE destination_timezone)
                           -
                           (((service_date + departure_day_offset) + departure_time)
                               AT TIME ZONE origin_timezone)
                       )) / 60 AS duration_minutes
                FROM raw
            )
            """;

    private static final RowMapper<SearchRow> SEARCH_ROW_MAPPER = (result, rowNumber) -> new SearchRow(
            result.getObject("schedule_id", UUID.class),
            result.getObject("route_version_id", UUID.class),
            result.getObject("train_id", UUID.class),
            result.getString("train_number"),
            result.getString("train_name"),
            result.getString("category_code"),
            result.getString("category_name"),
            result.getDate("service_date").toLocalDate(),
            result.getInt("departure_day_offset"),
            result.getInt("arrival_day_offset"),
            result.getTime("departure_time").toLocalTime(),
            result.getTime("arrival_time").toLocalTime(),
            result.getObject("origin_id", UUID.class),
            result.getString("origin_code"),
            result.getString("origin_name"),
            result.getString("origin_city"),
            result.getString("origin_state"),
            result.getString("origin_timezone"),
            result.getObject("destination_id", UUID.class),
            result.getString("destination_code"),
            result.getString("destination_name"),
            result.getString("destination_city"),
            result.getString("destination_state"),
            result.getString("destination_timezone"),
            result.getBigDecimal("distance_km"),
            result.getInt("intermediate_stops")
    );

    private final NamedParameterJdbcTemplate jdbc;
    private final int maximumSearchDays;

    public PublicTrainSearchService(
            NamedParameterJdbcTemplate jdbc,
            @Value("${railconnect.search.maximum-days:365}") int maximumSearchDays
    ) {
        this.jdbc = jdbc;
        if (maximumSearchDays < 1 || maximumSearchDays > 730) {
            throw new IllegalArgumentException("railconnect.search.maximum-days must be between 1 and 730");
        }
        this.maximumSearchDays = maximumSearchDays;
    }

    @Transactional(readOnly = true, timeout = 5)
    public List<StationSuggestion> searchStations(String query, int limit) {
        String normalized = query == null ? "" : query.trim();
        if (normalized.length() < 2 || normalized.length() > 80) {
            throw badRequest("INVALID_STATION_QUERY", "Station search text must contain 2 to 80 characters");
        }
        if (limit < 1 || limit > 20) {
            throw badRequest("INVALID_STATION_LIMIT", "Station suggestion limit must be between 1 and 20");
        }
        String escaped = escapeLike(normalized.toLowerCase());
        return jdbc.query("""
                SELECT station_id, station_code, station_name, city, state, timezone
                FROM stations
                WHERE status = 'ACTIVE'
                  AND (
                       lower(station_code) LIKE :prefix ESCAPE E'\\\\'
                       OR lower(station_name) LIKE :prefix ESCAPE E'\\\\'
                       OR lower(city) LIKE :prefix ESCAPE E'\\\\'
                       OR lower(station_name) LIKE :contains ESCAPE E'\\\\'
                       OR lower(city) LIKE :contains ESCAPE E'\\\\'
                       OR lower(alternate_names::text) LIKE :contains ESCAPE E'\\\\'
                       OR to_tsvector('simple',
                           station_name || ' ' || city || ' ' || alternate_names::text)
                           @@ plainto_tsquery('simple', :query)
                  )
                ORDER BY CASE
                           WHEN lower(station_code) = lower(:query) THEN 0
                           WHEN lower(station_name) = lower(:query) THEN 1
                           WHEN lower(city) = lower(:query) THEN 2
                           WHEN lower(station_code) LIKE :prefix ESCAPE E'\\\\' THEN 3
                           WHEN lower(station_name) LIKE :prefix ESCAPE E'\\\\' THEN 4
                           WHEN lower(city) LIKE :prefix ESCAPE E'\\\\' THEN 5
                           ELSE 6
                         END,
                         station_name, station_code
                LIMIT :limit
                """,
                new MapSqlParameterSource()
                        .addValue("query", normalized)
                        .addValue("prefix", escaped + "%")
                        .addValue("contains", "%" + escaped + "%")
                        .addValue("limit", limit),
                (result, rowNumber) -> new StationSuggestion(
                        result.getObject("station_id", UUID.class),
                        result.getString("station_code"),
                        result.getString("station_name"),
                        result.getString("city"),
                        result.getString("state"),
                        result.getString("timezone")
                ));
    }

    @Transactional(readOnly = true, timeout = 5)
    public StationDetail station(UUID stationId) {
        return jdbc.query("""
                SELECT station_id, station_code, station_name, city, state, country, timezone
                FROM stations
                WHERE station_id = :stationId AND status = 'ACTIVE'
                """,
                Map.of("stationId", stationId),
                (result, rowNumber) -> new StationDetail(
                        result.getObject("station_id", UUID.class),
                        result.getString("station_code"),
                        result.getString("station_name"),
                        result.getString("city"),
                        result.getString("state"),
                        result.getString("country"),
                        result.getString("timezone")
                )).stream().findFirst().orElseThrow(() ->
                new MasterDataException(HttpStatus.NOT_FOUND, "STATION_NOT_FOUND", "Active station not found"));
    }

    @Transactional(readOnly = true, timeout = 5)
    public SearchFilters searchFilters() {
        List<TrainCategoryOption> categories = jdbc.query("""
                SELECT category_code, category_name
                FROM train_categories
                WHERE active = TRUE
                ORDER BY category_name
                """,
                (result, rowNumber) -> new TrainCategoryOption(
                        result.getString("category_code"),
                        result.getString("category_name")
                ));
        List<CoachClass> classes = jdbc.query("""
                SELECT coach_class.class_code, coach_class.class_name, COUNT(*) AS coach_count,
                       string_agg(DISTINCT coach_type.type_name, ',' ORDER BY coach_type.type_name)
                           AS coach_types
                FROM train_compositions composition
                JOIN train_composition_versions version
                  ON version.composition_version_id = composition.current_version_id
                 AND version.status = 'PUBLISHED'
                JOIN train_composition_coaches composition_coach
                  ON composition_coach.composition_version_id = version.composition_version_id
                JOIN coaches coach ON coach.coach_id = composition_coach.coach_id AND coach.active = TRUE
                JOIN coach_configurations configuration
                  ON configuration.configuration_id = coach.configuration_id AND configuration.active = TRUE
                JOIN coach_classes coach_class
                  ON coach_class.coach_class_id = configuration.coach_class_id AND coach_class.active = TRUE
                JOIN coach_types coach_type
                  ON coach_type.coach_type_id = configuration.coach_type_id AND coach_type.active = TRUE
                JOIN trains train ON train.train_id = composition.train_id AND train.status = 'ACTIVE'
                GROUP BY coach_class.class_code, coach_class.class_name
                ORDER BY coach_class.class_code
                """,
                (result, rowNumber) -> new CoachClass(
                        result.getString("class_code"),
                        result.getString("class_name"),
                        result.getInt("coach_count"),
                        splitValues(result.getString("coach_types"))
                ));
        return new SearchFilters(categories, classes);
    }

    public SearchConfig searchConfig() {
        return new SearchConfig(maximumSearchDays);
    }

    @Transactional(readOnly = true, timeout = 5)
    public SearchPage<TrainSearchResult> search(
            UUID fromStationId,
            UUID toStationId,
            LocalDate journeyDate,
            String trainCategory,
            String coachClass,
            LocalTime departureFrom,
            LocalTime departureTo,
            LocalTime arrivalFrom,
            LocalTime arrivalTo,
            Integer maximumDuration,
            String trainQuery,
            String sortBy,
            int page,
            int size
    ) {
        SearchParameters parameters = validateSearch(
                fromStationId, toStationId, journeyDate, trainCategory, coachClass,
                departureFrom, departureTo, arrivalFrom, arrivalTo,
                maximumDuration, trainQuery, page, size);
        MapSqlParameterSource sqlParameters = searchParameters(parameters);
        String filters = filterSql(parameters, sqlParameters, null);
        String from = CANDIDATE_CTE
                + " SELECT COUNT(*) FROM candidates WHERE schedule_match_count = 1 AND duration_minutes > 0 "
                + filters;
        Long total = jdbc.queryForObject(from, sqlParameters, Long.class);

        String orderBy = sortSql(sortBy);
        sqlParameters.addValue("limit", size).addValue("offset", page * size);
        List<SearchRow> rows = jdbc.query(
                CANDIDATE_CTE + """
                        SELECT * FROM candidates
                        WHERE schedule_match_count = 1 AND duration_minutes > 0
                        """ + filters + " ORDER BY " + orderBy + ", train_id LIMIT :limit OFFSET :offset",
                sqlParameters,
                SEARCH_ROW_MAPPER
        );
        return new SearchPage<>(buildResults(rows), page, size, total == null ? 0 : total);
    }

    @Transactional(readOnly = true, timeout = 5)
    public TrainDetail detail(
            UUID trainId,
            UUID fromStationId,
            UUID toStationId,
            LocalDate journeyDate
    ) {
        validateSearch(fromStationId, toStationId, journeyDate, null, null,
                null, null, null, null, null, null, 0, 1);
        MapSqlParameterSource parameters = searchParameters(new SearchParameters(
                fromStationId, toStationId, journeyDate, null, null, null, null,
                null, null, null, null, 0, 1));
        String trainFilter = " AND train_id = :trainId";
        parameters.addValue("trainId", trainId);
        List<SearchRow> rows = jdbc.query(
                CANDIDATE_CTE + """
                        SELECT * FROM candidates
                        WHERE schedule_match_count = 1 AND duration_minutes > 0
                        """ + filterSql(null, parameters, trainFilter)
                        + " ORDER BY departure_instant, train_id LIMIT 1",
                parameters,
                SEARCH_ROW_MAPPER
        );
        if (rows.isEmpty()) {
            throw new MasterDataException(HttpStatus.NOT_FOUND, "TRAIN_JOURNEY_NOT_FOUND",
                    "No published train service matches the selected stations and date");
        }
        SearchRow selected = rows.get(0);
        List<TrainSearchResult> results = buildResults(rows);
        return new TrainDetail(results.get(0), route(selected, fromStationId, toStationId));
    }

    private List<RouteStop> route(SearchRow selected, UUID originId, UUID destinationId) {
        LocalDate serviceDate = selected.serviceDate();
        return jdbc.query("""
                SELECT stop.station_id,
                       station.station_code,
                       station.station_name,
                       station.city,
                       station.timezone,
                       stop.stop_sequence,
                       stop.arrival_time,
                       stop.departure_time,
                       stop.arrival_day_offset,
                       stop.departure_day_offset,
                       stop.halt_minutes,
                       stop.distance_from_source_km
                FROM train_route_stops stop
                JOIN stations station ON station.station_id = stop.station_id
                WHERE stop.route_version_id = :routeVersionId
                  AND station.status = 'ACTIVE'
                ORDER BY stop.stop_sequence
                """,
                Map.of("routeVersionId", selected.routeVersionId()),
                (result, rowNumber) -> {
                    UUID stationId = result.getObject("station_id", UUID.class);
                    String timezone = result.getString("timezone");
                    int arrivalOffset = result.getInt("arrival_day_offset");
                    int departureOffset = result.getInt("departure_day_offset");
                    LocalTime arrivalTime = result.getTime("arrival_time") == null
                            ? null : result.getTime("arrival_time").toLocalTime();
                    LocalTime departureTime = result.getTime("departure_time") == null
                            ? null : result.getTime("departure_time").toLocalTime();
                    return new RouteStop(
                            stationId,
                            result.getString("station_code"),
                            result.getString("station_name"),
                            result.getString("city"),
                            timezone,
                            result.getInt("stop_sequence"),
                            arrivalTime,
                            departureTime,
                            stopDate(serviceDate, arrivalOffset, arrivalTime, timezone),
                            stopDate(serviceDate, departureOffset, departureTime, timezone),
                            arrivalOffset,
                            departureOffset,
                            result.getInt("halt_minutes"),
                            result.getBigDecimal("distance_from_source_km"),
                            stationId.equals(originId),
                            stationId.equals(destinationId)
                    );
                });
    }

    private SearchParameters validateSearch(
            UUID fromStationId,
            UUID toStationId,
            LocalDate journeyDate,
            String trainCategory,
            String coachClass,
            LocalTime departureFrom,
            LocalTime departureTo,
            LocalTime arrivalFrom,
            LocalTime arrivalTo,
            Integer maximumDuration,
            String trainQuery,
            int page,
            int size
    ) {
        if (fromStationId == null || toStationId == null || journeyDate == null) {
            throw badRequest("INVALID_SEARCH", "Origin, destination, and journey date are required");
        }
        if (fromStationId.equals(toStationId)) {
            throw badRequest("SAME_STATION", "Origin and destination must be different stations");
        }
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
        if (journeyDate.isBefore(today)) {
            throw badRequest("PAST_JOURNEY_DATE", "Journey date cannot be in the past");
        }
        if (journeyDate.isAfter(today.plusDays(maximumSearchDays))) {
            throw badRequest("JOURNEY_DATE_TOO_FAR", "Journey date exceeds the configured search horizon");
        }
        if (page < 0 || page > 10_000 || size < 1 || size > 100) {
            throw badRequest("INVALID_PAGE", "Page must be between 0 and 10000 and size between 1 and 100");
        }
        if (maximumDuration != null && (maximumDuration < 1 || maximumDuration > 10_080)) {
            throw badRequest("INVALID_DURATION", "Maximum duration must be at least one minute");
        }
        validateTimeRange(departureFrom, departureTo, "departure");
        validateTimeRange(arrivalFrom, arrivalTo, "arrival");
        validateActiveStations(fromStationId, toStationId);
        return new SearchParameters(
                fromStationId,
                toStationId,
                journeyDate,
                cleanFilter(trainCategory),
                cleanFilter(coachClass),
                departureFrom,
                departureTo,
                arrivalFrom,
                arrivalTo,
                maximumDuration,
                cleanFilter(trainQuery),
                page,
                size
        );
    }

    private void validateActiveStations(UUID fromStationId, UUID toStationId) {
        List<String> timezones = jdbc.query("""
                SELECT timezone FROM stations
                WHERE station_id IN (:fromStationId, :toStationId) AND status = 'ACTIVE'
                """,
                Map.of("fromStationId", fromStationId, "toStationId", toStationId),
                (result, rowNumber) -> result.getString("timezone"));
        if (timezones.size() != 2) {
            throw badRequest("INVALID_STATION", "Select two active stations from the suggestions");
        }
        timezones.forEach(PublicTrainSearchService::zoneId);
    }

    private MapSqlParameterSource searchParameters(SearchParameters search) {
        return new MapSqlParameterSource()
                .addValue("journeyDate", search.journeyDate())
                .addValue("fromStationId", search.fromStationId())
                .addValue("toStationId", search.toStationId());
    }

    private String filterSql(SearchParameters filters, MapSqlParameterSource parameters, String additional) {
        StringBuilder sql = new StringBuilder();
        if (filters != null) {
            if (filters.trainCategory() != null) {
                sql.append(" AND (lower(category_code) = lower(:trainCategory)")
                        .append(" OR lower(category_name) = lower(:trainCategory))");
                parameters.addValue("trainCategory", filters.trainCategory());
            }
            if (filters.coachClass() != null) {
                sql.append("""
                         AND EXISTS (
                             SELECT 1
                             FROM train_compositions composition
                             JOIN train_composition_versions version
                               ON version.composition_version_id = composition.current_version_id
                              AND version.status = 'PUBLISHED'
                             JOIN train_composition_coaches composition_coach
                               ON composition_coach.composition_version_id = version.composition_version_id
                             JOIN coaches coach ON coach.coach_id = composition_coach.coach_id AND coach.active
                             JOIN coach_configurations configuration
                               ON configuration.configuration_id = coach.configuration_id AND configuration.active
                             JOIN coach_classes coach_class
                               ON coach_class.coach_class_id = configuration.coach_class_id AND coach_class.active
                             WHERE composition.train_id = candidates.train_id
                               AND (lower(coach_class.class_code) = lower(:coachClass)
                                    OR lower(coach_class.class_name) = lower(:coachClass))
                         )
                        """);
                parameters.addValue("coachClass", filters.coachClass());
            }
            if (filters.departureFrom() != null) {
                sql.append(" AND departure_time >= :departureFrom");
                parameters.addValue("departureFrom", filters.departureFrom());
            }
            if (filters.departureTo() != null) {
                sql.append(" AND departure_time <= :departureTo");
                parameters.addValue("departureTo", filters.departureTo());
            }
            if (filters.arrivalFrom() != null) {
                sql.append(" AND arrival_time >= :arrivalFrom");
                parameters.addValue("arrivalFrom", filters.arrivalFrom());
            }
            if (filters.arrivalTo() != null) {
                sql.append(" AND arrival_time <= :arrivalTo");
                parameters.addValue("arrivalTo", filters.arrivalTo());
            }
            if (filters.maximumDuration() != null) {
                sql.append(" AND duration_minutes <= :maximumDuration");
                parameters.addValue("maximumDuration", filters.maximumDuration());
            }
            if (filters.trainQuery() != null) {
                sql.append(" AND (lower(train_number) LIKE :trainQuery ESCAPE E'\\\\'")
                        .append(" OR lower(train_name) LIKE :trainQuery ESCAPE E'\\\\')");
                parameters.addValue("trainQuery", "%" + escapeLike(filters.trainQuery().toLowerCase()) + "%");
            }
        }
        if (additional != null) {
            sql.append(additional);
        }
        return sql.toString();
    }

    private List<TrainSearchResult> buildResults(List<SearchRow> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        List<UUID> trainIds = rows.stream().map(SearchRow::trainId).distinct().toList();
        List<UUID> scheduleIds = rows.stream().map(SearchRow::scheduleId).distinct().toList();
        Map<UUID, List<Integer>> operatingDays = operatingDays(scheduleIds);
        Map<UUID, List<CoachClass>> coachClasses = coachClasses(trainIds);
        return rows.stream().map(row -> toResult(row, operatingDays, coachClasses)).toList();
    }

    private TrainSearchResult toResult(
            SearchRow row,
            Map<UUID, List<Integer>> operatingDays,
            Map<UUID, List<CoachClass>> coachClasses
    ) {
        ZoneId originZone = zoneId(row.originTimezone());
        ZoneId destinationZone = zoneId(row.destinationTimezone());
        JourneyTimes times = JourneyTimeCalculator.calculate(
                row.serviceDate(),
                row.departureTime(),
                row.departureDayOffset(),
                originZone,
                row.arrivalTime(),
                row.arrivalDayOffset(),
                destinationZone
        );
        LocalDate journeyDate = times.departureAt().toLocalDate();
        return new TrainSearchResult(
                row.trainId(),
                row.trainNumber(),
                row.trainName(),
                row.categoryCode(),
                row.categoryName(),
                new StationSuggestion(row.originId(), row.originCode(), row.originName(),
                        row.originCity(), row.originState(), row.originTimezone()),
                new StationSuggestion(row.destinationId(), row.destinationCode(), row.destinationName(),
                        row.destinationCity(), row.destinationState(), row.destinationTimezone()),
                journeyDate,
                times.arrivalAt().toLocalDate(),
                times.departureAt(),
                times.arrivalAt(),
                times.durationMinutes(),
                row.distanceKm(),
                row.intermediateStops(),
                operatingDays.getOrDefault(row.scheduleId(), List.of()),
                coachClasses.getOrDefault(row.trainId(), List.of())
        );
    }

    private Map<UUID, List<Integer>> operatingDays(List<UUID> scheduleIds) {
        Map<UUID, List<Integer>> days = new HashMap<>();
        jdbc.query("""
                SELECT schedule_id, string_agg(day_of_week::text, ',' ORDER BY day_of_week) AS days
                FROM train_schedule_days
                WHERE schedule_id IN (:scheduleIds)
                GROUP BY schedule_id
                """,
                Map.of("scheduleIds", scheduleIds),
                (result, rowNumber) -> {
                    String value = result.getString("days");
                    List<Integer> parsed = value == null ? List.of() : java.util.Arrays.stream(value.split(","))
                            .map(Integer::parseInt).toList();
                    days.put(result.getObject("schedule_id", UUID.class), parsed);
                    return null;
                });
        return days;
    }

    private Map<UUID, List<CoachClass>> coachClasses(List<UUID> trainIds) {
        Map<UUID, List<CoachClass>> classes = new HashMap<>();
        jdbc.query("""
                SELECT composition.train_id,
                       coach_class.class_code,
                       coach_class.class_name,
                       COUNT(*) AS coach_count,
                       string_agg(DISTINCT coach_type.type_name, ',' ORDER BY coach_type.type_name)
                           AS coach_types
                FROM train_compositions composition
                JOIN train_composition_versions version
                  ON version.composition_version_id = composition.current_version_id
                 AND version.status = 'PUBLISHED'
                JOIN train_composition_coaches composition_coach
                  ON composition_coach.composition_version_id = version.composition_version_id
                JOIN coaches coach ON coach.coach_id = composition_coach.coach_id AND coach.active = TRUE
                JOIN coach_configurations configuration
                  ON configuration.configuration_id = coach.configuration_id AND configuration.active = TRUE
                JOIN coach_classes coach_class
                  ON coach_class.coach_class_id = configuration.coach_class_id AND coach_class.active = TRUE
                JOIN coach_types coach_type
                  ON coach_type.coach_type_id = configuration.coach_type_id AND coach_type.active = TRUE
                WHERE composition.train_id IN (:trainIds)
                GROUP BY composition.train_id, coach_class.class_code, coach_class.class_name
                ORDER BY coach_class.class_code
                """,
                Map.of("trainIds", trainIds),
                (result, rowNumber) -> {
                    UUID trainId = result.getObject("train_id", UUID.class);
                    classes.computeIfAbsent(trainId, ignored -> new ArrayList<>()).add(
                            new CoachClass(
                                    result.getString("class_code"),
                                    result.getString("class_name"),
                                    result.getInt("coach_count"),
                                    splitValues(result.getString("coach_types"))
                            ));
                    return null;
                });
        return classes;
    }

    private static String sortSql(String sortBy) {
        String normalized = sortBy == null ? "DEPARTURE_EARLIEST" : sortBy.trim().toUpperCase(java.util.Locale.ROOT);
        return switch (normalized) {
            case "DEPARTURE_EARLIEST" -> "departure_instant ASC";
            case "DEPARTURE_LATEST" -> "departure_instant DESC";
            case "ARRIVAL_EARLIEST" -> "arrival_instant ASC";
            case "DURATION_SHORTEST" -> "duration_minutes ASC";
            case "DURATION_LONGEST" -> "duration_minutes DESC";
            case "TRAIN_NUMBER" -> "train_number ASC";
            default -> throw badRequest("INVALID_SORT", "Unsupported train search sort order");
        };
    }

    private static void validateTimeRange(LocalTime from, LocalTime to, String label) {
        if (from != null && to != null && from.isAfter(to)) {
            throw badRequest("INVALID_TIME_RANGE", "The " + label + " start time must not be after its end time");
        }
    }

    private static MasterDataException badRequest(String code, String message) {
        return new MasterDataException(HttpStatus.BAD_REQUEST, code, message);
    }

    private static String cleanFilter(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > 120) {
            throw badRequest("INVALID_SEARCH_FILTER", "Search filters must not exceed 120 characters");
        }
        return normalized;
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static List<String> splitValues(String values) {
        return values == null || values.isBlank()
                ? List.of()
                : java.util.Arrays.stream(values.split(",")).toList();
    }

    private static ZoneId zoneId(String value) {
        try {
            return ZoneId.of(value);
        } catch (DateTimeException exception) {
            throw new MasterDataException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "INVALID_STATION_TIMEZONE", "A configured station timezone is invalid");
        }
    }

    private static LocalDate stopDate(LocalDate serviceDate, int dayOffset, LocalTime time, String timezone) {
        return time == null ? null : serviceDate.plusDays(dayOffset)
                .atTime(time)
                .atZone(zoneId(timezone))
                .withLaterOffsetAtOverlap()
                .toLocalDate();
    }

    private record SearchParameters(
            UUID fromStationId,
            UUID toStationId,
            LocalDate journeyDate,
            String trainCategory,
            String coachClass,
            LocalTime departureFrom,
            LocalTime departureTo,
            LocalTime arrivalFrom,
            LocalTime arrivalTo,
            Integer maximumDuration,
            String trainQuery,
            int page,
            int size
    ) {
    }

    private record SearchRow(
            UUID scheduleId,
            UUID routeVersionId,
            UUID trainId,
            String trainNumber,
            String trainName,
            String categoryCode,
            String categoryName,
            LocalDate serviceDate,
            int departureDayOffset,
            int arrivalDayOffset,
            LocalTime departureTime,
            LocalTime arrivalTime,
            UUID originId,
            String originCode,
            String originName,
            String originCity,
            String originState,
            String originTimezone,
            UUID destinationId,
            String destinationCode,
            String destinationName,
            String destinationCity,
            String destinationState,
            String destinationTimezone,
            java.math.BigDecimal distanceKm,
            int intermediateStops
    ) {
    }
}
