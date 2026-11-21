-- Runs once, the first time the Postgres data volume is created.
-- Each microservice owns its own database (database-per-service pattern).
-- Tables are created by each service on startup (spring.jpa.hibernate.ddl-auto=update).
CREATE DATABASE users_db;
CREATE DATABASE products_db;
CREATE DATABASE orders_db;
CREATE DATABASE payments_db;
