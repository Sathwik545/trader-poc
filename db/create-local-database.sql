-- One-off setup for a local MariaDB. Run as root:
--   mariadb -u root -p < db/create-local-database.sql
-- aegis      the demo app's database (tables are created by Flyway when the app starts)
-- aegis_test used by the integration tests, so test runs never touch demo data
CREATE DATABASE IF NOT EXISTS aegis;
CREATE DATABASE IF NOT EXISTS aegis_test;
CREATE USER IF NOT EXISTS 'aegis'@'localhost' IDENTIFIED BY 'aegis';
GRANT ALL PRIVILEGES ON aegis.* TO 'aegis'@'localhost';
GRANT ALL PRIVILEGES ON aegis_test.* TO 'aegis'@'localhost';
