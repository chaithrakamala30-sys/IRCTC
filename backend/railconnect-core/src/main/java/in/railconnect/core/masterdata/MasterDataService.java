package in.railconnect.core.masterdata;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.railconnect.core.api.RequestIds;
import in.railconnect.core.masterdata.MasterDataModels.AuditEntry;
import in.railconnect.core.masterdata.MasterDataModels.CoachClassRequest;
import in.railconnect.core.masterdata.MasterDataModels.CoachConfiguration;
import in.railconnect.core.masterdata.MasterDataModels.CoachConfigurationRequest;
import in.railconnect.core.masterdata.MasterDataModels.CoachLayoutRequest;
import in.railconnect.core.masterdata.MasterDataModels.CoachRequest;
import in.railconnect.core.masterdata.MasterDataModels.CoachSummary;
import in.railconnect.core.masterdata.MasterDataModels.CoachTypeRequest;
import in.railconnect.core.masterdata.MasterDataModels.Composition;
import in.railconnect.core.masterdata.MasterDataModels.CompositionCoach;
import in.railconnect.core.masterdata.MasterDataModels.CompositionCoachInput;
import in.railconnect.core.masterdata.MasterDataModels.CompositionRequest;
import in.railconnect.core.masterdata.MasterDataModels.DashboardStats;
import in.railconnect.core.masterdata.MasterDataModels.FacilityInput;
import in.railconnect.core.masterdata.MasterDataModels.Lookup;
import in.railconnect.core.masterdata.MasterDataModels.LookupRequest;
import in.railconnect.core.masterdata.MasterDataModels.PageResult;
import in.railconnect.core.masterdata.MasterDataModels.RouteStop;
import in.railconnect.core.masterdata.MasterDataModels.RouteStopRequest;
import in.railconnect.core.masterdata.MasterDataModels.RouteVersion;
import in.railconnect.core.masterdata.MasterDataModels.RouteVersionRequest;
import in.railconnect.core.masterdata.MasterDataModels.Schedule;
import in.railconnect.core.masterdata.MasterDataModels.ScheduleExceptionInput;
import in.railconnect.core.masterdata.MasterDataModels.ScheduleRequest;
import in.railconnect.core.masterdata.MasterDataModels.Station;
import in.railconnect.core.masterdata.MasterDataModels.StationRequest;
import in.railconnect.core.masterdata.MasterDataModels.StationStatusRequest;
import in.railconnect.core.masterdata.MasterDataModels.StationVerificationRequest;
import in.railconnect.core.masterdata.MasterDataModels.StationVersion;
import in.railconnect.core.masterdata.MasterDataModels.Train;
import in.railconnect.core.masterdata.MasterDataModels.TrainRequest;
import in.railconnect.core.masterdata.MasterDataModels.TrainStatusRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class MasterDataService {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public MasterDataService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public DashboardStats dashboard() {
        return new DashboardStats(
                count("SELECT COUNT(*) FROM stations"),
                count("SELECT COUNT(*) FROM stations WHERE status = 'ACTIVE'"),
                count("SELECT COUNT(*) FROM trains"),
                count("SELECT COUNT(*) FROM trains WHERE status = 'ACTIVE'"),
                count("SELECT COUNT(*) FROM train_route_versions WHERE status = 'PUBLISHED'"),
                count("SELECT COUNT(*) FROM coach_classes WHERE active"),
                count("SELECT COUNT(*) FROM coaches WHERE active"));
    }

    public PageResult<Station> stations(
            int page,
            int size,
            String search,
            String city,
            String state,
            String status
    ) {
        validatePage(page, size);
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        List<Object> parameters = new ArrayList<>();
        appendLike(where, parameters, " AND (lower(station_code) LIKE ? OR lower(station_name) LIKE ?)", search);
        appendLike(where, parameters, " AND lower(city) LIKE ?", city);
        appendLike(where, parameters, " AND lower(state) LIKE ?", state);
        appendEqual(where, parameters, " AND status = ?", status);
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM stations" + where,
                Long.class,
                parameters.toArray()
        );
        List<Object> pageParameters = new ArrayList<>(parameters);
        pageParameters.add(size);
        pageParameters.add(page * size);
        List<Station> items = jdbcTemplate.query(
                "SELECT * FROM stations" + where
                        + " ORDER BY station_name, station_code LIMIT ? OFFSET ?",
                stationRowMapper(),
                pageParameters.toArray()
        );
        return new PageResult<>(items, page, size, total == null ? 0 : total);
    }

    public Station station(UUID stationId) {
        return jdbcTemplate.query(
                "SELECT * FROM stations WHERE station_id = ?",
                stationRowMapper(),
                stationId
        ).stream().findFirst().orElseThrow(() -> notFound("STATION_NOT_FOUND", "Station not found"));
    }

    public List<StationVersion> stationVersions(UUID stationId) {
        station(stationId);
        return jdbcTemplate.query(
                """
                        SELECT station_version_id, station_id, version_number, version_type,
                               snapshot::text, change_reason, changed_by, created_at
                        FROM station_versions
                        WHERE station_id = ?
                        ORDER BY version_number DESC
                        """,
                (result, rowNumber) -> new StationVersion(
                        result.getObject("station_version_id", UUID.class),
                        result.getObject("station_id", UUID.class),
                        result.getInt("version_number"),
                        result.getString("version_type"),
                        parseMap(result.getString("snapshot")),
                        result.getString("change_reason"),
                        result.getObject("changed_by", UUID.class),
                        result.getTimestamp("created_at").toInstant()
                ),
                stationId
        );
    }

    @Transactional
    public Station createStation(StationRequest request, UUID actor, HttpServletRequest servletRequest) {
        validateStation(request);
        UUID stationId = UUID.randomUUID();
        try {
            jdbcTemplate.update(
                    """
                            INSERT INTO stations (
                                station_id, station_code, station_name, alternate_names, city, state, country,
                                latitude, longitude, timezone, address, zone_id, division_id,
                                station_category, code_verified, status, created_by, updated_by
                            ) VALUES (?, upper(?), ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, FALSE, 'DRAFT', ?, ?)
                            """,
                    stationId,
                    request.stationCode().trim(),
                    request.stationName().trim(),
                    json(request.alternateNames() == null ? List.of() : request.alternateNames()),
                    request.city().trim(),
                    request.state().trim(),
                    request.country().trim(),
                    request.latitude(),
                    request.longitude(),
                    request.timezone().trim(),
                    blankToNull(request.address()),
                    request.zoneId(),
                    request.divisionId(),
                    blankToNull(request.stationCategory()),
                    actor,
                    actor
            );
            replaceFacilities(stationId, request.facilities());
        } catch (DuplicateKeyException exception) {
            throw conflict("STATION_CODE_EXISTS", "A station with this code already exists");
        }
        Station created = station(stationId);
        recordStationVersion(created, actor, "CREATED", null);
        audit(actor, "STATION", stationId, "CREATED", null, created, null);
        return created;
    }

    @Transactional
    public Station updateStation(
            UUID stationId,
            StationRequest request,
            UUID actor,
            HttpServletRequest servletRequest
    ) {
        Station previous = station(stationId);
        validateStation(request);
        boolean stationCodeChanged = !previous.stationCode().equalsIgnoreCase(request.stationCode().trim());
        if (stationCodeChanged && "ACTIVE".equals(previous.status())) {
            throw conflict("ACTIVE_STATION_CODE_CHANGE", "Deactivate the station before changing its code");
        }
        try {
            jdbcTemplate.update(
                    """
                            UPDATE stations
                            SET station_code = upper(?), station_name = ?, alternate_names = ?::jsonb,
                                city = ?, state = ?, country = ?, latitude = ?, longitude = ?,
                                timezone = ?, address = ?, zone_id = ?, division_id = ?,
                                station_category = ?,
                                code_verified = CASE WHEN ? THEN FALSE ELSE code_verified END,
                                updated_by = ?,
                                updated_at = CURRENT_TIMESTAMP
                            WHERE station_id = ?
                            """,
                    request.stationCode().trim(),
                    request.stationName().trim(),
                    json(request.alternateNames() == null ? List.of() : request.alternateNames()),
                    request.city().trim(),
                    request.state().trim(),
                    request.country().trim(),
                    request.latitude(),
                    request.longitude(),
                    request.timezone().trim(),
                    blankToNull(request.address()),
                    request.zoneId(),
                    request.divisionId(),
                    blankToNull(request.stationCategory()),
                    stationCodeChanged,
                    actor,
                    stationId
            );
            replaceFacilities(stationId, request.facilities());
        } catch (DuplicateKeyException exception) {
            throw conflict("STATION_CODE_EXISTS", "A station with this code already exists");
        }
        Station updated = station(stationId);
        recordStationVersion(updated, actor, "UPDATED", null);
        audit(actor, "STATION", stationId, "UPDATED", previous, updated, null);
        return updated;
    }

    @Transactional
    public Station changeStationStatus(
            UUID stationId,
            StationStatusRequest request,
            UUID actor,
            HttpServletRequest servletRequest
    ) {
        Station previous = station(stationId);
        String status = request.status().trim().toUpperCase();
        if (!Set.of("ACTIVE", "INACTIVE", "RETIRED").contains(status)) {
            throw badRequest("INVALID_STATION_STATUS", "Station status must be ACTIVE, INACTIVE, or RETIRED");
        }
        if ("ACTIVE".equals(status) && !previous.codeVerified()) {
            throw badRequest("STATION_CODE_NOT_VERIFIED", "Verify the station code before activating the station");
        }
        jdbcTemplate.update(
                "UPDATE stations SET status = ?, updated_by = ?, updated_at = CURRENT_TIMESTAMP WHERE station_id = ?",
                status,
                actor,
                stationId
        );
        Station updated = station(stationId);
        recordStationVersion(updated, actor, "STATUS_CHANGED", request.reason());
        audit(actor, "STATION", stationId, "STATUS_CHANGED", previous, updated, request.reason());
        return updated;
    }

    @Transactional
    public Station verifyStationCode(
            UUID stationId,
            StationVerificationRequest request,
            UUID actor,
            HttpServletRequest servletRequest
    ) {
        Station previous = station(stationId);
        if ("ACTIVE".equals(previous.status()) && !request.verified()) {
            throw conflict("ACTIVE_STATION_CODE_REQUIRED", "An active station's code cannot be marked unverified");
        }
        jdbcTemplate.update(
                "UPDATE stations SET code_verified = ?, updated_by = ?, updated_at = CURRENT_TIMESTAMP WHERE station_id = ?",
                request.verified(),
                actor,
                stationId
        );
        Station updated = station(stationId);
        recordStationVersion(updated, actor, "VERIFIED", request.reason());
        audit(actor, "STATION", stationId, "UPDATED", previous, updated, request.reason());
        return updated;
    }

    public List<Lookup> categories() {
        return lookupRows("train_categories", "category_id", "category_code", "category_name");
    }

    public List<Lookup> trainTypes() {
        return lookupRows("train_types", "train_type_id", "type_code", "type_name");
    }

    @Transactional
    public Lookup createCategory(LookupRequest request, UUID actor, HttpServletRequest servletRequest) {
        return createLookup(
                "train_categories", "category_id", "category_code", "category_name",
                request, actor, servletRequest, "TRAIN_CATEGORY");
    }

    @Transactional
    public Lookup createTrainType(LookupRequest request, UUID actor, HttpServletRequest servletRequest) {
        return createLookup(
                "train_types", "train_type_id", "type_code", "type_name",
                request, actor, servletRequest, "TRAIN_TYPE");
    }

    public PageResult<Train> trains(int page, int size, String search, UUID categoryId, String status) {
        validatePage(page, size);
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        List<Object> parameters = new ArrayList<>();
        appendLike(where, parameters,
                " AND (lower(t.train_number) LIKE ? OR lower(t.train_name) LIKE ?)", search);
        if (categoryId != null) {
            where.append(" AND t.category_id = ?");
            parameters.add(categoryId);
        }
        appendEqual(where, parameters, " AND t.status = ?", status);
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM trains t" + where, Long.class, parameters.toArray());
        List<Object> pageParameters = new ArrayList<>(parameters);
        pageParameters.add(size);
        pageParameters.add(page * size);
        List<Train> items = jdbcTemplate.query(
                trainSelect() + where + " ORDER BY t.train_number LIMIT ? OFFSET ?",
                trainRowMapper(),
                pageParameters.toArray()
        );
        return new PageResult<>(items, page, size, total == null ? 0 : total);
    }

    public Train train(UUID trainId) {
        return jdbcTemplate.query(
                trainSelect() + " WHERE t.train_id = ?",
                trainRowMapper(),
                trainId
        ).stream().findFirst().orElseThrow(() -> notFound("TRAIN_NOT_FOUND", "Train not found"));
    }

    @Transactional
    public Train createTrain(TrainRequest request, UUID actor, HttpServletRequest servletRequest) {
        validateTrain(request);
        UUID trainId = UUID.randomUUID();
        try {
            jdbcTemplate.update(
                    """
                            INSERT INTO trains (
                                train_id, train_number, train_name, category_id, train_type_id,
                                source_station_id, destination_station_id, route_id, total_distance_km,
                                description, effective_from, effective_to, created_by, updated_by
                            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    trainId,
                    request.trainNumber().trim(),
                    request.trainName().trim(),
                    request.categoryId(),
                    request.trainTypeId(),
                    request.sourceStationId(),
                    request.destinationStationId(),
                    request.routeId(),
                    request.totalDistanceKm(),
                    blankToNull(request.description()),
                    request.effectiveFrom(),
                    request.effectiveTo(),
                    actor,
                    actor
            );
        } catch (DuplicateKeyException exception) {
            throw conflict("TRAIN_NUMBER_EXISTS", "A train with this identifier already exists");
        }
        Train created = train(trainId);
        audit(actor, "TRAIN", trainId, "CREATED", null, created, null);
        return created;
    }

    @Transactional
    public Train updateTrain(
            UUID trainId,
            TrainRequest request,
            UUID actor,
            HttpServletRequest servletRequest
    ) {
        Train previous = train(trainId);
        validateTrain(request);
        try {
            jdbcTemplate.update(
                    """
                            UPDATE trains SET train_number = ?, train_name = ?, category_id = ?,
                                train_type_id = ?, source_station_id = ?, destination_station_id = ?,
                                route_id = ?, total_distance_km = ?, description = ?, effective_from = ?,
                                effective_to = ?, updated_by = ?, updated_at = CURRENT_TIMESTAMP
                            WHERE train_id = ?
                            """,
                    request.trainNumber().trim(),
                    request.trainName().trim(),
                    request.categoryId(),
                    request.trainTypeId(),
                    request.sourceStationId(),
                    request.destinationStationId(),
                    request.routeId(),
                    request.totalDistanceKm(),
                    blankToNull(request.description()),
                    request.effectiveFrom(),
                    request.effectiveTo(),
                    actor,
                    trainId
            );
        } catch (DuplicateKeyException exception) {
            throw conflict("TRAIN_NUMBER_EXISTS", "A train with this identifier already exists");
        }
        Train updated = train(trainId);
        audit(actor, "TRAIN", trainId, "UPDATED", previous, updated, null);
        return updated;
    }

    @Transactional
    public Train changeTrainStatus(
            UUID trainId,
            TrainStatusRequest request,
            UUID actor,
            HttpServletRequest servletRequest
    ) {
        Train previous = train(trainId);
        String status = request.status().trim().toUpperCase();
        if (!Set.of("ACTIVE", "SUSPENDED", "RETIRED").contains(status)) {
            throw badRequest("INVALID_TRAIN_STATUS", "Train status must be ACTIVE, SUSPENDED, or RETIRED");
        }
        if ("ACTIVE".equals(status)) {
            if (previous.routeId() == null) {
                throw badRequest("TRAIN_ROUTE_REQUIRED", "Assign a published route before activating the train");
            }
            Integer publishedRoute = jdbcTemplate.queryForObject(
                    """
                            SELECT COUNT(*) FROM train_route_versions
                            WHERE route_id = ? AND status = 'PUBLISHED'
                              AND effective_from <= CURRENT_DATE
                              AND (effective_to IS NULL OR effective_to >= CURRENT_DATE)
                            """,
                    Integer.class,
                    previous.routeId()
            );
            if (publishedRoute == null || publishedRoute == 0) {
                throw badRequest("TRAIN_ROUTE_NOT_PUBLISHED", "Train requires a currently effective published route");
            }
            if (previous.effectiveFrom().isAfter(LocalDate.now())
                    || (previous.effectiveTo() != null && previous.effectiveTo().isBefore(LocalDate.now()))) {
                throw badRequest("TRAIN_NOT_EFFECTIVE", "Train cannot be activated outside its effective dates");
            }
        }
        jdbcTemplate.update(
                "UPDATE trains SET status = ?, updated_by = ?, updated_at = CURRENT_TIMESTAMP WHERE train_id = ?",
                status,
                actor,
                trainId
        );
        Train updated = train(trainId);
        audit(actor, "TRAIN", trainId, "STATUS_CHANGED", previous, updated, request.reason());
        return updated;
    }

    public PageResult<RouteVersion> routes(int page, int size, String search, String status) {
        validatePage(page, size);
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        List<Object> parameters = new ArrayList<>();
        appendLike(where, parameters, " AND lower(r.route_name) LIKE ?", search);
        appendEqual(where, parameters, " AND rv.status = ?", status);
        String base = """
                FROM train_routes r
                JOIN train_route_versions rv ON rv.route_id = r.route_id
                JOIN stations source ON source.station_id = rv.source_station_id
                JOIN stations destination ON destination.station_id = rv.destination_station_id
                """;
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) " + base + where, Long.class, parameters.toArray());
        List<Object> pageParameters = new ArrayList<>(parameters);
        pageParameters.add(size);
        pageParameters.add(page * size);
        List<RouteVersion> items = jdbcTemplate.query(
                "SELECT rv.*, r.route_name, source.station_code AS source_code, "
                        + "destination.station_code AS destination_code " + base + where
                        + " ORDER BY lower(r.route_name), rv.version_number DESC LIMIT ? OFFSET ?",
                routeVersionRowMapper(),
                pageParameters.toArray()
        );
        return new PageResult<>(items, page, size, total == null ? 0 : total);
    }

    public RouteVersion routeVersion(UUID routeVersionId) {
        return jdbcTemplate.query(
                """
                        SELECT rv.*, r.route_name, source.station_code AS source_code,
                               destination.station_code AS destination_code
                        FROM train_route_versions rv
                        JOIN train_routes r ON r.route_id = rv.route_id
                        JOIN stations source ON source.station_id = rv.source_station_id
                        JOIN stations destination ON destination.station_id = rv.destination_station_id
                        WHERE rv.route_version_id = ?
                        """,
                routeVersionRowMapper(),
                routeVersionId
        ).stream().findFirst().orElseThrow(() -> notFound("ROUTE_VERSION_NOT_FOUND", "Route version not found"));
    }

    @Transactional
    public RouteVersion createRoute(RouteVersionRequest request, UUID actor, HttpServletRequest servletRequest) {
        validateRoute(request);
        UUID routeId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO train_routes (route_id, route_name, created_by, updated_by) VALUES (?, ?, ?, ?)",
                routeId,
                request.routeName().trim(),
                actor,
                actor
        );
        RouteVersion created = saveRouteVersion(routeId, request, actor);
        audit(actor, "ROUTE", routeId, "CREATED", null, Map.of("routeName", request.routeName()), null);
        audit(actor, "ROUTE_VERSION", created.routeVersionId(), "VERSION_CREATED", null, created, request.changeReason());
        return created;
    }

    @Transactional
    public RouteVersion createRouteVersion(
            UUID routeId,
            RouteVersionRequest request,
            UUID actor,
            HttpServletRequest servletRequest
    ) {
        String existingStatus = jdbcTemplate.query(
                "SELECT status FROM train_routes WHERE route_id = ?",
                (result, rowNumber) -> result.getString("status"),
                routeId
        ).stream().findFirst().orElseThrow(() -> notFound("ROUTE_NOT_FOUND", "Route not found"));
        if ("RETIRED".equals(existingStatus)) {
            throw conflict("ROUTE_RETIRED", "A retired route cannot be changed");
        }
        validateRoute(request);
        jdbcTemplate.update(
                "UPDATE train_routes SET route_name = ?, updated_by = ?, updated_at = CURRENT_TIMESTAMP WHERE route_id = ?",
                request.routeName().trim(),
                actor,
                routeId
        );
        RouteVersion created = saveRouteVersion(routeId, request, actor);
        audit(actor, "ROUTE_VERSION", created.routeVersionId(), "VERSION_CREATED", null, created, request.changeReason());
        return created;
    }

    @Transactional
    public RouteVersion publishRouteVersion(UUID routeVersionId, UUID actor, HttpServletRequest servletRequest) {
        RouteVersion draft = routeVersion(routeVersionId);
        if (!"DRAFT".equals(draft.status())) {
            throw conflict("ROUTE_VERSION_NOT_DRAFT", "Only a draft route version can be published");
        }
        if (draft.stops().size() < 2) {
            throw badRequest("ROUTE_NOT_PUBLISHABLE", "A route must have an origin and a terminal stop");
        }
        draft.stops().forEach(stop -> requireActiveStation(stop.stationId()));
        jdbcTemplate.update(
                """
                        UPDATE train_route_versions
                        SET status = 'SUPERSEDED'
                        WHERE route_id = ? AND status = 'PUBLISHED'
                        """,
                draft.routeId()
        );
        jdbcTemplate.update(
                """
                        UPDATE train_route_versions
                        SET status = 'PUBLISHED', published_by = ?, published_at = CURRENT_TIMESTAMP
                        WHERE route_version_id = ?
                        """,
                actor,
                routeVersionId
        );
        jdbcTemplate.update(
                "UPDATE train_routes SET status = 'PUBLISHED', updated_by = ?, updated_at = CURRENT_TIMESTAMP WHERE route_id = ?",
                actor,
                draft.routeId()
        );
        RouteVersion published = routeVersion(routeVersionId);
        audit(actor, "ROUTE_VERSION", routeVersionId, "PUBLISHED", draft, published, null);
        return published;
    }

    public PageResult<Schedule> schedules(int page, int size, UUID trainId, String status) {
        validatePage(page, size);
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        List<Object> parameters = new ArrayList<>();
        if (trainId != null) {
            where.append(" AND train_id = ?");
            parameters.add(trainId);
        }
        appendEqual(where, parameters, " AND status = ?", status);
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM train_schedules" + where, Long.class, parameters.toArray());
        List<Object> pageParameters = new ArrayList<>(parameters);
        pageParameters.add(size);
        pageParameters.add(page * size);
        List<UUID> ids = jdbcTemplate.query(
                "SELECT schedule_id FROM train_schedules" + where
                        + " ORDER BY created_at DESC LIMIT ? OFFSET ?",
                (result, rowNumber) -> result.getObject("schedule_id", UUID.class),
                pageParameters.toArray()
        );
        return new PageResult<>(
                ids.stream().map(this::schedule).toList(),
                page,
                size,
                total == null ? 0 : total
        );
    }

    public Schedule schedule(UUID scheduleId) {
        return jdbcTemplate.query(
                "SELECT * FROM train_schedules WHERE schedule_id = ?",
                (result, rowNumber) -> mapSchedule(result),
                scheduleId
        ).stream().findFirst()
                .orElseThrow(() -> notFound("SCHEDULE_NOT_FOUND", "Schedule not found"));
    }

    @Transactional
    public Schedule createSchedule(ScheduleRequest request, UUID actor, HttpServletRequest servletRequest) {
        validateSchedule(request);
        Integer version = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(version_number), 0) + 1 FROM train_schedules WHERE train_id = ?",
                Integer.class,
                request.trainId()
        );
        UUID scheduleId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                        INSERT INTO train_schedules (
                            schedule_id, train_id, route_version_id, version_number, timezone,
                            effective_from, effective_to, change_reason, created_by
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                scheduleId,
                request.trainId(),
                request.routeVersionId(),
                version,
                request.timezone().trim(),
                request.effectiveFrom(),
                request.effectiveTo(),
                blankToNull(request.changeReason()),
                actor
        );
        for (Integer day : request.operatingDays()) {
            jdbcTemplate.update(
                    "INSERT INTO train_schedule_days (schedule_id, day_of_week) VALUES (?, ?)",
                    scheduleId,
                    day
            );
        }
        for (ScheduleExceptionInput exception : request.exceptions() == null
                ? List.<ScheduleExceptionInput>of() : request.exceptions()) {
            jdbcTemplate.update(
                    """
                            INSERT INTO train_schedule_exceptions (
                                schedule_id, service_date, service_status, change_reason, created_by
                            ) VALUES (?, ?, ?, ?, ?)
                            """,
                    scheduleId,
                    exception.serviceDate(),
                    exception.serviceStatus().trim().toUpperCase(),
                    blankToNull(exception.changeReason()),
                    actor
            );
        }
        Schedule created = schedule(scheduleId);
        audit(actor, "SCHEDULE", scheduleId, "VERSION_CREATED", null, created, request.changeReason());
        return created;
    }

    @Transactional
    public Schedule publishSchedule(UUID scheduleId, UUID actor, HttpServletRequest servletRequest) {
        Schedule draft = schedule(scheduleId);
        if (!"DRAFT".equals(draft.status())) {
            throw conflict("SCHEDULE_NOT_DRAFT", "Only a draft schedule can be published");
        }
        int publishedRouteCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM train_route_versions WHERE route_version_id = ? AND status = 'PUBLISHED'",
                Integer.class,
                draft.routeVersionId()
        );
        if (publishedRouteCount != 1) {
            throw badRequest("SCHEDULE_ROUTE_NOT_PUBLISHED", "The schedule route version must be published first");
        }
        jdbcTemplate.update(
                "UPDATE train_schedules SET status = 'SUPERSEDED' WHERE train_id = ? AND status = 'PUBLISHED'",
                draft.trainId()
        );
        jdbcTemplate.update(
                """
                        UPDATE train_schedules
                        SET status = 'PUBLISHED', published_by = ?, published_at = CURRENT_TIMESTAMP
                        WHERE schedule_id = ?
                        """,
                actor,
                scheduleId
        );
        Schedule published = schedule(scheduleId);
        audit(actor, "SCHEDULE", scheduleId, "PUBLISHED", draft, published, null);
        return published;
    }

    public List<Lookup> coachClasses() {
        return lookupRows("coach_classes", "coach_class_id", "class_code", "class_name");
    }

    public List<Lookup> coachTypes() {
        return lookupRows("coach_types", "coach_type_id", "type_code", "type_name");
    }

    @Transactional
    public Lookup createCoachClass(CoachClassRequest request, UUID actor, HttpServletRequest servletRequest) {
        if (!Set.of("SEATING", "SLEEPING", "MIXED", "OTHER")
                .contains(request.accommodationType().trim().toUpperCase())) {
            throw badRequest("INVALID_COACH_ACCOMMODATION", "Coach class accommodation type is invalid");
        }
        UUID id = UUID.randomUUID();
        try {
            jdbcTemplate.update(
                    """
                            INSERT INTO coach_classes (
                                coach_class_id, class_code, class_name, description,
                                accommodation_type, fare_category, active, created_by
                            ) VALUES (?, upper(?), ?, ?, ?, ?, ?, ?)
                            """,
                    id,
                    request.classCode().trim(),
                    request.className().trim(),
                    blankToNull(request.description()),
                    request.accommodationType().trim().toUpperCase(),
                    blankToNull(request.fareCategory()),
                    request.active(),
                    actor
            );
        } catch (DuplicateKeyException exception) {
            throw conflict("COACH_CLASS_EXISTS", "A coach class with this code or name already exists");
        }
        Lookup created = coachClass(id);
        audit(actor, "COACH_CLASS", id, "CREATED", null, created, null);
        return created;
    }

    @Transactional
    public Lookup createCoachType(CoachTypeRequest request, UUID actor, HttpServletRequest servletRequest) {
        UUID id = UUID.randomUUID();
        try {
            jdbcTemplate.update(
                    """
                            INSERT INTO coach_types (coach_type_id, type_code, type_name, description, active, created_by)
                            VALUES (?, upper(?), ?, ?, ?, ?)
                            """,
                    id,
                    request.typeCode().trim(),
                    request.typeName().trim(),
                    blankToNull(request.description()),
                    request.active(),
                    actor
            );
        } catch (DuplicateKeyException exception) {
            throw conflict("COACH_TYPE_EXISTS", "A coach type with this code or name already exists");
        }
        Lookup created = coachType(id);
        audit(actor, "COACH_TYPE", id, "CREATED", null, created, null);
        return created;
    }

    public List<CoachConfiguration> coachConfigurations() {
        return jdbcTemplate.query(
                "SELECT configuration_id FROM coach_configurations ORDER BY configuration_code",
                (result, rowNumber) -> result.getObject("configuration_id", UUID.class)
        ).stream().map(this::coachConfiguration).toList();
    }

    public CoachConfiguration coachConfiguration(UUID configurationId) {
        return jdbcTemplate.query(
                """
                        SELECT c.*, cc.class_name, ct.type_name,
                               COALESCE(layout.version_number, 0) AS layout_version,
                               layout.status AS layout_status
                        FROM coach_configurations c
                        JOIN coach_classes cc ON cc.coach_class_id = c.coach_class_id
                        JOIN coach_types ct ON ct.coach_type_id = c.coach_type_id
                        LEFT JOIN LATERAL (
                            SELECT version_number, status
                            FROM accommodation_layouts
                            WHERE configuration_id = c.configuration_id
                            ORDER BY version_number DESC
                            LIMIT 1
                        ) layout ON TRUE
                        WHERE c.configuration_id = ?
                        """,
                (result, rowNumber) -> mapCoachConfiguration(result),
                configurationId
        ).stream().findFirst().orElseThrow(() -> notFound("COACH_CONFIGURATION_NOT_FOUND", "Coach configuration not found"));
    }

    @Transactional
    public CoachConfiguration createCoachConfiguration(
            CoachConfigurationRequest request,
            UUID actor,
            HttpServletRequest servletRequest
    ) {
        validateAccommodationSet(request.accommodations(), request.capacity());
        requireActiveLookup("coach_classes", "coach_class_id", request.coachClassId());
        requireActiveLookup("coach_types", "coach_type_id", request.coachTypeId());
        UUID configurationId = UUID.randomUUID();
        try {
            jdbcTemplate.update(
                    """
                            INSERT INTO coach_configurations (
                                configuration_id, configuration_code, configuration_name, coach_class_id,
                                coach_type_id, capacity, created_by
                            ) VALUES (?, upper(?), ?, ?, ?, ?, ?)
                            """,
                    configurationId,
                    request.configurationCode().trim(),
                    request.configurationName().trim(),
                    request.coachClassId(),
                    request.coachTypeId(),
                    request.capacity(),
                    actor
            );
        } catch (DuplicateKeyException exception) {
            throw conflict("COACH_CONFIGURATION_EXISTS", "A coach configuration with this code/name already exists");
        }
        int layoutVersion = createLayout(configurationId, request.accommodations(), actor);
        CoachConfiguration created = coachConfiguration(configurationId);
        audit(actor, "COACH_CONFIGURATION", configurationId, "CREATED", null, created, request.changeReason());
        return created;
    }

    @Transactional
    public CoachConfiguration createCoachLayoutVersion(
            UUID configurationId,
            CoachLayoutRequest request,
            UUID actor,
            HttpServletRequest servletRequest
    ) {
        CoachConfiguration previous = coachConfiguration(configurationId);
        validateAccommodationSet(request.accommodations(), previous.capacity());
        createLayout(configurationId, request.accommodations(), actor);
        CoachConfiguration updated = coachConfiguration(configurationId);
        audit(actor, "COACH_CONFIGURATION", configurationId, "VERSION_CREATED", previous, updated, request.changeReason());
        return updated;
    }

    @Transactional
    public CoachConfiguration publishCoachLayout(
            UUID configurationId,
            UUID actor,
            HttpServletRequest servletRequest
    ) {
        CoachConfiguration before = coachConfiguration(configurationId);
        if (before.layoutVersion() == 0) {
            throw badRequest("COACH_LAYOUT_MISSING", "Create a coach layout before publishing");
        }
        UUID layoutId = jdbcTemplate.queryForObject(
                "SELECT layout_id FROM accommodation_layouts WHERE configuration_id = ? AND version_number = ?",
                UUID.class,
                configurationId,
                before.layoutVersion()
        );
        String layoutStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM accommodation_layouts WHERE layout_id = ?",
                String.class,
                layoutId
        );
        if (!"DRAFT".equals(layoutStatus)) {
            throw conflict("COACH_LAYOUT_NOT_DRAFT", "Only a draft coach layout can be published");
        }
        jdbcTemplate.update(
                "UPDATE accommodation_layouts SET status = 'SUPERSEDED' WHERE configuration_id = ? AND status = 'PUBLISHED'",
                configurationId
        );
        jdbcTemplate.update(
                """
                        UPDATE accommodation_layouts
                        SET status = 'PUBLISHED', published_by = ?, published_at = CURRENT_TIMESTAMP
                        WHERE layout_id = ?
                        """,
                actor,
                layoutId
        );
        CoachConfiguration after = coachConfiguration(configurationId);
        audit(actor, "COACH_CONFIGURATION", configurationId, "PUBLISHED", before, after, null);
        return after;
    }

    public List<CoachSummary> coaches() {
        return jdbcTemplate.query(
                """
                        SELECT c.coach_id, c.coach_code, c.configuration_id, c.active,
                               cc.configuration_name, cl.class_name, ct.type_name
                        FROM coaches c
                        JOIN coach_configurations cc ON cc.configuration_id = c.configuration_id
                        JOIN coach_classes cl ON cl.coach_class_id = cc.coach_class_id
                        JOIN coach_types ct ON ct.coach_type_id = cc.coach_type_id
                        ORDER BY c.coach_code
                        """,
                coachRowMapper()
        );
    }

    @Transactional
    public CoachSummary createCoach(CoachRequest request, UUID actor, HttpServletRequest servletRequest) {
        CoachConfiguration configuration = coachConfiguration(request.configurationId());
        if (!configuration.active() || !hasPublishedLayout(request.configurationId())) {
            throw badRequest("COACH_CONFIGURATION_NOT_READY", "Coach configuration must be active and have a published layout");
        }
        UUID coachId = UUID.randomUUID();
        try {
            jdbcTemplate.update(
                    "INSERT INTO coaches (coach_id, coach_code, configuration_id, created_by) VALUES (?, upper(?), ?, ?)",
                    coachId,
                    request.coachCode().trim(),
                    request.configurationId(),
                    actor
            );
        } catch (DuplicateKeyException exception) {
            throw conflict("COACH_CODE_EXISTS", "A coach with this code already exists");
        }
        CoachSummary created = coaches().stream()
                .filter(coach -> coach.coachId().equals(coachId))
                .findFirst().orElseThrow(() -> new IllegalStateException("Created coach could not be read"));
        audit(actor, "COACH", coachId, "CREATED", null, created, null);
        return created;
    }

    @Transactional
    public Composition saveComposition(
            UUID trainId,
            CompositionRequest request,
            UUID actor,
            HttpServletRequest servletRequest
    ) {
        train(trainId);
        validateComposition(request);
        UUID compositionId = jdbcTemplate.query(
                "SELECT composition_id FROM train_compositions WHERE train_id = ?",
                (result, rowNumber) -> result.getObject("composition_id", UUID.class),
                trainId
        ).stream().findFirst().orElse(null);
        if (compositionId == null) {
            compositionId = UUID.randomUUID();
            jdbcTemplate.update(
                    "INSERT INTO train_compositions (composition_id, train_id, created_by, updated_by) VALUES (?, ?, ?, ?)",
                    compositionId,
                    trainId,
                    actor,
                    actor
            );
        }
        UUID versionId = UUID.randomUUID();
        UUID finalCompositionId = compositionId;
        Integer versionNumber = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(version_number), 0) + 1 FROM train_composition_versions WHERE composition_id = ?",
                Integer.class,
                compositionId
        );
        List<CompositionCoach> snapshot = new ArrayList<>();
        int totalCapacity = 0;
        for (CompositionCoachInput coach : request.coaches()) {
            CoachSummary summary = coach(coach.coachId());
            CoachConfiguration configuration = coachConfiguration(summary.configurationId());
            if (!summary.active() || !configuration.active() || !hasPublishedLayout(configuration.configurationId())) {
                throw badRequest("COACH_NOT_COMPOSABLE", "Every coach must be active and use a published configuration");
            }
            int capacity = configuration.capacity();
            totalCapacity += capacity;
            snapshot.add(new CompositionCoach(
                    coach.coachId(),
                    coach.coachNumber().trim(),
                    coach.position(),
                    capacity,
                    configuration.configurationId(),
                    configuration.configurationName(),
                    configuration.coachClassName(),
                    configuration.coachTypeName()
            ));
        }
        jdbcTemplate.update(
                """
                        INSERT INTO train_composition_versions (
                            composition_version_id, composition_id, version_number, total_capacity,
                            snapshot, change_reason, created_by
                        ) VALUES (?, ?, ?, ?, ?::jsonb, ?, ?)
                        """,
                versionId,
                compositionId,
                versionNumber,
                totalCapacity,
                json(Map.of("coaches", snapshot)),
                blankToNull(request.changeReason()),
                actor
        );
        for (int i = 0; i < request.coaches().size(); i++) {
            CompositionCoachInput coach = request.coaches().get(i);
            CompositionCoach snapshotCoach = snapshot.get(i);
            jdbcTemplate.update(
                    """
                            INSERT INTO train_composition_coaches (
                                composition_version_id, coach_id, coach_number, position,
                                coach_capacity, configuration_snapshot
                            ) VALUES (?, ?, ?, ?, ?, ?::jsonb)
                            """,
                    versionId,
                    coach.coachId(),
                    coach.coachNumber().trim(),
                    coach.position(),
                    snapshotCoach.capacity(),
                    json(snapshotCoach)
            );
        }
        jdbcTemplate.update(
                "UPDATE train_compositions SET updated_by = ?, updated_at = CURRENT_TIMESTAMP WHERE composition_id = ?",
                actor,
                finalCompositionId
        );
        Composition created = composition(trainId);
        audit(actor, "COMPOSITION", versionId, "VERSION_CREATED", null, created, request.changeReason());
        return created;
    }

    @Transactional
    public Composition publishComposition(
            UUID trainId,
            UUID versionId,
            UUID actor,
            HttpServletRequest servletRequest
    ) {
        Composition before = composition(trainId);
        Integer matching = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*) FROM train_composition_versions v
                        JOIN train_compositions c ON c.composition_id = v.composition_id
                        WHERE c.train_id = ? AND v.composition_version_id = ? AND v.status = 'DRAFT'
                        """,
                Integer.class,
                trainId,
                versionId
        );
        if (matching == null || matching != 1) {
            throw notFound("COMPOSITION_DRAFT_NOT_FOUND", "Draft composition version not found for train");
        }
        Integer inactiveCoachCount = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM train_composition_coaches composed
                        JOIN coaches coach ON coach.coach_id = composed.coach_id
                        JOIN coach_configurations configuration
                          ON configuration.configuration_id = coach.configuration_id
                        WHERE composed.composition_version_id = ?
                          AND (NOT coach.active OR NOT configuration.active OR NOT EXISTS (
                              SELECT 1 FROM accommodation_layouts layout
                              WHERE layout.configuration_id = configuration.configuration_id
                                AND layout.status = 'PUBLISHED'
                          ))
                        """,
                Integer.class,
                versionId
        );
        if (inactiveCoachCount != null && inactiveCoachCount > 0) {
            throw badRequest("COMPOSITION_COACH_NOT_READY", "Every coach must remain active with a published layout");
        }
        jdbcTemplate.update(
                """
                        UPDATE train_composition_versions
                        SET status = 'SUPERSEDED'
                        WHERE composition_id = (
                            SELECT composition_id FROM train_compositions WHERE train_id = ?
                        ) AND status = 'PUBLISHED'
                        """,
                trainId
        );
        jdbcTemplate.update(
                """
                        UPDATE train_composition_versions
                        SET status = 'PUBLISHED', published_by = ?, published_at = CURRENT_TIMESTAMP
                        WHERE composition_version_id = ?
                        """,
                actor,
                versionId
        );
        jdbcTemplate.update(
                """
                        UPDATE train_compositions
                        SET current_version_id = ?, updated_by = ?, updated_at = CURRENT_TIMESTAMP
                        WHERE train_id = ?
                        """,
                versionId,
                actor,
                trainId
        );
        Composition published = composition(trainId);
        audit(actor, "COMPOSITION", versionId, "PUBLISHED", before, published, null);
        return published;
    }

    public Composition composition(UUID trainId) {
        return jdbcTemplate.query(
                """
                        SELECT c.composition_id, c.train_id, c.current_version_id,
                               v.composition_version_id, v.version_number, v.status, v.total_capacity
                        FROM train_compositions c
                        JOIN LATERAL (
                            SELECT * FROM train_composition_versions
                            WHERE composition_id = c.composition_id
                            ORDER BY version_number DESC
                            LIMIT 1
                        ) v ON TRUE
                        WHERE c.train_id = ?
                        """,
                (result, rowNumber) -> mapComposition(result),
                trainId
        ).stream().findFirst().orElseThrow(() -> notFound("COMPOSITION_NOT_FOUND", "Train composition not found"));
    }

    public PageResult<AuditEntry> audit(String entityType, UUID entityId, int page, int size) {
        validatePage(page, size);
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        List<Object> parameters = new ArrayList<>();
        appendEqual(where, parameters, " AND entity_type = ?", entityType);
        if (entityId != null) {
            where.append(" AND entity_id = ?");
            parameters.add(entityId);
        }
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM master_data_audit_logs" + where,
                Long.class,
                parameters.toArray()
        );
        List<Object> pageParameters = new ArrayList<>(parameters);
        pageParameters.add(size);
        pageParameters.add(page * size);
        List<AuditEntry> items = jdbcTemplate.query(
                "SELECT * FROM master_data_audit_logs" + where
                        + " ORDER BY occurred_at DESC LIMIT ? OFFSET ?",
                auditRowMapper(),
                pageParameters.toArray()
        );
        return new PageResult<>(items, page, size, total == null ? 0 : total);
    }

    private RouteVersion saveRouteVersion(UUID routeId, RouteVersionRequest request, UUID actor) {
        Integer versionNumber = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(version_number), 0) + 1 FROM train_route_versions WHERE route_id = ?",
                Integer.class,
                routeId
        );
        UUID routeVersionId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                        INSERT INTO train_route_versions (
                            route_version_id, route_id, version_number, source_station_id,
                            destination_station_id, total_distance_km, effective_from,
                            effective_to, change_reason, created_by
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                routeVersionId,
                routeId,
                versionNumber,
                request.sourceStationId(),
                request.destinationStationId(),
                request.totalDistanceKm(),
                request.effectiveFrom(),
                request.effectiveTo(),
                blankToNull(request.changeReason()),
                actor
        );
        for (int index = 0; index < request.stops().size(); index++) {
            RouteStopRequest stop = request.stops().get(index);
            String stopType = index == 0 ? "ORIGIN"
                    : index == request.stops().size() - 1 ? "TERMINAL" : "REGULAR";
            jdbcTemplate.update(
                    """
                            INSERT INTO train_route_stops (
                                route_stop_id, route_version_id, station_id, stop_sequence,
                                arrival_time, departure_time, arrival_day_offset, departure_day_offset,
                                halt_minutes, distance_from_source_km, stop_type, platform_label
                            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    UUID.randomUUID(),
                    routeVersionId,
                    stop.stationId(),
                    stop.sequence(),
                    stop.arrivalTime(),
                    stop.departureTime(),
                    stop.arrivalDayOffset(),
                    stop.departureDayOffset(),
                    stop.haltMinutes(),
                    stop.distanceFromSourceKm(),
                    stopType,
                    blankToNull(stop.platformLabel())
            );
        }
        return routeVersion(routeVersionId);
    }

    private void validateRoute(RouteVersionRequest request) {
        if (request.sourceStationId().equals(request.destinationStationId())) {
            throw badRequest("INVALID_ROUTE_STATIONS", "Route source and destination must differ");
        }
        if (request.effectiveTo() != null && request.effectiveTo().isBefore(request.effectiveFrom())) {
            throw badRequest("INVALID_EFFECTIVE_DATES", "Route end date cannot be before its start date");
        }
        List<RouteStopRequest> stops = request.stops();
        if (stops == null || stops.size() < 2) {
            throw badRequest("INVALID_ROUTE_STOPS", "A route needs at least an origin and a terminal stop");
        }
        if (!stops.get(0).stationId().equals(request.sourceStationId())
                || !stops.get(stops.size() - 1).stationId().equals(request.destinationStationId())) {
            throw badRequest("ROUTE_ENDPOINT_MISMATCH", "The first and last stops must match the route endpoints");
        }
        Set<UUID> stationIds = new HashSet<>();
        long previousDeparture = -1;
        BigDecimal previousDistance = BigDecimal.valueOf(-1);
        for (int index = 0; index < stops.size(); index++) {
            RouteStopRequest stop = stops.get(index);
            requireActiveStation(stop.stationId());
            if (!stationIds.add(stop.stationId())) {
                throw badRequest("DUPLICATE_ROUTE_STATION", "A station may occur only once in a route version");
            }
            if (stop.sequence() != index + 1) {
                throw badRequest("INVALID_ROUTE_SEQUENCE", "Route stop sequence must start at 1 and be contiguous");
            }
            if (stop.distanceFromSourceKm().compareTo(previousDistance) < 0
                    || stop.distanceFromSourceKm().compareTo(request.totalDistanceKm()) > 0) {
                throw badRequest("INVALID_ROUTE_DISTANCE", "Route stop distances must increase and stay within the route distance");
            }
            if (index == 0) {
                if (stop.arrivalTime() != null || stop.departureTime() == null
                        || stop.arrivalDayOffset() != 0 || stop.departureDayOffset() != 0
                        || stop.distanceFromSourceKm().compareTo(BigDecimal.ZERO) != 0) {
                    throw badRequest("INVALID_ROUTE_ORIGIN", "Origin must start at day 0 and distance 0 with a departure time");
                }
            } else if (index == stops.size() - 1) {
                if (stop.arrivalTime() == null || stop.departureTime() != null
                        || stop.distanceFromSourceKm().compareTo(request.totalDistanceKm()) != 0) {
                    throw badRequest("INVALID_ROUTE_TERMINAL", "Terminal must have an arrival time and match total route distance");
                }
            } else if (stop.arrivalTime() == null || stop.departureTime() == null) {
                throw badRequest("INVALID_ROUTE_TIMES", "Intermediate stops need both arrival and departure times");
            }
            long arrival = stop.arrivalTime() == null ? -1
                    : stop.arrivalDayOffset() * 1440L + stop.arrivalTime().toSecondOfDay() / 60L;
            long departure = stop.departureTime() == null ? -1
                    : stop.departureDayOffset() * 1440L + stop.departureTime().toSecondOfDay() / 60L;
            if (arrival >= 0 && arrival < previousDeparture) {
                throw badRequest("INVALID_ROUTE_CHRONOLOGY", "Route arrival times must not move backwards");
            }
            if (departure >= 0 && departure < (arrival >= 0 ? arrival : previousDeparture)) {
                throw badRequest("INVALID_ROUTE_CHRONOLOGY", "Route departure times must not move backwards");
            }
            if (departure >= 0) {
                previousDeparture = departure;
            } else if (arrival >= 0) {
                previousDeparture = arrival;
            }
            previousDistance = stop.distanceFromSourceKm();
        }
    }

    private RowMapper<RouteVersion> routeVersionRowMapper() {
        return (result, rowNumber) -> {
            UUID routeVersionId = result.getObject("route_version_id", UUID.class);
            return new RouteVersion(
                    routeVersionId,
                    result.getObject("route_id", UUID.class),
                    result.getString("route_name"),
                    result.getInt("version_number"),
                    result.getObject("source_station_id", UUID.class),
                    result.getString("source_code"),
                    result.getObject("destination_station_id", UUID.class),
                    result.getString("destination_code"),
                    result.getBigDecimal("total_distance_km"),
                    result.getObject("effective_from", LocalDate.class),
                    result.getObject("effective_to", LocalDate.class),
                    result.getString("status"),
                    routeStops(routeVersionId)
            );
        };
    }

    private List<RouteStop> routeStops(UUID routeVersionId) {
        return jdbcTemplate.query(
                """
                        SELECT rs.*, s.station_code, s.station_name
                        FROM train_route_stops rs
                        JOIN stations s ON s.station_id = rs.station_id
                        WHERE rs.route_version_id = ?
                        ORDER BY rs.stop_sequence
                        """,
                (result, rowNumber) -> new RouteStop(
                        result.getObject("route_stop_id", UUID.class),
                        result.getObject("station_id", UUID.class),
                        result.getString("station_code"),
                        result.getString("station_name"),
                        result.getInt("stop_sequence"),
                        result.getObject("arrival_time", java.time.LocalTime.class),
                        result.getObject("departure_time", java.time.LocalTime.class),
                        result.getInt("arrival_day_offset"),
                        result.getInt("departure_day_offset"),
                        result.getInt("halt_minutes"),
                        result.getBigDecimal("distance_from_source_km"),
                        result.getString("stop_type"),
                        result.getString("platform_label")
                ),
                routeVersionId
        );
    }

    private void validateSchedule(ScheduleRequest request) {
        if (request.effectiveTo() != null && request.effectiveTo().isBefore(request.effectiveFrom())) {
            throw badRequest("INVALID_EFFECTIVE_DATES", "Schedule end date cannot be before its start date");
        }
        if (request.operatingDays() == null || request.operatingDays().isEmpty()) {
            throw badRequest("SCHEDULE_DAYS_REQUIRED", "At least one operating day is required");
        }
        try {
            java.time.ZoneId.of(request.timezone().trim());
        } catch (java.time.DateTimeException exception) {
            throw badRequest("INVALID_TIMEZONE", "Schedule timezone must be a recognized IANA time zone");
        }
        if (new HashSet<>(request.operatingDays()).size() != request.operatingDays().size()
                || request.operatingDays().stream().anyMatch(day -> day < 1 || day > 7)) {
            throw badRequest("INVALID_SCHEDULE_DAYS", "Operating days must be unique values from 1 through 7");
        }
        Train targetTrain = train(request.trainId());
        RouteVersion routeVersion = routeVersion(request.routeVersionId());
        if (!"PUBLISHED".equals(routeVersion.status())) {
            throw badRequest("SCHEDULE_ROUTE_NOT_PUBLISHED", "A schedule must reference a published route version");
        }
        if (targetTrain.routeId() == null || !targetTrain.routeId().equals(routeVersion.routeId())
                || !targetTrain.sourceStationId().equals(routeVersion.sourceStationId())
                || !targetTrain.destinationStationId().equals(routeVersion.destinationStationId())) {
            throw badRequest("SCHEDULE_ROUTE_MISMATCH", "Schedule route endpoints must match the train's assigned route");
        }
        if (request.exceptions() != null) {
            Set<LocalDate> dates = new HashSet<>();
            for (ScheduleExceptionInput exception : request.exceptions()) {
                if (!Set.of("RUN", "CANCEL").contains(exception.serviceStatus().trim().toUpperCase())) {
                    throw badRequest("INVALID_SCHEDULE_EXCEPTION", "Exception status must be RUN or CANCEL");
                }
                if (!dates.add(exception.serviceDate())) {
                    throw badRequest("DUPLICATE_SCHEDULE_EXCEPTION", "An exception date may appear only once");
                }
                if (exception.serviceDate().isBefore(request.effectiveFrom())
                        || (request.effectiveTo() != null && exception.serviceDate().isAfter(request.effectiveTo()))) {
                    throw badRequest("SCHEDULE_EXCEPTION_OUT_OF_RANGE", "Exception dates must fall within schedule effective dates");
                }
            }
        }
    }

    private Schedule mapSchedule(ResultSet result) throws SQLException {
        UUID scheduleId = result.getObject("schedule_id", UUID.class);
        List<Integer> days = jdbcTemplate.query(
                "SELECT day_of_week FROM train_schedule_days WHERE schedule_id = ? ORDER BY day_of_week",
                (row, rowNumber) -> row.getInt("day_of_week"),
                scheduleId
        );
        List<ScheduleExceptionInput> exceptions = jdbcTemplate.query(
                """
                        SELECT service_date, service_status, change_reason
                        FROM train_schedule_exceptions
                        WHERE schedule_id = ?
                        ORDER BY service_date
                        """,
                (row, rowNumber) -> new ScheduleExceptionInput(
                        row.getObject("service_date", LocalDate.class),
                        row.getString("service_status"),
                        row.getString("change_reason")
                ),
                scheduleId
        );
        return new Schedule(
                scheduleId,
                result.getObject("train_id", UUID.class),
                result.getObject("route_version_id", UUID.class),
                result.getInt("version_number"),
                result.getString("timezone"),
                result.getObject("effective_from", LocalDate.class),
                result.getObject("effective_to", LocalDate.class),
                result.getString("status"),
                days,
                exceptions
        );
    }

    private void validateAccommodationSet(List<MasterDataModels.AccommodationInput> accommodations, int capacity) {
        if (capacity > 500) {
            throw badRequest("COACH_CAPACITY_LIMIT", "Coach capacity cannot exceed 500 accommodations");
        }
        if (accommodations == null || accommodations.isEmpty()) {
            throw badRequest("COACH_LAYOUT_REQUIRED", "At least one physical accommodation is required");
        }
        Set<String> numbers = new HashSet<>();
        Set<Integer> orders = new HashSet<>();
        int activeCapacity = 0;
        Set<String> allowedTypes = Set.of(
                "SEAT", "LOWER_BERTH", "MIDDLE_BERTH", "UPPER_BERTH",
                "SIDE_LOWER_BERTH", "SIDE_UPPER_BERTH", "CHAIR", "OTHER"
        );
        for (MasterDataModels.AccommodationInput accommodation : accommodations) {
            if (!allowedTypes.contains(accommodation.type().trim().toUpperCase())) {
                throw badRequest("INVALID_ACCOMMODATION_TYPE", "Unsupported physical accommodation type");
            }
            if (!numbers.add(accommodation.number().trim().toUpperCase())
                    || !orders.add(accommodation.displayOrder())) {
                throw badRequest("DUPLICATE_ACCOMMODATION", "Accommodation numbers and display orders must be unique");
            }
            if (accommodation.active()) {
                activeCapacity++;
            }
        }
        if (activeCapacity != capacity) {
            throw badRequest("COACH_CAPACITY_MISMATCH", "Coach capacity must equal the number of active accommodations");
        }
    }

    private int createLayout(
            UUID configurationId,
            List<MasterDataModels.AccommodationInput> accommodations,
            UUID actor
    ) {
        Integer version = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(version_number), 0) + 1 FROM accommodation_layouts WHERE configuration_id = ?",
                Integer.class,
                configurationId
        );
        UUID layoutId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO accommodation_layouts (layout_id, configuration_id, version_number, created_by) VALUES (?, ?, ?, ?)",
                layoutId,
                configurationId,
                version,
                actor
        );
        for (MasterDataModels.AccommodationInput accommodation : accommodations) {
            jdbcTemplate.update(
                    """
                            INSERT INTO physical_accommodations (
                                accommodation_id, layout_id, accommodation_number, accommodation_type,
                                position, deck_level, display_order, active
                            ) VALUES (?, ?, ?, ?, ?::jsonb, ?, ?, ?)
                            """,
                    UUID.randomUUID(),
                    layoutId,
                    accommodation.number().trim(),
                    accommodation.type().trim().toUpperCase(),
                    json(accommodation.position() == null ? Map.of() : accommodation.position()),
                    accommodation.deckLevel(),
                    accommodation.displayOrder(),
                    accommodation.active()
            );
        }
        return version == null ? 1 : version;
    }

    private RowMapper<CoachConfiguration> coachConfigurationRowMapper() {
        return (result, rowNumber) -> mapCoachConfiguration(result);
    }

    private CoachConfiguration mapCoachConfiguration(ResultSet result) throws SQLException {
        UUID configurationId = result.getObject("configuration_id", UUID.class);
        int version = result.getInt("layout_version");
        List<MasterDataModels.AccommodationInput> accommodations = version == 0
                ? List.of()
                : jdbcTemplate.query(
                        """
                                SELECT pa.accommodation_number, pa.accommodation_type, pa.position::text,
                                       pa.deck_level, pa.display_order, pa.active
                                FROM physical_accommodations pa
                                JOIN accommodation_layouts al ON al.layout_id = pa.layout_id
                                WHERE al.configuration_id = ? AND al.version_number = ?
                                ORDER BY pa.display_order
                                """,
                        (row, rowNumber) -> new MasterDataModels.AccommodationInput(
                                row.getString("accommodation_number"),
                                row.getString("accommodation_type"),
                                parseMap(row.getString("position")),
                                (Integer) row.getObject("deck_level"),
                                row.getInt("display_order"),
                                row.getBoolean("active")
                        ),
                        configurationId,
                        version
                );
        return new CoachConfiguration(
                configurationId,
                result.getString("configuration_code"),
                result.getString("configuration_name"),
                result.getObject("coach_class_id", UUID.class),
                result.getString("class_name"),
                result.getObject("coach_type_id", UUID.class),
                result.getString("type_name"),
                result.getInt("capacity"),
                result.getBoolean("active"),
                version,
                result.getString("layout_status"),
                accommodations
        );
    }

    private Lookup coachClass(UUID id) {
        return jdbcTemplate.queryForObject(
                "SELECT coach_class_id, class_code, class_name, description, active FROM coach_classes WHERE coach_class_id = ?",
                (result, rowNumber) -> new Lookup(
                        result.getObject("coach_class_id", UUID.class),
                        result.getString("class_code"),
                        result.getString("class_name"),
                        result.getString("description"),
                        result.getBoolean("active")
                ),
                id
        );
    }

    private Lookup coachType(UUID id) {
        return jdbcTemplate.queryForObject(
                "SELECT coach_type_id, type_code, type_name, description, active FROM coach_types WHERE coach_type_id = ?",
                (result, rowNumber) -> new Lookup(
                        result.getObject("coach_type_id", UUID.class),
                        result.getString("type_code"),
                        result.getString("type_name"),
                        result.getString("description"),
                        result.getBoolean("active")
                ),
                id
        );
    }

    private RowMapper<CoachSummary> coachRowMapper() {
        return (result, rowNumber) -> new CoachSummary(
                result.getObject("coach_id", UUID.class),
                result.getString("coach_code"),
                result.getObject("configuration_id", UUID.class),
                result.getString("configuration_name"),
                result.getString("class_name"),
                result.getString("type_name"),
                result.getBoolean("active")
        );
    }

    private CoachSummary coach(UUID coachId) {
        return jdbcTemplate.query(
                """
                        SELECT c.coach_id, c.coach_code, c.configuration_id, c.active,
                               cc.configuration_name, cl.class_name, ct.type_name
                        FROM coaches c
                        JOIN coach_configurations cc ON cc.configuration_id = c.configuration_id
                        JOIN coach_classes cl ON cl.coach_class_id = cc.coach_class_id
                        JOIN coach_types ct ON ct.coach_type_id = cc.coach_type_id
                        WHERE c.coach_id = ?
                        """,
                coachRowMapper(),
                coachId
        ).stream().findFirst().orElseThrow(() -> notFound("COACH_NOT_FOUND", "Coach not found"));
    }

    private boolean hasPublishedLayout(UUID configurationId) {
        Integer publishedLayouts = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM accommodation_layouts WHERE configuration_id = ? AND status = 'PUBLISHED'",
                Integer.class,
                configurationId
        );
        return publishedLayouts != null && publishedLayouts > 0;
    }

    private void validateComposition(CompositionRequest request) {
        if (request.coaches() == null || request.coaches().isEmpty()) {
            throw badRequest("COMPOSITION_EMPTY", "A train composition must contain at least one coach");
        }
        Set<UUID> coachIds = new HashSet<>();
        Set<String> numbers = new HashSet<>();
        Set<Integer> positions = new HashSet<>();
        for (CompositionCoachInput coach : request.coaches()) {
            if (!coachIds.add(coach.coachId())
                    || !numbers.add(coach.coachNumber().trim().toUpperCase())
                    || !positions.add(coach.position())) {
                throw badRequest("DUPLICATE_COMPOSITION_COACH", "Coach, coach number, and position values must be unique");
            }
        }
        for (int position = 1; position <= request.coaches().size(); position++) {
            if (!positions.contains(position)) {
                throw badRequest("INVALID_COMPOSITION_ORDER", "Coach positions must be contiguous starting at 1");
            }
        }
    }

    private Composition mapComposition(ResultSet result) throws SQLException {
        UUID versionId = result.getObject("composition_version_id", UUID.class);
        List<CompositionCoach> coaches = jdbcTemplate.query(
                """
                        SELECT coach_id, coach_number, position, coach_capacity, configuration_snapshot::text
                        FROM train_composition_coaches
                        WHERE composition_version_id = ?
                        ORDER BY position
                        """,
                (row, rowNumber) -> {
                    Map<String, Object> snapshot = parseMap(row.getString("configuration_snapshot"));
                    return new CompositionCoach(
                            row.getObject("coach_id", UUID.class),
                            row.getString("coach_number"),
                            row.getInt("position"),
                            row.getInt("coach_capacity"),
                            UUID.fromString((String) snapshot.get("configurationId")),
                            (String) snapshot.get("configurationName"),
                            (String) snapshot.get("className"),
                            (String) snapshot.get("coachTypeName")
                    );
                },
                versionId
        );
        return new Composition(
                result.getObject("composition_id", UUID.class),
                result.getObject("train_id", UUID.class),
                versionId,
                result.getInt("version_number"),
                result.getString("status"),
                result.getInt("total_capacity"),
                coaches
        );
    }

    private RowMapper<AuditEntry> auditRowMapper() {
        return (result, rowNumber) -> new AuditEntry(
                result.getObject("audit_id", UUID.class),
                result.getObject("actor_user_id", UUID.class),
                result.getString("entity_type"),
                result.getObject("entity_id", UUID.class),
                result.getString("action"),
                result.getString("previous_value"),
                result.getString("new_value"),
                result.getString("change_reason"),
                result.getTimestamp("occurred_at").toInstant(),
                result.getString("correlation_id")
        );
    }

    private void validateStation(StationRequest request) {
        if ((request.latitude() == null) != (request.longitude() == null)) {
            throw badRequest("INVALID_COORDINATES", "Latitude and longitude must both be provided or both be empty");
        }
        if (request.zoneId() != null) {
            Integer activeZone = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM railway_zones WHERE zone_id = ? AND status = 'ACTIVE'",
                    Integer.class,
                    request.zoneId()
            );
            if (activeZone == null || activeZone != 1) {
                throw badRequest("ZONE_NOT_ACTIVE", "Select an active railway zone");
            }
        }
        if (request.divisionId() != null) {
            Integer activeDivision = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM railway_divisions WHERE division_id = ? AND status = 'ACTIVE'",
                    Integer.class,
                    request.divisionId()
            );
            if (activeDivision == null || activeDivision != 1) {
                throw badRequest("DIVISION_NOT_ACTIVE", "Select an active railway division");
            }
        }
        if (request.divisionId() != null && request.zoneId() != null) {
            Integer matches = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM railway_divisions WHERE division_id = ? AND zone_id = ?",
                    Integer.class,
                    request.divisionId(),
                    request.zoneId()
            );
            if (matches == null || matches != 1) {
                throw badRequest("DIVISION_ZONE_MISMATCH", "The selected division does not belong to the selected zone");
            }
        }
        try {
            java.time.ZoneId.of(request.timezone().trim());
        } catch (java.time.DateTimeException exception) {
            throw badRequest("INVALID_TIMEZONE", "Station timezone must be a recognized IANA time zone");
        }
    }

    private void validateTrain(TrainRequest request) {
        if (request.sourceStationId().equals(request.destinationStationId())) {
            throw badRequest("INVALID_TRAIN_STATIONS", "Source and destination stations must differ");
        }
        if (request.effectiveTo() != null && request.effectiveTo().isBefore(request.effectiveFrom())) {
            throw badRequest("INVALID_EFFECTIVE_DATES", "Train end date cannot be before its start date");
        }
        requireActiveStation(request.sourceStationId());
        requireActiveStation(request.destinationStationId());
        requireActiveLookup("train_categories", "category_id", request.categoryId());
        requireActiveLookup("train_types", "train_type_id", request.trainTypeId());
        if (request.routeId() != null) {
            RouteVersion route = jdbcTemplate.query(
                    """
                            SELECT rv.route_version_id, rv.route_id, r.route_name, rv.version_number,
                                   rv.source_station_id, source.station_code AS source_code,
                                   rv.destination_station_id, destination.station_code AS destination_code,
                                   rv.total_distance_km, rv.effective_from, rv.effective_to, rv.status
                            FROM train_routes r
                            JOIN LATERAL (
                                SELECT * FROM train_route_versions
                                WHERE route_id = r.route_id
                                ORDER BY version_number DESC
                                LIMIT 1
                            ) rv ON TRUE
                            JOIN stations source ON source.station_id = rv.source_station_id
                            JOIN stations destination ON destination.station_id = rv.destination_station_id
                            WHERE r.route_id = ? AND r.status <> 'RETIRED'
                            """,
                    (result, rowNumber) -> new RouteVersion(
                            result.getObject("route_version_id", UUID.class),
                            result.getObject("route_id", UUID.class),
                            result.getString("route_name"),
                            result.getInt("version_number"),
                            result.getObject("source_station_id", UUID.class),
                            result.getString("source_code"),
                            result.getObject("destination_station_id", UUID.class),
                            result.getString("destination_code"),
                            result.getBigDecimal("total_distance_km"),
                            result.getObject("effective_from", LocalDate.class),
                            result.getObject("effective_to", LocalDate.class),
                            result.getString("status"),
                            List.of()
                    ),
                    request.routeId()
            ).stream().findFirst().orElse(null);
            if (route == null) {
                throw notFound("ROUTE_NOT_FOUND", "Assigned route not found");
            }
            if (!route.sourceStationId().equals(request.sourceStationId())
                    || !route.destinationStationId().equals(request.destinationStationId())) {
                throw badRequest("TRAIN_ROUTE_ENDPOINT_MISMATCH", "Assigned route endpoints must match the train");
            }
        }
    }

    private void requireActiveStation(UUID stationId) {
        Integer matches = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM stations WHERE station_id = ? AND status = 'ACTIVE'",
                Integer.class,
                stationId
        );
        if (matches == null || matches != 1) {
            throw badRequest("STATION_NOT_ACTIVE", "Train endpoints must reference active stations");
        }
    }

    private void requireActiveLookup(String table, String idColumn, UUID id) {
        Integer matches = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE " + idColumn + " = ? AND active",
                Integer.class,
                id
        );
        if (matches == null || matches != 1) {
            throw badRequest("MASTER_DATA_REFERENCE_INVALID", "Selected category or train type is inactive or missing");
        }
    }

    private void replaceFacilities(UUID stationId, List<FacilityInput> facilities) {
        jdbcTemplate.update("DELETE FROM station_facilities WHERE station_id = ?", stationId);
        if (facilities == null) {
            return;
        }
        Set<String> codes = new HashSet<>();
        for (FacilityInput facility : facilities) {
            String code = facility.facilityCode().trim().toUpperCase();
            if (!codes.add(code)) {
                throw badRequest("DUPLICATE_STATION_FACILITY", "A facility code may appear only once per station");
            }
            jdbcTemplate.update(
                    """
                            INSERT INTO station_facilities (station_id, facility_code, details, active)
                            VALUES (?, ?, ?::jsonb, ?)
                            """,
                    stationId,
                    code,
                    json(facility.details() == null ? Map.of() : facility.details()),
                    facility.active()
            );
        }
    }

    private RowMapper<Station> stationRowMapper() {
        return (result, rowNumber) -> {
            UUID stationId = result.getObject("station_id", UUID.class);
            return new Station(
                    stationId,
                    result.getString("station_code"),
                    result.getString("station_name"),
                    parseStringList(result.getString("alternate_names")),
                    result.getString("city"),
                    result.getString("state"),
                    result.getString("country"),
                    result.getBigDecimal("latitude"),
                    result.getBigDecimal("longitude"),
                    result.getString("timezone"),
                    result.getString("address"),
                    result.getObject("zone_id", UUID.class),
                    result.getObject("division_id", UUID.class),
                    result.getString("station_category"),
                    result.getBoolean("code_verified"),
                    result.getString("status"),
                    facilities(stationId),
                    result.getTimestamp("created_at").toInstant(),
                    result.getTimestamp("updated_at").toInstant()
            );
        };
    }

    private List<FacilityInput> facilities(UUID stationId) {
        return jdbcTemplate.query(
                """
                        SELECT facility_code, details::text, active
                        FROM station_facilities
                        WHERE station_id = ?
                        ORDER BY facility_code
                        """,
                (result, rowNumber) -> new FacilityInput(
                        result.getString("facility_code"),
                        parseMap(result.getString("details")),
                        result.getBoolean("active")
                ),
                stationId
        );
    }

    private List<Lookup> lookupRows(String table, String idColumn, String codeColumn, String nameColumn) {
        String descriptionColumn = "description";
        return jdbcTemplate.query(
                "SELECT " + idColumn + ", " + codeColumn + ", " + nameColumn
                        + ", " + descriptionColumn + ", active FROM " + table
                        + " ORDER BY lower(" + nameColumn + ")",
                (result, rowNumber) -> new Lookup(
                        result.getObject(idColumn, UUID.class),
                        result.getString(codeColumn),
                        result.getString(nameColumn),
                        result.getString(descriptionColumn),
                        result.getBoolean("active")
                )
        );
    }

    private Lookup createLookup(
            String table,
            String idColumn,
            String codeColumn,
            String nameColumn,
            LookupRequest request,
            UUID actor,
            HttpServletRequest servletRequest,
            String entityType
    ) {
        UUID id = UUID.randomUUID();
        try {
            jdbcTemplate.update(
                    "INSERT INTO " + table + " (" + idColumn + ", " + codeColumn + ", " + nameColumn
                            + ", description, created_by) VALUES (?, upper(?), ?, ?, ?)",
                    id,
                    request.code().trim(),
                    request.name().trim(),
                    blankToNull(request.description()),
                    actor
            );
        } catch (DuplicateKeyException exception) {
            throw conflict("MASTER_DATA_CODE_EXISTS", "A category or train type with this code/name already exists");
        }
        Lookup created = jdbcTemplate.queryForObject(
                "SELECT " + idColumn + ", " + codeColumn + ", " + nameColumn
                        + ", description, active FROM " + table + " WHERE " + idColumn + " = ?",
                (result, rowNumber) -> new Lookup(
                        result.getObject(idColumn, UUID.class),
                        result.getString(codeColumn),
                        result.getString(nameColumn),
                        result.getString("description"),
                        result.getBoolean("active")
                ),
                id
        );
        audit(actor, entityType, id, "CREATED", null, created, null);
        return created;
    }

    private String trainSelect() {
        return """
                SELECT t.*, c.category_name, tt.type_name,
                       ss.station_code AS source_code, ss.station_name AS source_name,
                       ds.station_code AS destination_code, ds.station_name AS destination_name
                FROM trains t
                JOIN train_categories c ON c.category_id = t.category_id
                JOIN train_types tt ON tt.train_type_id = t.train_type_id
                JOIN stations ss ON ss.station_id = t.source_station_id
                JOIN stations ds ON ds.station_id = t.destination_station_id
                """;
    }

    private RowMapper<Train> trainRowMapper() {
        return (result, rowNumber) -> new Train(
                result.getObject("train_id", UUID.class),
                result.getString("train_number"),
                result.getString("train_name"),
                result.getObject("category_id", UUID.class),
                result.getString("category_name"),
                result.getObject("train_type_id", UUID.class),
                result.getString("type_name"),
                result.getObject("source_station_id", UUID.class),
                result.getString("source_code"),
                result.getString("source_name"),
                result.getObject("destination_station_id", UUID.class),
                result.getString("destination_code"),
                result.getString("destination_name"),
                result.getObject("route_id", UUID.class),
                result.getBigDecimal("total_distance_km"),
                result.getString("description"),
                result.getString("status"),
                result.getObject("effective_from", LocalDate.class),
                result.getObject("effective_to", LocalDate.class)
        );
    }

    private void appendLike(StringBuilder where, List<Object> parameters, String clause, String value) {
        if (value != null && !value.isBlank()) {
            where.append(clause);
            String pattern = "%" + value.trim().toLowerCase() + "%";
            if (clause.contains("OR")) {
                parameters.add(pattern);
                parameters.add(pattern);
            } else {
                parameters.add(pattern);
            }
        }
    }

    private void appendEqual(StringBuilder where, List<Object> parameters, String clause, String value) {
        if (value != null && !value.isBlank()) {
            where.append(clause);
            parameters.add(value.trim().toUpperCase());
        }
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw badRequest("INVALID_PAGE", "Page must be non-negative and size must be between 1 and 100");
        }
    }

    private long count(String sql) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class);
        return value == null ? 0 : value;
    }

    private void audit(
            UUID actor,
            String entityType,
            UUID entityId,
            String action,
            Object previous,
            Object next,
            String reason
    ) {
        jdbcTemplate.update(
                """
                        INSERT INTO master_data_audit_logs (
                            audit_id, actor_user_id, entity_type, entity_id, action,
                            previous_value, new_value, change_reason, correlation_id
                        ) VALUES (?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?)
                        """,
                UUID.randomUUID(),
                actor,
                entityType,
                entityId,
                action,
                previous == null ? null : json(previous),
                next == null ? null : json(next),
                blankToNull(reason),
                RequestIds.current()
        );
    }

    private void recordStationVersion(Station snapshot, UUID actor, String type, String reason) {
        Integer version = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(version_number), 0) + 1 FROM station_versions WHERE station_id = ?",
                Integer.class,
                snapshot.stationId()
        );
        jdbcTemplate.update(
                """
                        INSERT INTO station_versions (
                            station_version_id, station_id, version_number, version_type,
                            snapshot, change_reason, changed_by
                        ) VALUES (?, ?, ?, ?, ?::jsonb, ?, ?)
                        """,
                UUID.randomUUID(),
                snapshot.stationId(),
                version,
                type,
                json(snapshot),
                blankToNull(reason),
                actor
        );
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize master-data audit payload", exception);
        }
    }

    private List<String> parseStringList(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Stored station alternate names are invalid", exception);
        }
    }

    private Map<String, Object> parseMap(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Stored station facility details are invalid", exception);
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private MasterDataException notFound(String code, String message) {
        return new MasterDataException(HttpStatus.NOT_FOUND, code, message);
    }

    private MasterDataException badRequest(String code, String message) {
        return new MasterDataException(HttpStatus.BAD_REQUEST, code, message);
    }

    private MasterDataException conflict(String code, String message) {
        return new MasterDataException(HttpStatus.CONFLICT, code, message);
    }
}
