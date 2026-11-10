-- ============================================================================
-- RailConnect / IRCTC Complete Database Wipe & Reset Script
-- ============================================================================
-- WARNING: DESTRUCTIVE ACTION
-- This script completely drops all tables, views, sequences, triggers, types,
-- and custom functions across both "railconnect" and "public" schemas.
--
-- After running this script, the database will be a completely fresh slate,
-- ready for a clean re-run of Flyway migrations from V1.
-- ============================================================================

-- 1. Completely drop the 'railconnect' schema and everything in it
DROP SCHEMA IF EXISTS railconnect CASCADE;

-- 2. Drop all user-defined objects in the 'public' schema
DO $$
DECLARE
    r RECORD;
BEGIN
    -- Drop all views in public schema
    FOR r IN (SELECT table_name FROM information_schema.views WHERE table_schema = 'public') LOOP
        EXECUTE 'DROP VIEW IF EXISTS public.' || quote_ident(r.table_name) || ' CASCADE;';
    END LOOP;

    -- Drop all tables in public schema
    FOR r IN (SELECT tablename FROM pg_tables WHERE schemaname = 'public') LOOP
        EXECUTE 'DROP TABLE IF EXISTS public.' || quote_ident(r.tablename) || ' CASCADE;';
    END LOOP;

    -- Drop all sequences in public schema
    FOR r IN (SELECT sequence_name FROM information_schema.sequences WHERE sequence_schema = 'public') LOOP
        EXECUTE 'DROP SEQUENCE IF EXISTS public.' || quote_ident(r.sequence_name) || ' CASCADE;';
    END LOOP;

    -- Drop all user-defined functions and procedures in public schema
    FOR r IN (
        SELECT p.proname, pg_catalog.pg_get_function_identity_arguments(p.oid) AS args
        FROM pg_catalog.pg_proc p
        JOIN pg_catalog.pg_namespace n ON n.oid = p.pronamespace
        WHERE n.nspname = 'public'
    ) LOOP
        EXECUTE 'DROP FUNCTION IF EXISTS public.' || quote_ident(r.proname) || '(' || r.args || ') CASCADE;';
    END LOOP;

    -- Drop all custom composite and enum types in public schema
    FOR r IN (
        SELECT typname
        FROM pg_type t
        JOIN pg_namespace n ON n.oid = t.typnamespace
        WHERE n.nspname = 'public'
          AND t.typtype IN ('e', 'c')
    ) LOOP
        EXECUTE 'DROP TYPE IF EXISTS public.' || quote_ident(r.typname) || ' CASCADE;';
    END LOOP;

    RAISE NOTICE 'Success: All tables, views, functions, triggers, and sequences have been dropped.';
END $$;

-- 3. Recreate clean 'railconnect' and 'public' schemas
CREATE SCHEMA IF NOT EXISTS railconnect;
CREATE SCHEMA IF NOT EXISTS public;

GRANT ALL ON SCHEMA railconnect TO postgres;
GRANT ALL ON SCHEMA public TO postgres;

SET search_path TO railconnect, public;

