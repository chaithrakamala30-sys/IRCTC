-- ============================================================================
-- RailConnect / IRCTC Complete Database Wipe & Reset Script
-- ============================================================================
-- WARNING: DESTRUCTIVE ACTION
-- Drops all tables, views, sequences, triggers, types, and functions
-- in both "railconnect" and "public" schemas.
--
-- Usage:
--   psql -h <host> -U <user> -d <database> -f drop_all_database_objects.sql
-- ============================================================================

\i backend/railconnect-core/src/main/resources/db/seed/drop_all_database_objects.sql

