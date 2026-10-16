#!/usr/bin/env sh
set -eu

: "${POSTGRES_USER:?POSTGRES_USER must be set}"
: "${POSTGRES_DB:?POSTGRES_DB must be set}"
: "${MINI_ERP_DEV_DB_PASSWORD:?MINI_ERP_DEV_DB_PASSWORD must be set}"
: "${MINI_ERP_TEST_DB_PASSWORD:?MINI_ERP_TEST_DB_PASSWORD must be set}"

psql \
  --username "$POSTGRES_USER" \
  --dbname "$POSTGRES_DB" \
  --set ON_ERROR_STOP=1 \
  --set dev_role_password="$MINI_ERP_DEV_DB_PASSWORD" \
  --set test_role_password="$MINI_ERP_TEST_DB_PASSWORD" <<'EOSQL'
SELECT format(
  'CREATE ROLE %I LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOREPLICATION NOBYPASSRLS PASSWORD %L',
  'mini_erp_dev_app',
  :'dev_role_password'
)
WHERE NOT EXISTS (
  SELECT 1 FROM pg_roles WHERE rolname = 'mini_erp_dev_app'
)
\gexec

SELECT format(
  'CREATE ROLE %I LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOREPLICATION NOBYPASSRLS PASSWORD %L',
  'mini_erp_test_app',
  :'test_role_password'
)
WHERE NOT EXISTS (
  SELECT 1 FROM pg_roles WHERE rolname = 'mini_erp_test_app'
)
\gexec

SELECT format(
  'CREATE DATABASE %I OWNER %I TEMPLATE template0',
  'mini_erp_dev',
  'mini_erp_dev_app'
)
WHERE NOT EXISTS (
  SELECT 1 FROM pg_database WHERE datname = 'mini_erp_dev'
)
\gexec

SELECT format(
  'CREATE DATABASE %I OWNER %I TEMPLATE template0',
  'mini_erp_test',
  'mini_erp_test_app'
)
WHERE NOT EXISTS (
  SELECT 1 FROM pg_database WHERE datname = 'mini_erp_test'
)
\gexec

REVOKE CONNECT ON DATABASE mini_erp_dev FROM PUBLIC;
REVOKE CONNECT ON DATABASE mini_erp_test FROM PUBLIC;

GRANT CONNECT, TEMPORARY ON DATABASE mini_erp_dev TO mini_erp_dev_app;
GRANT CONNECT, TEMPORARY ON DATABASE mini_erp_test TO mini_erp_test_app;

\connect mini_erp_dev
REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT USAGE, CREATE ON SCHEMA public TO mini_erp_dev_app;

\connect mini_erp_test
REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT USAGE, CREATE ON SCHEMA public TO mini_erp_test_app;
EOSQL

unset MINI_ERP_DEV_DB_PASSWORD MINI_ERP_TEST_DB_PASSWORD
