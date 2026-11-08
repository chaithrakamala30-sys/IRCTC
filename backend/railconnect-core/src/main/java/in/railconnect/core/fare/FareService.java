package in.railconnect.core.fare;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.railconnect.core.fare.FareModels.DistanceSlabRequest;
import in.railconnect.core.fare.FareModels.FareComponentRequest;
import in.railconnect.core.fare.FareModels.FarePassengerCategory;
import in.railconnect.core.fare.FareModels.FareQuoteRequest;
import in.railconnect.core.fare.FareModels.FareQuoteResponse;
import in.railconnect.core.fare.FareModels.FareRuleAuditEvent;
import in.railconnect.core.fare.FareModels.FareRuleVersionRequest;
import in.railconnect.core.fare.FareModels.FareVersionSummary;
import in.railconnect.core.fare.FareModels.PassengerAdjustmentRequest;
import in.railconnect.core.fare.FareModels.PassengerCategoryRequest;
import in.railconnect.core.fare.FareModels.PassengerRequest;
import in.railconnect.core.identity.RailConnectPrincipal;
import in.railconnect.core.search.PublicSearchModels.CoachClass;
import in.railconnect.core.search.PublicSearchModels.TrainDetail;
import in.railconnect.core.search.PublicTrainSearchService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static in.railconnect.core.fare.FareModels.*;

@Service
public class FareService {
    private static final String VERSION_SELECT = """
            SELECT r.fare_rule_id, r.rule_name, v.fare_rule_version_id, v.version_number, v.status,
                   v.train_id, t.train_number, v.category_id, cat.category_code,
                   v.coach_class_id, cc.class_code, v.pricing_model, v.currency,
                   v.base_amount, v.per_km_rate, v.priority, v.effective_from, v.effective_to,
                   v.change_reason, creator.full_name AS created_by, v.created_at,
                   approver.full_name AS approved_by, v.approved_at,
                   publisher.full_name AS published_by, v.published_at
            FROM fare_rule_versions v
            JOIN fare_rules r ON r.fare_rule_id = v.fare_rule_id
            LEFT JOIN trains t ON t.train_id = v.train_id
            LEFT JOIN train_categories cat ON cat.category_id = v.category_id
            LEFT JOIN coach_classes cc ON cc.coach_class_id = v.coach_class_id
            JOIN users creator ON creator.user_id = v.created_by
            LEFT JOIN users approver ON approver.user_id = v.approved_by
            LEFT JOIN users publisher ON publisher.user_id = v.published_by
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final PublicTrainSearchService trainSearchService;
    private final ObjectMapper objectMapper;
    private final Duration quoteValidity;

    public FareService(
            NamedParameterJdbcTemplate jdbc,
            PublicTrainSearchService trainSearchService,
            ObjectMapper objectMapper,
            @Value("${railconnect.fares.quote-validity:PT10M}") Duration quoteValidity
    ) {
        this.jdbc = jdbc;
        this.trainSearchService = trainSearchService;
        this.objectMapper = objectMapper;
        this.quoteValidity = quoteValidity;
        if (quoteValidity.isZero() || quoteValidity.isNegative() || quoteValidity.compareTo(Duration.ofHours(1)) > 0) {
            throw new IllegalArgumentException("railconnect.fares.quote-validity must be greater than zero and at most one hour");
        }
    }

    @Transactional(readOnly = true)
    public List<FareVersionSummary> versions() {
        return jdbc.query(VERSION_SELECT + """
                ORDER BY r.rule_name, v.version_number DESC
                LIMIT 500
                """, Map.of(), FareService::mapVersion);
    }

    @Transactional(readOnly = true)
    public List<FarePassengerCategory> passengerCategories() {
        return jdbc.query("""
                SELECT passenger_category_id, category_code, category_name, minimum_age, maximum_age, active
                FROM fare_passenger_categories
                ORDER BY category_name
                """, Map.of(), (row, index) -> new FarePassengerCategory(
                row.getObject("passenger_category_id", UUID.class),
                row.getString("category_code"),
                row.getString("category_name"),
                row.getObject("minimum_age", Integer.class),
                row.getObject("maximum_age", Integer.class),
                row.getBoolean("active")));
    }

    @Transactional(readOnly = true)
    public List<FarePassengerCategory> publicPassengerCategories() {
        return jdbc.query("""
                SELECT passenger_category_id, category_code, category_name, minimum_age, maximum_age, active
                FROM fare_passenger_categories
                WHERE active = TRUE
                ORDER BY category_name
                """, Map.of(), (row, index) -> new FarePassengerCategory(
                row.getObject("passenger_category_id", UUID.class),
                row.getString("category_code"),
                row.getString("category_name"),
                row.getObject("minimum_age", Integer.class),
                row.getObject("maximum_age", Integer.class),
                row.getBoolean("active")));
    }

    @Transactional(readOnly = true)
    public FareAdminOptions adminOptions() {
        List<FareTrainOption> trains = jdbc.query("""
                SELECT train_id, train_number, train_name, category_id
                FROM trains WHERE status = 'ACTIVE'
                ORDER BY train_number LIMIT 500
                """, Map.of(), (row, index) -> new FareTrainOption(
                row.getObject("train_id", UUID.class),
                row.getString("train_number"),
                row.getString("train_name"),
                row.getObject("category_id", UUID.class)));
        List<FareLookupOption> categories = jdbc.query("""
                SELECT category_id, category_code, category_name
                FROM train_categories WHERE active = TRUE
                ORDER BY category_name LIMIT 200
                """, Map.of(), (row, index) -> new FareLookupOption(
                row.getObject("category_id", UUID.class),
                row.getString("category_code"),
                row.getString("category_name")));
        List<FareLookupOption> classes = jdbc.query("""
                SELECT coach_class_id, class_code, class_name
                FROM coach_classes WHERE active = TRUE
                ORDER BY class_code LIMIT 200
                """, Map.of(), (row, index) -> new FareLookupOption(
                row.getObject("coach_class_id", UUID.class),
                row.getString("class_code"),
                row.getString("class_name")));
        List<FareLookupOption> stations = jdbc.query("""
                SELECT station_id, station_code, station_name
                FROM stations WHERE status = 'ACTIVE'
                ORDER BY station_code LIMIT 500
                """, Map.of(), (row, index) -> new FareLookupOption(
                row.getObject("station_id", UUID.class),
                row.getString("station_code"),
                row.getString("station_name")));
        return new FareAdminOptions(trains, categories, classes, stations);
    }

    @Transactional
    public FarePassengerCategory createPassengerCategory(PassengerCategoryRequest request, RailConnectPrincipal actor) {
        if (request.minimumAge() != null && request.maximumAge() != null
                && request.minimumAge() > request.maximumAge()) {
            throw invalid("Minimum age cannot exceed maximum age");
        }
        UUID id = UUID.randomUUID();
        try {
            jdbc.update("""
                    INSERT INTO fare_passenger_categories (
                        passenger_category_id, category_code, category_name,
                        minimum_age, maximum_age, created_by
                    ) VALUES (
                        :id, :code, :name, :minimumAge, :maximumAge, :actorId
                    )
                    """, new MapSqlParameterSource()
                    .addValue("id", id)
                    .addValue("code", request.categoryCode())
                    .addValue("name", request.categoryName().trim())
                    .addValue("minimumAge", request.minimumAge())
                    .addValue("maximumAge", request.maximumAge())
                    .addValue("actorId", actor.userId()));
        } catch (DuplicateKeyException exception) {
            throw conflict("FARE_PASSENGER_CATEGORY_EXISTS",
                    "A passenger category with that code or name already exists");
        }
        return jdbc.queryForObject("""
                SELECT passenger_category_id, category_code, category_name, minimum_age, maximum_age, active
                FROM fare_passenger_categories WHERE passenger_category_id = :id
                """, Map.of("id", id), (row, index) -> new FarePassengerCategory(
                row.getObject("passenger_category_id", UUID.class),
                row.getString("category_code"),
                row.getString("category_name"),
                row.getObject("minimum_age", Integer.class),
                row.getObject("maximum_age", Integer.class),
                row.getBoolean("active")));
    }

    @Transactional
    public FareVersionSummary createRule(FareRuleVersionRequest request, RailConnectPrincipal actor) {
        UUID ruleId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO fare_rules (fare_rule_id, rule_name, created_by)
                VALUES (:id, :name, :actorId)
                """, Map.of("id", ruleId, "name", request.ruleName().trim(), "actorId", actor.userId()));
        UUID versionId = createVersionRecord(ruleId, 1, request, actor);
        return version(versionId);
    }

    @Transactional
    public FareVersionSummary createVersion(UUID ruleId, FareRuleVersionRequest request, RailConnectPrincipal actor) {
        String currentName = jdbc.query("""
                SELECT rule_name FROM fare_rules WHERE fare_rule_id = :id FOR UPDATE
                """, Map.of("id", ruleId), result -> result.next() ? result.getString(1) : null);
        if (currentName == null) throw notFound("FARE_RULE_NOT_FOUND", "Fare rule was not found");
        int nextVersion = jdbc.queryForObject("""
                SELECT COALESCE(MAX(version_number), 0) + 1
                FROM fare_rule_versions WHERE fare_rule_id = :id
                """, Map.of("id", ruleId), Integer.class);
        UUID versionId = createVersionRecord(ruleId, nextVersion, request, actor);
        return version(versionId);
    }

    @Transactional
    public FareVersionSummary updateDraft(UUID versionId, FareRuleVersionRequest request, RailConnectPrincipal actor) {
        VersionState state = versionState(versionId, true);
        requireDraft(state);
        validateRequest(request);
        jdbc.update("""
                UPDATE fare_rule_versions SET
                    train_id = :trainId, category_id = :categoryId, coach_class_id = :coachClassId,
                    pricing_model = :pricingModel, base_amount = :baseAmount, per_km_rate = :perKmRate,
                    priority = :priority, effective_from = :effectiveFrom, effective_to = :effectiveTo,
                    change_reason = :reason, updated_by = :actorId, updated_at = CURRENT_TIMESTAMP
                WHERE fare_rule_version_id = :versionId
                """, versionParams(request, actor.userId())
                .addValue("versionId", versionId));
        jdbc.update("""
                INSERT INTO fare_rule_version_authors (fare_rule_version_id, user_id)
                VALUES (:versionId, :actorId) ON CONFLICT DO NOTHING
                """, Map.of("versionId", versionId, "actorId", actor.userId()));
        replaceChildren(versionId, request);
        audit(versionId, actor.userId(), "UPDATED", Map.of("changeReason", request.changeReason()));
        return version(versionId);
    }

    @Transactional
    public FareVersionSummary submit(UUID versionId, RailConnectPrincipal actor) {
        VersionState state = versionState(versionId, true);
        requireDraft(state);
        if (state.authorCount() < 1) {
            throw conflict("FARE_RULE_AUTHOR_REQUIRED", "A fare rule version must have an author before submission");
        }
        jdbc.update("""
                UPDATE fare_rule_versions
                SET status = 'SUBMITTED', submitted_at = CURRENT_TIMESTAMP
                WHERE fare_rule_version_id = :versionId
                """, Map.of("versionId", versionId));
        audit(versionId, actor.userId(), "SUBMITTED", Map.of());
        return version(versionId);
    }

    @Transactional
    public FareVersionSummary approve(UUID versionId, RailConnectPrincipal actor) {
        VersionState state = versionState(versionId, true);
        requireStatus(state, "SUBMITTED", "Only submitted fare-rule versions can be approved");
        requireNotAuthor(versionId, actor.userId());
        jdbc.update("""
                UPDATE fare_rule_versions
                SET status = 'APPROVED', approved_by = :actorId, approved_at = CURRENT_TIMESTAMP
                WHERE fare_rule_version_id = :versionId
                """, Map.of("versionId", versionId, "actorId", actor.userId()));
        audit(versionId, actor.userId(), "APPROVED", Map.of());
        return version(versionId);
    }

    @Transactional
    public FareVersionSummary publish(UUID versionId, RailConnectPrincipal actor) {
        VersionState state = versionState(versionId, true);
        requireStatus(state, "APPROVED", "Only approved fare-rule versions can be published");
        requireNotAuthor(versionId, actor.userId());
        jdbc.getJdbcTemplate().execute("SELECT pg_advisory_xact_lock(41207, 1)");
        ensureNoPublishedOverlap(state);
        jdbc.update("""
                UPDATE fare_rule_versions
                SET status = 'PUBLISHED', published_by = :actorId, published_at = CURRENT_TIMESTAMP
                WHERE fare_rule_version_id = :versionId
                """, Map.of("versionId", versionId, "actorId", actor.userId()));
        audit(versionId, actor.userId(), "PUBLISHED", Map.of());
        return version(versionId);
    }

    @Transactional
    public FareVersionSummary retire(UUID versionId, RailConnectPrincipal actor) {
        VersionState state = versionState(versionId, true);
        requireStatus(state, "PUBLISHED", "Only published fare-rule versions can be retired");
        jdbc.update("""
                UPDATE fare_rule_versions SET status = 'RETIRED'
                WHERE fare_rule_version_id = :versionId
                """, Map.of("versionId", versionId));
        audit(versionId, actor.userId(), "RETIRED", Map.of());
        return version(versionId);
    }

    @Transactional(readOnly = true)
    public List<FareRuleAuditEvent> auditEvents(UUID versionId) {
        versionState(versionId, false);
        return jdbc.query("""
                SELECT e.fare_rule_audit_event_id, e.fare_rule_version_id, u.full_name,
                       e.event_type, e.details::text, e.occurred_at
                FROM fare_rule_audit_events e
                JOIN users u ON u.user_id = e.actor_user_id
                WHERE e.fare_rule_version_id = :versionId
                ORDER BY e.occurred_at DESC
                LIMIT 100
                """, Map.of("versionId", versionId), (row, index) -> new FareRuleAuditEvent(
                row.getObject("fare_rule_audit_event_id", UUID.class),
                row.getObject("fare_rule_version_id", UUID.class),
                row.getString("full_name"),
                row.getString("event_type"),
                row.getString("details"),
                row.getTimestamp("occurred_at").toInstant()));
    }

    @Transactional(readOnly = true)
    public FareVersionDetail versionDetail(UUID versionId) {
        FareVersionSummary summary = version(versionId);
        List<DistanceSlabRequest> slabs = jdbc.query("""
                SELECT minimum_distance_km, maximum_distance_km, base_amount, per_km_rate
                FROM fare_rule_distance_slabs WHERE fare_rule_version_id = :versionId
                ORDER BY minimum_distance_km
                """, Map.of("versionId", versionId), (row, index) -> new DistanceSlabRequest(
                row.getBigDecimal("minimum_distance_km"),
                row.getBigDecimal("maximum_distance_km"),
                row.getBigDecimal("base_amount"),
                row.getBigDecimal("per_km_rate")));
        List<FareComponentRequest> components = jdbc.query("""
                SELECT passenger_category_id, component_code, display_name, component_type,
                       calculation_method, amount, maximum_amount, minimum_distance_km,
                       calculation_order, enabled
                FROM fare_rule_components WHERE fare_rule_version_id = :versionId
                ORDER BY calculation_order, component_code
                """, Map.of("versionId", versionId), (row, index) -> new FareComponentRequest(
                row.getObject("passenger_category_id", UUID.class),
                row.getString("component_code"),
                row.getString("display_name"),
                row.getString("component_type"),
                row.getString("calculation_method"),
                row.getBigDecimal("amount"),
                row.getBigDecimal("maximum_amount"),
                row.getBigDecimal("minimum_distance_km"),
                row.getInt("calculation_order"),
                row.getBoolean("enabled")));
        List<PassengerAdjustmentRequest> adjustments = jdbc.query("""
                SELECT passenger_category_id, adjustment_type, adjustment_value, maximum_amount
                FROM fare_rule_passenger_adjustments WHERE fare_rule_version_id = :versionId
                ORDER BY passenger_category_id
                """, Map.of("versionId", versionId), (row, index) -> new PassengerAdjustmentRequest(
                row.getObject("passenger_category_id", UUID.class),
                row.getString("adjustment_type"),
                row.getBigDecimal("adjustment_value"),
                row.getBigDecimal("maximum_amount")));
        FareRuleVersionRequest configuration = new FareRuleVersionRequest(
                summary.ruleName(),
                summary.changeReason(),
                summary.trainId(),
                summary.categoryId(),
                summary.coachClassId(),
                summary.pricingModel(),
                summary.baseAmount(),
                summary.perKmRate(),
                summary.priority(),
                summary.effectiveFrom(),
                summary.effectiveTo(),
                slabs,
                components,
                adjustments);
        return new FareVersionDetail(summary, configuration);
    }

    @Transactional
    public FareQuoteResponse quote(FareQuoteRequest request, RailConnectPrincipal actor) {
        return calculateQuote(request, actor == null ? null : actor.userId(), null, true);
    }

    @Transactional(readOnly = true)
    public FareQuoteResponse simulate(FareSimulationRequest request) {
        return calculateQuote(request.journey(), null, request.fareRuleVersionId(), false);
    }

    @Transactional(readOnly = true)
    public FareQuoteResponse quote(UUID quoteId, RailConnectPrincipal actor) {
        StoredQuote stored = jdbc.query("""
                SELECT owner_user_id, snapshot::text, expires_at
                FROM fare_quotes WHERE fare_quote_id = :quoteId
                """, Map.of("quoteId", quoteId), result -> result.next()
                ? new StoredQuote(
                        result.getObject("owner_user_id", UUID.class),
                        result.getString("snapshot"),
                        result.getTimestamp("expires_at").toInstant())
                : null);
        if (stored == null || (stored.ownerId() != null
                && (actor == null || !stored.ownerId().equals(actor.userId())))) {
            throw notFound("FARE_QUOTE_NOT_FOUND", "Fare quote was not found");
        }
        FareQuoteResponse snapshot = readQuote(stored.snapshot());
        String status = Instant.now().isBefore(stored.expiresAt()) ? "ACTIVE" : "EXPIRED";
        return withStatus(snapshot, status);
    }

    private UUID createVersionRecord(
            UUID ruleId,
            int versionNumber,
            FareRuleVersionRequest request,
            RailConnectPrincipal actor
    ) {
        validateRequest(request);
        UUID versionId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO fare_rule_versions (
                    fare_rule_version_id, fare_rule_id, version_number, train_id, category_id,
                    coach_class_id, pricing_model, base_amount, per_km_rate, priority,
                    effective_from, effective_to, change_reason, created_by, updated_by
                ) VALUES (
                    :versionId, :ruleId, :versionNumber, :trainId, :categoryId, :coachClassId,
                    :pricingModel, :baseAmount, :perKmRate, :priority,
                    :effectiveFrom, :effectiveTo, :reason, :actorId, :actorId
                )
                """, versionParams(request, actor.userId())
                .addValue("versionId", versionId)
                .addValue("ruleId", ruleId)
                .addValue("versionNumber", versionNumber));
        jdbc.update("""
                INSERT INTO fare_rule_version_authors (fare_rule_version_id, user_id)
                VALUES (:versionId, :actorId)
                """, Map.of("versionId", versionId, "actorId", actor.userId()));
        replaceChildren(versionId, request);
        audit(versionId, actor.userId(), "CREATED", Map.of("changeReason", request.changeReason()));
        return versionId;
    }

    private void replaceChildren(UUID versionId, FareRuleVersionRequest request) {
        jdbc.update("DELETE FROM fare_rule_distance_slabs WHERE fare_rule_version_id = :id", Map.of("id", versionId));
        jdbc.update("DELETE FROM fare_rule_components WHERE fare_rule_version_id = :id", Map.of("id", versionId));
        jdbc.update("DELETE FROM fare_rule_passenger_adjustments WHERE fare_rule_version_id = :id", Map.of("id", versionId));
        for (DistanceSlabRequest slab : request.distanceSlabs()) {
            jdbc.update("""
                    INSERT INTO fare_rule_distance_slabs (
                        fare_rule_version_id, minimum_distance_km, maximum_distance_km, base_amount, per_km_rate
                    ) VALUES (:versionId, :minimumDistance, :maximumDistance, :baseAmount, :perKmRate)
                    """, new MapSqlParameterSource()
                    .addValue("versionId", versionId)
                    .addValue("minimumDistance", slab.minimumDistanceKm())
                    .addValue("maximumDistance", slab.maximumDistanceKm())
                    .addValue("baseAmount", slab.baseAmount())
                    .addValue("perKmRate", slab.perKmRate()));
        }
        for (FareComponentRequest component : request.components()) {
            jdbc.update("""
                    INSERT INTO fare_rule_components (
                        fare_rule_component_id, fare_rule_version_id, passenger_category_id,
                        component_code, display_name, component_type, calculation_method, amount,
                        maximum_amount, minimum_distance_km, calculation_order, enabled
                    ) VALUES (
                        :id, :versionId, :passengerCategoryId, :code, :name, :type, :method,
                        :amount, :maximumAmount, :minimumDistance, :calculationOrder, :enabled
                    )
                    """, new MapSqlParameterSource()
                    .addValue("id", UUID.randomUUID())
                    .addValue("versionId", versionId)
                    .addValue("passengerCategoryId", component.passengerCategoryId())
                    .addValue("code", component.componentCode())
                    .addValue("name", component.displayName().trim())
                    .addValue("type", component.componentType())
                    .addValue("method", component.calculationMethod())
                    .addValue("amount", component.amount())
                    .addValue("maximumAmount", component.maximumAmount())
                    .addValue("minimumDistance", component.minimumDistanceKm())
                    .addValue("calculationOrder", component.calculationOrder())
                    .addValue("enabled", component.enabled()));
        }
        for (PassengerAdjustmentRequest adjustment : request.passengerAdjustments()) {
            jdbc.update("""
                    INSERT INTO fare_rule_passenger_adjustments (
                        fare_rule_version_id, passenger_category_id, adjustment_type,
                        adjustment_value, maximum_amount
                    ) VALUES (:versionId, :categoryId, :type, :value, :maximumAmount)
                    """, new MapSqlParameterSource()
                    .addValue("versionId", versionId)
                    .addValue("categoryId", adjustment.passengerCategoryId())
                    .addValue("type", adjustment.adjustmentType())
                    .addValue("value", adjustment.adjustmentValue())
                    .addValue("maximumAmount", adjustment.maximumAmount()));
        }
    }

    private void validateRequest(FareRuleVersionRequest request) {
        if (request.effectiveTo() != null && request.effectiveTo().isBefore(request.effectiveFrom())) {
            throw invalid("The effective end date must not be before the start date");
        }
        String model = request.pricingModel();
        if (!List.of("DISTANCE_RATE", "BASE_PLUS_DISTANCE", "DISTANCE_SLABS").contains(model)) {
            throw invalid("Choose a supported fare pricing model");
        }
        if (model.equals("DISTANCE_RATE")
                && (request.baseAmount().signum() != 0 || request.perKmRate().signum() <= 0
                || !request.distanceSlabs().isEmpty())) {
            throw invalid("Distance-rate rules require a positive per-kilometre rate and no slabs or base amount");
        }
        if (model.equals("BASE_PLUS_DISTANCE")
                && (request.baseAmount().add(request.perKmRate()).signum() <= 0
                || !request.distanceSlabs().isEmpty())) {
            throw invalid("Base-plus-distance rules require a non-zero base or rate and no slabs");
        }
        if (model.equals("DISTANCE_SLABS")
                && (request.baseAmount().signum() != 0 || request.perKmRate().signum() != 0
                || request.distanceSlabs().isEmpty())) {
            throw invalid("Slab rules require configured distance slabs and no top-level base or rate");
        }
        validateScopes(request);
        validateSlabs(request.distanceSlabs(), model);
        validateComponents(request.components());
        validateAdjustments(request.passengerAdjustments());
    }

    private void validateScopes(FareRuleVersionRequest request) {
        if (request.trainId() != null) {
            Integer count = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM trains WHERE train_id = :id AND status <> 'RETIRED'
                    """, Map.of("id", request.trainId()), Integer.class);
            if (count == null || count != 1) throw invalid("Choose an existing active train");
        }
        if (request.categoryId() != null) {
            Integer count = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM train_categories WHERE category_id = :id AND active = TRUE
                    """, Map.of("id", request.categoryId()), Integer.class);
            if (count == null || count != 1) throw invalid("Choose an active train category");
        }
        if (request.trainId() != null && request.categoryId() != null) {
            Integer matches = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM trains
                    WHERE train_id = :trainId AND category_id = :categoryId
                    """, Map.of("trainId", request.trainId(), "categoryId", request.categoryId()), Integer.class);
            if (matches == null || matches != 1) throw invalid("The train does not belong to the selected category");
        }
        if (request.coachClassId() != null) {
            Integer count = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM coach_classes WHERE coach_class_id = :id AND active = TRUE
                    """, Map.of("id", request.coachClassId()), Integer.class);
            if (count == null || count != 1) throw invalid("Choose an active coach class");
        }
    }

    private static void validateSlabs(List<DistanceSlabRequest> slabs, String model) {
        if (!model.equals("DISTANCE_SLABS") && !slabs.isEmpty()) {
            throw invalid("Distance slabs can only be used with the slab pricing model");
        }
        List<DistanceSlabRequest> ordered = slabs.stream()
                .sorted(Comparator.comparing(DistanceSlabRequest::minimumDistanceKm))
                .toList();
        for (int index = 0; index < ordered.size(); index++) {
            DistanceSlabRequest slab = ordered.get(index);
            if (slab.baseAmount().add(slab.perKmRate()).signum() <= 0) {
                throw invalid("Each distance slab must produce a positive base fare");
            }
            if (index > 0) {
                DistanceSlabRequest previous = ordered.get(index - 1);
                if (previous.maximumDistanceKm() == null
                        || previous.maximumDistanceKm().compareTo(slab.minimumDistanceKm()) > 0) {
                    throw invalid("Distance slabs must not overlap and an open-ended slab must be last");
                }
            }
        }
    }

    private void validateComponents(List<FareComponentRequest> components) {
        Map<String, Boolean> codes = new HashMap<>();
        for (FareComponentRequest component : components) {
            if (codes.put(component.componentCode(), true) != null) {
                throw invalid("Component codes must be unique within a fare rule version");
            }
            if (!List.of("CHARGE", "TAX", "DISCOUNT").contains(component.componentType())
                    || !List.of("FIXED", "PER_KM", "PERCENT_OF_BASE", "PERCENT_OF_SUBTOTAL")
                    .contains(component.calculationMethod())) {
                throw invalid("Choose a supported fare component type and calculation method");
            }
            if (component.calculationMethod().startsWith("PERCENT_")
                    && component.amount().compareTo(new BigDecimal("100")) > 0) {
                throw invalid("Percentage components cannot exceed 100 percent");
            }
            if (component.passengerCategoryId() != null) requireActiveCategory(component.passengerCategoryId());
        }
    }

    private void validateAdjustments(List<PassengerAdjustmentRequest> adjustments) {
        Map<UUID, Boolean> categories = new HashMap<>();
        for (PassengerAdjustmentRequest adjustment : adjustments) {
            if (categories.put(adjustment.passengerCategoryId(), true) != null) {
                throw invalid("Only one passenger adjustment may be configured per category");
            }
            if (!List.of("DISCOUNT_PERCENT", "DISCOUNT_FIXED", "CHARGE_PERCENT", "CHARGE_FIXED")
                    .contains(adjustment.adjustmentType())) {
                throw invalid("Choose a supported passenger adjustment");
            }
            if (adjustment.adjustmentType().endsWith("PERCENT")
                    && adjustment.adjustmentValue().compareTo(new BigDecimal("100")) > 0) {
                throw invalid("Passenger percentage adjustments cannot exceed 100 percent");
            }
            requireActiveCategory(adjustment.passengerCategoryId());
        }
    }

    private void requireActiveCategory(UUID categoryId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM fare_passenger_categories
                WHERE passenger_category_id = :id AND active = TRUE
                """, Map.of("id", categoryId), Integer.class);
        if (count == null || count != 1) throw invalid("Choose an active passenger category");
    }

    private FareQuoteResponse calculateQuote(
            FareQuoteRequest request,
            UUID ownerId,
            UUID simulationVersionId,
            boolean persist
    ) {
        TrainDetail detail = trainSearchService.detail(
                request.trainId(), request.fromStationId(), request.toStationId(), request.journeyDate());
        BigDecimal distanceKm = detail.journey().distanceKm();
        if (distanceKm == null || distanceKm.signum() <= 0) {
            throw new FareException(HttpStatus.UNPROCESSABLE_ENTITY, "FARE_DISTANCE_UNAVAILABLE",
                    "A verified journey distance is not available for this train and station pair");
        }
        CoachClass selectedClass = detail.journey().coachClasses().stream()
                .filter(item -> item.code().equalsIgnoreCase(request.coachClassCode()))
                .findFirst()
                .orElseThrow(() -> new FareException(
                        HttpStatus.UNPROCESSABLE_ENTITY, "FARE_CLASS_UNAVAILABLE",
                        "The selected coach class is not configured for this train journey"));
        UUID classId = jdbc.query("""
                SELECT coach_class_id FROM coach_classes
                WHERE class_code = :code AND active = TRUE
                """, Map.of("code", selectedClass.code()), result -> result.next()
                ? result.getObject(1, UUID.class) : null);
        if (classId == null) {
            throw new FareException(HttpStatus.UNPROCESSABLE_ENTITY, "FARE_CLASS_UNAVAILABLE",
                    "The selected coach class is no longer active");
        }

        RuleRow rule = simulationVersionId == null
                ? selectPublishedRule(detail, classId, request.journeyDate())
                : simulationRule(simulationVersionId, detail, classId, request.journeyDate());
        FareCalculator.Pricing pricing = pricing(rule, request, distanceKm);
        FareCalculator.Calculation calculation = FareCalculator.calculate(distanceKm, pricing);
        Instant createdAt = Instant.now();
        Instant expiresAt = persist ? createdAt.plus(quoteValidity) : null;
        FareQuoteResponse response = new FareQuoteResponse(
                persist ? UUID.randomUUID() : null,
                "INR",
                persist ? "ACTIVE" : "SIMULATION",
                distanceKm,
                detail.journey().trainNumber(),
                detail.journey().trainName(),
                detail.journey().origin().stationName(),
                detail.journey().destination().stationName(),
                request.journeyDate(),
                selectedClass.code(),
                selectedClass.name(),
                calculation.passengers().size(),
                calculation.passengers(),
                calculation.totalAmount(),
                createdAt,
                expiresAt,
                rule.versionNumber(),
                "Indicative quote only. This is not a reservation, ticket, seat allocation, or payment."
        );
        if (persist) saveQuote(response, request, ownerId, distanceKm, rule.versionId());
        return response;
    }

    private FareCalculator.Pricing pricing(RuleRow rule, FareQuoteRequest request, BigDecimal distanceKm) {
        List<FareCalculator.DistanceSlab> slabs = jdbc.query("""
                SELECT minimum_distance_km, maximum_distance_km, base_amount, per_km_rate
                FROM fare_rule_distance_slabs
                WHERE fare_rule_version_id = :versionId
                ORDER BY minimum_distance_km
                """, Map.of("versionId", rule.versionId()), (row, index) -> new FareCalculator.DistanceSlab(
                row.getBigDecimal("minimum_distance_km"),
                row.getBigDecimal("maximum_distance_km"),
                row.getBigDecimal("base_amount"),
                row.getBigDecimal("per_km_rate")));
        List<FareCalculator.Component> components = jdbc.query("""
                SELECT passenger_category_id, component_code, display_name, component_type,
                       calculation_method, amount, maximum_amount, minimum_distance_km,
                       calculation_order, enabled
                FROM fare_rule_components
                WHERE fare_rule_version_id = :versionId
                ORDER BY calculation_order, component_code
                """, Map.of("versionId", rule.versionId()), (row, index) -> new FareCalculator.Component(
                row.getObject("passenger_category_id", UUID.class),
                row.getString("component_code"),
                row.getString("display_name"),
                row.getString("component_type"),
                row.getString("calculation_method"),
                row.getBigDecimal("amount"),
                row.getBigDecimal("maximum_amount"),
                row.getBigDecimal("minimum_distance_km"),
                row.getInt("calculation_order"),
                row.getBoolean("enabled")));
        Map<String, FarePassengerCategory> requestedCategories = new HashMap<>();
        for (PassengerRequest passenger : request.passengers()) {
            FarePassengerCategory category = requestedCategories.computeIfAbsent(
                    categoryKey(passenger.categoryCode()),
                    this::passengerCategory);
            validatePassengerAge(category, passenger.age());
        }
        Map<UUID, FareCalculator.PassengerAdjustment> adjustments = new HashMap<>();
        List<UUID> categoryIds = requestedCategories.values().stream()
                .map(FarePassengerCategory::passengerCategoryId)
                .distinct()
                .toList();
        Map<UUID, FarePassengerCategory> categoriesById = new HashMap<>();
        requestedCategories.values().forEach(category ->
                categoriesById.put(category.passengerCategoryId(), category));
        jdbc.query("""
                SELECT passenger_category_id, adjustment_type, adjustment_value, maximum_amount
                FROM fare_rule_passenger_adjustments
                WHERE fare_rule_version_id = :versionId
                  AND passenger_category_id IN (:categoryIds)
                """, new MapSqlParameterSource()
                .addValue("versionId", rule.versionId())
                .addValue("categoryIds", categoryIds), row -> {
            UUID categoryId = row.getObject("passenger_category_id", UUID.class);
            String type = row.getString("adjustment_type");
            FarePassengerCategory category = categoriesById.get(categoryId);
            adjustments.put(
                    categoryId,
                    new FareCalculator.PassengerAdjustment(
                            category.categoryName() + " " + type.toLowerCase(Locale.ROOT).replace('_', ' '),
                            type,
                            row.getBigDecimal("adjustment_value"),
                            row.getBigDecimal("maximum_amount")));
        });
        List<FareCalculator.PassengerPricing> passengers = request.passengers().stream()
                .map(passenger -> {
                    FarePassengerCategory category = requestedCategories.get(categoryKey(passenger.categoryCode()));
                    return new FareCalculator.PassengerPricing(
                            category.passengerCategoryId(),
                            category.categoryCode(),
                            category.categoryName(),
                            adjustments.get(category.passengerCategoryId()));
                })
                .toList();
        return new FareCalculator.Pricing(
                rule.pricingModel(), rule.baseAmount(), rule.perKmRate(),
                slabs, components, passengers);
    }

    private FarePassengerCategory passengerCategory(String code) {
        FarePassengerCategory category = jdbc.query("""
                SELECT passenger_category_id, category_code, category_name, minimum_age, maximum_age, active
                FROM fare_passenger_categories
                WHERE category_code = :code AND active = TRUE
                """, Map.of("code", categoryKey(code)), result -> result.next()
                ? new FarePassengerCategory(
                        result.getObject("passenger_category_id", UUID.class),
                        result.getString("category_code"),
                        result.getString("category_name"),
                        result.getObject("minimum_age", Integer.class),
                        result.getObject("maximum_age", Integer.class),
                        result.getBoolean("active"))
                : null);
        if (category == null) {
            throw new FareException(HttpStatus.UNPROCESSABLE_ENTITY, "FARE_PASSENGER_CATEGORY_UNAVAILABLE",
                    "Choose an active passenger category");
        }
        return category;
    }

    private static void validatePassengerAge(FarePassengerCategory category, Integer age) {
        if ((category.minimumAge() != null || category.maximumAge() != null) && age == null) {
            throw new FareException(HttpStatus.BAD_REQUEST, "PASSENGER_AGE_REQUIRED",
                    "Age is required for the selected passenger category");
        }

        if (age != null && ((category.minimumAge() != null && age < category.minimumAge())
                || (category.maximumAge() != null && age > category.maximumAge()))) {
            throw new FareException(HttpStatus.UNPROCESSABLE_ENTITY, "PASSENGER_AGE_OUT_OF_RANGE",
                    "Passenger age does not meet the selected category's configured age limits");
        }
    }

    private static String categoryKey(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private RuleRow selectPublishedRule(TrainDetail detail, UUID classId, LocalDate journeyDate) {
        UUID categoryId = jdbc.query("""
                SELECT category_id FROM trains WHERE train_id = :trainId
                """, Map.of("trainId", detail.journey().trainId()), result -> result.next()
                ? result.getObject(1, UUID.class) : null);
        if (categoryId == null) {
            throw notFound("TRAIN_JOURNEY_NOT_FOUND", "Train journey is no longer available");
        }
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("trainId", detail.journey().trainId())
                .addValue("categoryId", categoryId)
                .addValue("classId", classId)
                .addValue("journeyDate", journeyDate);
        List<RuleRow> matches = jdbc.query("""
                SELECT v.fare_rule_version_id, v.version_number, v.pricing_model,
                       v.base_amount, v.per_km_rate
                FROM fare_rule_versions v
                WHERE v.status = 'PUBLISHED'
                  AND v.effective_from <= :journeyDate
                  AND (v.effective_to IS NULL OR v.effective_to >= :journeyDate)
                  AND (v.train_id IS NULL OR v.train_id = :trainId)
                  AND (v.category_id IS NULL OR v.category_id = :categoryId)
                  AND (v.coach_class_id IS NULL OR v.coach_class_id = :classId)
                ORDER BY v.priority DESC,
                         ((v.train_id IS NOT NULL)::int + (v.category_id IS NOT NULL)::int
                          + (v.coach_class_id IS NOT NULL)::int) DESC,
                         v.effective_from DESC, v.fare_rule_version_id
                LIMIT 1
                """, parameters, FareService::mapRule);
        if (matches.isEmpty()) {
            throw new FareException(HttpStatus.UNPROCESSABLE_ENTITY, "FARE_CONFIGURATION_UNAVAILABLE",
                    "No approved and published fare rule is configured for this train, class, and journey date");
        }
        return matches.get(0);
    }

    private RuleRow simulationRule(UUID versionId, TrainDetail detail, UUID classId, LocalDate journeyDate) {
        RuleRow rule = jdbc.query("""
                SELECT fare_rule_version_id, version_number, pricing_model, base_amount, per_km_rate,
                       status, train_id, category_id, coach_class_id, effective_from, effective_to
                FROM fare_rule_versions WHERE fare_rule_version_id = :id
                """, Map.of("id", versionId), result -> result.next()
                ? new RuleRow(
                        result.getObject("fare_rule_version_id", UUID.class),
                        result.getInt("version_number"),
                        result.getString("pricing_model"),
                        result.getBigDecimal("base_amount"),
                        result.getBigDecimal("per_km_rate"),
                        result.getString("status"),
                        result.getObject("train_id", UUID.class),
                        result.getObject("category_id", UUID.class),
                        result.getObject("coach_class_id", UUID.class),
                        result.getObject("effective_from", LocalDate.class),
                        result.getObject("effective_to", LocalDate.class))
                : null);
        if (rule == null) throw notFound("FARE_RULE_VERSION_NOT_FOUND", "Fare rule version was not found");
        UUID categoryId = jdbc.query("""
                SELECT category_id FROM trains WHERE train_id = :trainId
                """, Map.of("trainId", detail.journey().trainId()), result -> result.next()
                ? result.getObject(1, UUID.class) : null);
        if ((rule.trainId() != null && !rule.trainId().equals(detail.journey().trainId()))
                || (rule.categoryId() != null && !rule.categoryId().equals(categoryId))
                || (rule.coachClassId() != null && !rule.coachClassId().equals(classId))
                || journeyDate.isBefore(rule.effectiveFrom())
                || (rule.effectiveTo() != null && journeyDate.isAfter(rule.effectiveTo()))) {
            throw invalid("The selected draft rule does not match the simulated journey and date");
        }
        return rule;
    }

    private void saveQuote(
            FareQuoteResponse quote,
            FareQuoteRequest request,
            UUID ownerId,
            BigDecimal distanceKm,
            UUID ruleVersionId
    ) {
        try {
            jdbc.update("""
                    INSERT INTO fare_quotes (
                        fare_quote_id, owner_user_id, train_id, origin_station_id,
                        destination_station_id, journey_date, coach_class_code,
                        fare_rule_version_id, distance_km, total_amount, snapshot, expires_at
                    ) VALUES (
                        :quoteId, :ownerId, :trainId, :originId, :destinationId,
                        :journeyDate, :classCode, :versionId, :distanceKm, :totalAmount,
                        CAST(:snapshot AS jsonb), :expiresAt
                    )
                    """, new MapSqlParameterSource()
                    .addValue("quoteId", quote.fareQuoteId())
                    .addValue("ownerId", ownerId)
                    .addValue("trainId", request.trainId())
                    .addValue("originId", request.fromStationId())
                    .addValue("destinationId", request.toStationId())
                    .addValue("journeyDate", request.journeyDate())
                    .addValue("classCode", quote.coachClassCode())
                    .addValue("versionId", ruleVersionId)
                    .addValue("distanceKm", distanceKm)
                    .addValue("totalAmount", quote.totalAmount())
                    .addValue("snapshot", objectMapper.writeValueAsString(quote))
                    .addValue("expiresAt", quote.expiresAt()));
        } catch (JsonProcessingException exception) {
            throw new FareException(HttpStatus.INTERNAL_SERVER_ERROR, "FARE_QUOTE_SERIALIZATION_FAILED",
                    "The fare quote could not be persisted");
        }
    }

    private void ensureNoPublishedOverlap(VersionState state) {
        Integer overlaps = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM fare_rule_versions other
                WHERE other.status = 'PUBLISHED'
                  AND other.fare_rule_version_id <> :versionId
                  AND other.priority = :priority
                  AND other.train_id IS NOT DISTINCT FROM :trainId
                  AND other.category_id IS NOT DISTINCT FROM :categoryId
                  AND other.coach_class_id IS NOT DISTINCT FROM :classId
                  AND (other.effective_to IS NULL OR other.effective_to >= :effectiveFrom)
                  AND (CAST(:effectiveTo AS date) IS NULL
                       OR other.effective_from <= CAST(:effectiveTo AS date))
                """, new MapSqlParameterSource()
                .addValue("versionId", state.versionId())
                .addValue("priority", state.priority())
                .addValue("trainId", state.trainId())
                .addValue("categoryId", state.categoryId())
                .addValue("classId", state.coachClassId())
                .addValue("effectiveFrom", state.effectiveFrom())
                .addValue("effectiveTo", state.effectiveTo()), Integer.class);
        if (overlaps != null && overlaps > 0) {
            throw conflict("FARE_RULE_EFFECTIVE_RANGE_CONFLICT",
                    "Another published rule has the same scope and priority during this effective period");
        }
    }

    private void requireNotAuthor(UUID versionId, UUID actorId) {
        Integer author = jdbc.queryForObject("""
                SELECT COUNT(*) FROM fare_rule_version_authors
                WHERE fare_rule_version_id = :versionId AND user_id = :actorId
                """, Map.of("versionId", versionId, "actorId", actorId), Integer.class);
        if (author != null && author > 0) {
            throw new FareException(HttpStatus.FORBIDDEN, "FARE_RULE_MAKER_CHECKER_REQUIRED",
                    "A fare-rule author or editor cannot approve or publish that version");
        }
    }

    private void audit(UUID versionId, UUID actorId, String event, Map<String, String> details) {
        try {
            jdbc.update("""
                    INSERT INTO fare_rule_audit_events (
                        fare_rule_audit_event_id, fare_rule_version_id, actor_user_id, event_type, details
                    ) VALUES (:id, :versionId, :actorId, :event, CAST(:details AS jsonb))
                    """, Map.of(
                    "id", UUID.randomUUID(),
                    "versionId", versionId,
                    "actorId", actorId,
                    "event", event,
                    "details", objectMapper.writeValueAsString(details)));
        } catch (JsonProcessingException exception) {
            throw new FareException(HttpStatus.INTERNAL_SERVER_ERROR, "FARE_AUDIT_SERIALIZATION_FAILED",
                    "The fare-rule audit event could not be recorded");
        }
    }

    private FareVersionSummary version(UUID versionId) {
        List<FareVersionSummary> rows = jdbc.query(
                VERSION_SELECT + " WHERE v.fare_rule_version_id = :id",
                Map.of("id", versionId),
                FareService::mapVersion);
        if (rows.isEmpty()) throw notFound("FARE_RULE_VERSION_NOT_FOUND", "Fare rule version was not found");
        return rows.get(0);
    }

    private VersionState versionState(UUID versionId, boolean lock) {
        String lockClause = lock ? " FOR UPDATE" : "";
        VersionState state = jdbc.query("""
                SELECT v.fare_rule_version_id, v.status, v.priority, v.train_id, v.category_id,
                       v.coach_class_id, v.effective_from, v.effective_to,
                       (SELECT COUNT(*) FROM fare_rule_version_authors a
                        WHERE a.fare_rule_version_id = v.fare_rule_version_id) AS author_count
                FROM fare_rule_versions v
                WHERE v.fare_rule_version_id = :versionId
                """ + lockClause,
                Map.of("versionId", versionId),
                result -> result.next()
                        ? new VersionState(
                                result.getObject("fare_rule_version_id", UUID.class),
                                result.getString("status"),
                                result.getInt("priority"),
                                result.getObject("train_id", UUID.class),
                                result.getObject("category_id", UUID.class),
                                result.getObject("coach_class_id", UUID.class),
                                result.getObject("effective_from", LocalDate.class),
                                result.getObject("effective_to", LocalDate.class),
                                result.getInt("author_count"))
                        : null);
        if (state == null) throw notFound("FARE_RULE_VERSION_NOT_FOUND", "Fare rule version was not found");
        return state;
    }

    private MapSqlParameterSource versionParams(FareRuleVersionRequest request, UUID actorId) {
        return new MapSqlParameterSource()
                .addValue("trainId", request.trainId())
                .addValue("categoryId", request.categoryId())
                .addValue("coachClassId", request.coachClassId())
                .addValue("pricingModel", request.pricingModel())
                .addValue("baseAmount", request.baseAmount())
                .addValue("perKmRate", request.perKmRate())
                .addValue("priority", request.priority())
                .addValue("effectiveFrom", request.effectiveFrom())
                .addValue("effectiveTo", request.effectiveTo())
                .addValue("reason", request.changeReason().trim())
                .addValue("actorId", actorId);
    }

    private static FareVersionSummary mapVersion(java.sql.ResultSet row, int index)
            throws java.sql.SQLException {
        return new FareVersionSummary(
                row.getObject("fare_rule_id", UUID.class),
                row.getString("rule_name"),
                row.getObject("fare_rule_version_id", UUID.class),
                row.getInt("version_number"),
                row.getString("status"),
                row.getObject("train_id", UUID.class),
                row.getString("train_number"),
                row.getObject("category_id", UUID.class),
                row.getString("category_code"),
                row.getObject("coach_class_id", UUID.class),
                row.getString("class_code"),
                row.getString("pricing_model"),
                row.getString("currency"),
                row.getBigDecimal("base_amount"),
                row.getBigDecimal("per_km_rate"),
                row.getInt("priority"),
                row.getObject("effective_from", LocalDate.class),
                row.getObject("effective_to", LocalDate.class),
                row.getString("change_reason"),
                row.getString("created_by"),
                row.getTimestamp("created_at").toInstant(),
                row.getString("approved_by"),
                row.getTimestamp("approved_at") == null ? null : row.getTimestamp("approved_at").toInstant(),
                row.getString("published_by"),
                row.getTimestamp("published_at") == null ? null : row.getTimestamp("published_at").toInstant());
    }

    private static RuleRow mapRule(java.sql.ResultSet row, int index) throws java.sql.SQLException {
        return new RuleRow(
                row.getObject("fare_rule_version_id", UUID.class),
                row.getInt("version_number"),
                row.getString("pricing_model"),
                row.getBigDecimal("base_amount"),
                row.getBigDecimal("per_km_rate"),
                "PUBLISHED",
                null,
                null,
                null,
                null,
                null);
    }

    private FareQuoteResponse readQuote(String snapshot) {
        try {
            return objectMapper.readValue(snapshot, FareQuoteResponse.class);
        } catch (JsonProcessingException exception) {
            throw new FareException(HttpStatus.INTERNAL_SERVER_ERROR, "FARE_QUOTE_CORRUPTED",
                    "The stored fare quote could not be read");
        }
    }

    private static FareQuoteResponse withStatus(FareQuoteResponse quote, String status) {
        return new FareQuoteResponse(
                quote.fareQuoteId(), quote.currency(), status, quote.distanceKm(),
                quote.trainNumber(), quote.trainName(), quote.originName(), quote.destinationName(),
                quote.journeyDate(), quote.coachClassCode(), quote.coachClassName(),
                quote.passengerCount(), quote.passengers(), quote.totalAmount(),
                quote.createdAt(), quote.expiresAt(), quote.fareRuleVersionNumber(),
                quote.quoteOnlyNotice());
    }

    private static void requireDraft(VersionState state) {
        requireStatus(state, "DRAFT", "Only draft fare-rule versions can be edited or submitted");
    }

    private static void requireStatus(VersionState state, String expected, String message) {
        if (!state.status().equals(expected)) throw conflict("FARE_RULE_INVALID_STATE", message);
    }

    private static FareException invalid(String message) {
        return new FareException(HttpStatus.BAD_REQUEST, "FARE_RULE_VALIDATION_ERROR", message);
    }

    private static FareException conflict(String code, String message) {
        return new FareException(HttpStatus.CONFLICT, code, message);
    }

    private static FareException notFound(String code, String message) {
        return new FareException(HttpStatus.NOT_FOUND, code, message);
    }

    private record VersionState(
            UUID versionId,
            String status,
            int priority,
            UUID trainId,
            UUID categoryId,
            UUID coachClassId,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            int authorCount
    ) {
    }

    private record RuleRow(
            UUID versionId,
            int versionNumber,
            String pricingModel,
            BigDecimal baseAmount,
            BigDecimal perKmRate,
            String status,
            UUID trainId,
            UUID categoryId,
            UUID coachClassId,
            LocalDate effectiveFrom,
            LocalDate effectiveTo
    ) {
    }

    private record StoredQuote(UUID ownerId, String snapshot, Instant expiresAt) {
    }
}
