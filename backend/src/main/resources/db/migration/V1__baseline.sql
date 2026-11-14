-- V1: baseline.
-- Intentionally empty: it proves the Flyway pipeline works end to end
-- (the flyway_schema_history table is created and this version is recorded).
-- Real tables start in A1 (users, Spring Authorization Server tables).
--
-- Rules for every migration in this folder:
--   1. Never edit a migration that has been merged; add a new V<n>__... file instead.
--   2. One logical change per file, named after what it does.
--   3. Reviewed in the PR like any other code.
SELECT 1;
