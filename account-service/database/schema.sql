CREATE DATABASE IF NOT EXISTS ridelink_account_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE ridelink_account_db;
CREATE TABLE IF NOT EXISTS users (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 name VARCHAR(100) NOT NULL,
 email VARCHAR(254) NOT NULL,
 password_hash VARCHAR(100) NOT NULL,
 phone_number VARCHAR(20) NOT NULL,
 role VARCHAR(20) NOT NULL,
 account_status VARCHAR(20) NOT NULL,
 created_at DATETIME(6) NOT NULL,
 updated_at DATETIME(6) NOT NULL,
 CONSTRAINT uk_users_email UNIQUE (email),
 CONSTRAINT chk_users_role CHECK (role IN ('PASSENGER','DRIVER','ADMIN')),
 CONSTRAINT chk_users_status CHECK (account_status IN ('ACTIVE','INACTIVE'))
);
