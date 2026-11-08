package in.railconnect.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(
        named = "RAILCONNECT_DB_URL",
        matches = "jdbc:postgresql:.+"
)
class DatabaseMigrationIT {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void postgresConnectionAndFlywayMigrationAreAvailable() {
        Integer connectionResult = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        Integer migrationCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = TRUE",
                Integer.class);
        Integer metadataTableCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = current_schema()
                  AND table_name = 'app_metadata'
                """,
                Integer.class);
        Integer masterDataTableCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = current_schema()
                  AND table_name IN (
                    'stations', 'station_versions', 'train_categories', 'train_types', 'train_routes',
                    'train_route_versions', 'train_schedules', 'coach_configurations',
                    'physical_accommodations', 'train_composition_versions', 'master_data_audit_logs'
                  )
                """,
                Integer.class);
        Integer masterDataPermissionCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM permissions
                WHERE permission_code IN (
                    'operations:master-data:read',
                    'operations:stations:manage',
                    'operations:trains:manage',
                    'operations:routes:manage',
                    'operations:schedules:manage',
                    'operations:composition:manage',
                    'operations:master-data:publish',
                    'operations:master-data:audit:read'
                )
                """,
                Integer.class);
        Integer fareTableCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = current_schema()
                  AND table_name IN (
                    'fare_passenger_categories', 'fare_rules', 'fare_rule_versions',
                    'fare_rule_version_authors', 'fare_rule_distance_slabs', 'fare_rule_components',
                    'fare_rule_passenger_adjustments', 'fare_rule_audit_events', 'fare_quotes'
                  )
                """,
                Integer.class);
        Integer farePermissionCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM permissions
                WHERE permission_code IN (
                    'fares:rules:read', 'fares:rules:manage',
                    'fares:rules:approve', 'fares:rules:publish'
                )
                """,
                Integer.class);
        Integer inventoryTableCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = current_schema()
                  AND table_name IN (
                    'inventory_train_runs', 'inventory_run_stops', 'inventory_run_segments',
                    'inventory_coaches', 'inventory_berths', 'inventory_holds',
                    'inventory_hold_segments', 'inventory_blocks', 'inventory_block_segments',
                    'inventory_segment_claims', 'inventory_audit_log', 'inventory_configuration',
                    'inventory_hold_rate_limits'
                  )
                """,
                Integer.class);
        Integer inventoryPermissionCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM permissions
                WHERE permission_code IN (
                    'inventory:availability:read', 'inventory:holds:manage', 'inventory:runs:initialize',
                    'inventory:admin:read', 'inventory:blocks:manage'
                )
                """,
                Integer.class);
        Integer reservationTableCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = current_schema()
                  AND table_name IN (
                    'reservation_allocation_policies', 'reservation_requests',
                    'reservation_passengers', 'reservation_request_holds',
                    'reservation_allocations', 'reservation_allocation_segments',
                    'reservation_state_history', 'reservation_audit_log',
                    'reservation_policy_audit_log', 'reservation_request_rate_limits'
                  )
                """,
                Integer.class);
        Integer reservationPermissionCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM permissions
                WHERE permission_code LIKE 'reservations:%'
                """,
                Integer.class);
        Integer reservationTriggerCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM pg_trigger
                WHERE NOT tgisinternal
                  AND tgname IN (
                    'reservation_allocation_policy_transition_guard',
                    'reservation_passengers_immutable',
                    'reservation_request_holds_immutable',
                    'reservation_allocation_segment_range_guard',
                    'reservation_allocation_segments_immutable',
                    'reservation_request_state_transition_guard',
                    'reservation_allocation_state_transition_guard',
                    'reservation_state_history_append_only',
                    'reservation_audit_append_only',
                    'reservation_policy_audit_append_only'
                  )
                """,
                Integer.class);
        Integer publishedReservationPolicyCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM reservation_allocation_policies
                WHERE status = 'PUBLISHED' AND version_number = 1
                """,
                Integer.class);
        Integer inventorySnapshotTriggerCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM pg_trigger
                WHERE NOT tgisinternal
                  AND tgname IN (
                    'inventory_train_run_snapshot_immutable',
                    'inventory_run_stops_immutable',
                    'inventory_run_segments_immutable',
                    'inventory_coaches_immutable',
                    'inventory_berths_immutable'
                  )
                """,
                Integer.class);
        Integer basePassengerCategoryCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM fare_passenger_categories WHERE category_code = 'ADULT' AND active = TRUE",
                Integer.class);
        Integer bookingTableCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = current_schema()
                  AND table_name IN (
                    'bookings', 'booking_passengers', 'booking_status_history',
                    'booking_idempotency_records', 'booking_request_rate_limits', 'booking_audit_log'
                  )
                """,
                Integer.class);
        Integer bookingPermissionCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM permissions
                WHERE permission_code IN ('bookings:own:create', 'bookings:own:read')
                """,
                Integer.class);
        Integer bookingTriggerCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM pg_trigger
                WHERE NOT tgisinternal
                  AND tgname IN (
                    'bookings_snapshot_immutable',
                    'booking_passengers_immutable',
                    'booking_status_history_append_only',
                    'booking_audit_append_only',
                    'booking_idempotency_immutable'
                  )
                """,
                Integer.class);

        assertThat(connectionResult).isEqualTo(1);
        assertThat(migrationCount).isGreaterThanOrEqualTo(1);
        assertThat(metadataTableCount).isEqualTo(1);
        assertThat(masterDataTableCount).isEqualTo(11);
        assertThat(masterDataPermissionCount).isEqualTo(8);
        assertThat(fareTableCount).isEqualTo(9);
        assertThat(farePermissionCount).isEqualTo(4);
        assertThat(basePassengerCategoryCount).isEqualTo(1);
        assertThat(inventoryTableCount).isEqualTo(13);
        assertThat(inventoryPermissionCount).isEqualTo(5);
        assertThat(inventorySnapshotTriggerCount).isEqualTo(5);
        assertThat(reservationTableCount).isEqualTo(10);
        assertThat(reservationPermissionCount).isGreaterThanOrEqualTo(11);
        assertThat(reservationTriggerCount).isEqualTo(10);
        assertThat(publishedReservationPolicyCount).isEqualTo(1);
        assertThat(bookingTableCount).isEqualTo(6);
        assertThat(bookingPermissionCount).isEqualTo(2);
        assertThat(bookingTriggerCount).isEqualTo(5);
    }
}
