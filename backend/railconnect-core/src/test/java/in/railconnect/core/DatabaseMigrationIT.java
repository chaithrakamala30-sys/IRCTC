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
        Integer basePassengerCategoryCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM fare_passenger_categories WHERE category_code = 'ADULT' AND active = TRUE",
                Integer.class);

        assertThat(connectionResult).isEqualTo(1);
        assertThat(migrationCount).isGreaterThanOrEqualTo(1);
        assertThat(metadataTableCount).isEqualTo(1);
        assertThat(masterDataTableCount).isEqualTo(11);
        assertThat(masterDataPermissionCount).isEqualTo(8);
        assertThat(fareTableCount).isEqualTo(9);
        assertThat(farePermissionCount).isEqualTo(4);
        assertThat(basePassengerCategoryCount).isEqualTo(1);
    }
}
