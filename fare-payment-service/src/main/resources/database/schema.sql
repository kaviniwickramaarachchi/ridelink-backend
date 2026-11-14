-- Manual DDL for the schema owned exclusively by the Fare & Payment Service: ridelink_fare_payment_db.
CREATE TABLE final_fares (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ride_id BIGINT NOT NULL,
    passenger_id BIGINT NOT NULL,
    distance_km DOUBLE NOT NULL,
    duration_minutes DOUBLE NOT NULL,
    amount DOUBLE NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT uq_final_fares_ride_id UNIQUE (ride_id)
);

CREATE TABLE payments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ride_id BIGINT NOT NULL,
    passenger_id BIGINT NOT NULL,
    amount DOUBLE NOT NULL,
    method VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at DATETIME(6) NOT NULL,

    successful_ride_id BIGINT
        GENERATED ALWAYS AS (
            CASE
                WHEN status = 'SUCCESS' THEN ride_id
                ELSE NULL
            END
        ) STORED,

    CONSTRAINT uq_successful_payment_per_ride
        UNIQUE (successful_ride_id)
);

CREATE TABLE receipts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    payment_id BIGINT NOT NULL,
    receipt_number VARCHAR(32) NOT NULL,
    amount DOUBLE NOT NULL,
    issued_at DATETIME(6) NOT NULL,
    CONSTRAINT uq_receipts_payment_id UNIQUE (payment_id),
    CONSTRAINT uq_receipts_receipt_number UNIQUE (receipt_number)
);