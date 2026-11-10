-- ============================================================================
-- RailConnect / IRCTC Drop Database Objects Script (Except V14 & Migrations)
-- ============================================================================
-- WARNING: This drops all tables, views, and functions created in migrations
-- V1 through V13 in the "railconnect" schema, while preserving:
--   - V14 ticket management tables (ticket%)
--   - Flyway migration tracking (flyway_schema_history)
-- ============================================================================

SET search_path TO railconnect, public;

DO $$
DECLARE
    r RECORD;
BEGIN
    -- 1. Drop all non-ticket views
    FOR r IN (
        SELECT table_name
        FROM information_schema.views
        WHERE table_schema = 'railconnect'
          AND table_name NOT LIKE 'ticket%'
    ) LOOP
        EXECUTE 'DROP VIEW IF EXISTS railconnect.' || quote_ident(r.table_name) || ' CASCADE;';
        RAISE NOTICE 'Dropped view: railconnect.%', r.table_name;
    END LOOP;

    -- 2. Drop all non-ticket tables (except flyway history)
    FOR r IN (
        SELECT tablename
        FROM pg_tables
        WHERE schemaname = 'railconnect'
          AND tablename NOT LIKE 'ticket%'
          AND tablename NOT LIKE 'flyway_%'
    ) LOOP
        EXECUTE 'DROP TABLE IF EXISTS railconnect.' || quote_ident(r.tablename) || ' CASCADE;';
        RAISE NOTICE 'Dropped table: railconnect.%', r.tablename;
    END LOOP;

    -- 3. Drop functions belonging to railconnect schema (excluding ticket functions)
    FOR r IN (
        SELECT p.proname, pg_get_function_identity_arguments(p.oid) AS args
        FROM pg_proc p
        JOIN pg_namespace n ON p.pronamespace = n.oid
        WHERE n.nspname = 'railconnect'
          AND p.proname NOT LIKE 'ticket%'
          AND p.proname NOT LIKE '%ticket%'
    ) LOOP
        EXECUTE 'DROP FUNCTION IF EXISTS railconnect.' || quote_ident(r.proname) || '(' || r.args || ') CASCADE;';
        RAISE NOTICE 'Dropped function: railconnect.%(%)', r.proname, r.args;
    END LOOP;

    RAISE NOTICE 'Completed: All non-V14 tables, views, and functions in schema "railconnect" have been dropped.';
END $$;
