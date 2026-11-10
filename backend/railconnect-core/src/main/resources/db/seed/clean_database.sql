-- ============================================================================
-- RailConnect / IRCTC Truncate / Reset Script (Up to Migration V13 Only)
-- ============================================================================
-- WARNING: This script empties all domain, transactional, and master-data tables
-- up to Migration V13, leaving the schema intact.
--
-- Excluded:
--   - V14 tables (tickets, ticket_events, ticket_verifications) are NOT touched.
--   - flyway_schema_history (to preserve migration tracking)
-- ============================================================================

DO $$
BEGIN
    -- Truncate tables created up to V13 in proper dependency order with CASCADE
    EXECUTE '
        TRUNCATE TABLE
            -- Payments & Ledgers (V12, V13)
            payment_webhook_events,
            payment_attempts,
            payment_orders,

            -- Bookings & Reservations (V10, V11)
            booking_passengers,
            bookings,
            reservation_allocation_items,
            reservation_allocations,

            -- Physical Seat Inventory & Runs (V9)
            inventory_run_seat_allocations,
            inventory_run_seats,
            inventory_run_coaches,
            inventory_run_segments,
            inventory_run_stops,
            inventory_train_runs,

            -- Fare Engine (V8)
            fare_rule_version_authors,
            fare_rule_versions,
            fare_rules,
            fare_passenger_categories,

            -- Railway Master Data (V5, V7)
            master_data_audit_logs,
            train_composition_coaches,
            train_compositions,
            train_composition_versions,
            physical_accommodations,
            accommodation_layouts,
            coaches,
            coach_configurations,
            coach_types,
            coach_classes,
            train_schedule_exceptions,
            train_schedule_days,
            train_schedules,
            train_route_stops,
            train_route_versions,
            train_routes,
            trains,
            train_types,
            train_categories,
            station_versions,
            station_facilities,
            stations,
            railway_divisions,
            railway_zones,

            -- Identity & Access Management (V2, V3, V4)
            audit_logs,
            authentication_events,
            auth_throttles,
            user_sessions,
            user_roles,
            role_permissions,
            permissions,
            roles,
            users
        RESTART IDENTITY CASCADE;
    ';

    RAISE NOTICE 'RailConnect tables up to V13 have been truncated and sequences restarted successfully. V14 ticket tables were preserved.';
END $$;
