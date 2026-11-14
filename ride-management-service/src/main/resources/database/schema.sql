-- Manual DDL for the schema owned exclusively by the Ride Management Service: ridelink_ride_db.

CREATE TABLE rides (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    version BIGINT NOT NULL,
    passenger_id BIGINT NOT NULL,
    driver_id BIGINT NULL,
    service_area VARCHAR(100) NOT NULL,
    pickup_place_name VARCHAR(255) NOT NULL,
    pickup_latitude DECIMAL(10,7) NOT NULL,
    pickup_longitude DECIMAL(10,7) NOT NULL,
    destination_place_name VARCHAR(255) NOT NULL,
    destination_latitude DECIMAL(10,7) NOT NULL,
    destination_longitude DECIMAL(10,7) NOT NULL,
    status VARCHAR(32) NOT NULL,
    requested_at DATETIME(6) NOT NULL,
    assigned_at DATETIME(6) NULL,
    accepted_at DATETIME(6) NULL,
    started_at DATETIME(6) NULL,
    completed_at DATETIME(6) NULL,
    cancelled_at DATETIME(6) NULL,
    actual_distance DECIMAL(10,3) NULL COMMENT 'Actual ride distance in kilometres',
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL
);
