-- Database schema owned exclusively by the Driver & Vehicle Service.
-- Database: ridelink_driver_db

CREATE TABLE IF NOT EXISTS driver_profiles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id BIGINT NOT NULL,
    availability_status VARCHAR(20) NOT NULL DEFAULT 'UNAVAILABLE',
    service_area VARCHAR(100),
    current_latitude DECIMAL(9, 6),
    current_longitude DECIMAL(10, 6),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),

    CONSTRAINT uq_driver_profiles_account_id UNIQUE (account_id),
    CONSTRAINT chk_driver_availability
        CHECK (availability_status IN ('AVAILABLE', 'UNAVAILABLE')),
    CONSTRAINT chk_driver_latitude
        CHECK (current_latitude IS NULL
            OR current_latitude BETWEEN -90 AND 90),
    CONSTRAINT chk_driver_longitude
        CHECK (current_longitude IS NULL
            OR current_longitude BETWEEN -180 AND 180)
);

CREATE INDEX idx_driver_eligibility
    ON driver_profiles (availability_status, service_area);

CREATE TABLE IF NOT EXISTS vehicles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    driver_id BIGINT NOT NULL,
    registration_number VARCHAR(30) NOT NULL,
    make VARCHAR(100) NOT NULL,
    model VARCHAR(100) NOT NULL,
    vehicle_type VARCHAR(20) NOT NULL,
    colour VARCHAR(50) NOT NULL,
    vehicle_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),

    CONSTRAINT uq_vehicles_registration_number
        UNIQUE (registration_number),
    CONSTRAINT fk_vehicles_driver
        FOREIGN KEY (driver_id) REFERENCES driver_profiles (id),
    CONSTRAINT chk_vehicle_type
        CHECK (vehicle_type IN ('CAR', 'BIKE')),
    CONSTRAINT chk_vehicle_status
        CHECK (vehicle_status IN ('ACTIVE', 'INACTIVE'))
);

CREATE INDEX idx_vehicles_driver_status
    ON vehicles (driver_id, vehicle_status);