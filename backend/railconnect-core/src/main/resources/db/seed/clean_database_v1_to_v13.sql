-- ============================================================================
-- RailConnect / IRCTC Database Clean / Truncate Script (Schema: railconnect)
-- ============================================================================
-- Purpose:
--   Truncates all tables in the "railconnect" schema up to migration V13,
--   leaving table schemas and Flyway migration records intact.
--
-- Excluded:
--   - V14 tables (tickets, ticket_passengers, etc.)
--   - flyway_schema_history
--   - spring_session tables
-- ============================================================================

SET search_path TO railconnect, public;

DO $$
DECLARE
    r RECORD;
    v_truncate_sql TEXT := '';
BEGIN
    -- Query all existing tables specifically inside the "railconnect" schema
    FOR r IN (
        SELECT tablename
        FROM pg_tables
        WHERE schemaname = 'railconnect'
          AND tablename NOT LIKE 'flyway_%'
          AND tablename NOT LIKE 'ticket%'
          AND tablename NOT LIKE 'spring_session%'
          AND tablename NOT IN ('roles', 'permissions', 'role_permissions')
        ORDER BY tablename
    ) LOOP
        v_truncate_sql := v_truncate_sql || 'railconnect.' || quote_ident(r.tablename) || ', ';
    END LOOP;

    IF length(v_truncate_sql) > 0 THEN
        -- Remove trailing comma and execute CASCADE TRUNCATE
        v_truncate_sql := 'TRUNCATE TABLE ' || substring(v_truncate_sql FROM 1 FOR length(v_truncate_sql) - 2) || ' RESTART IDENTITY CASCADE;';
        EXECUTE v_truncate_sql;
        RAISE NOTICE 'Success: All tables in schema "railconnect" up to V13 have been emptied and sequences restarted.';
    ELSE
        RAISE NOTICE 'No matching tables found in schema "railconnect".';
    END IF;
END $$;
