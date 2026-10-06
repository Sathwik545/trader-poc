@echo off
rem Builds the app (tests skipped) and starts it on http://localhost:8080 against the local
rem MariaDB database "aegis". First-time database setup: db\create-local-database.sql.
rem Extra arguments are passed to the app, for example: run-demo.cmd --server.port=9090
setlocal
cd /d "%~dp0"
call "%~dp0mvnw.cmd" -q -DskipTests package
if errorlevel 1 exit /b 1
java -jar "%~dp0target\aegis-deal-detection-service-0.1.0-SNAPSHOT.jar" %*
