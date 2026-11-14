# RideLink Ride Request Sequence Diagram

This sequence shows the final implemented RideLink workflow. It uses the actual Ride Management status names from the merged implementation: `REQUESTED`, `ASSIGNED`, `ACCEPTED`, `IN_PROGRESS`, `COMPLETED`, and `CANCELLED`.

```mermaid
sequenceDiagram
    autonumber
    actor Passenger
    participant Account as Account Service
    participant Fare as Fare & Payment Service
    participant Ride as Ride Management Service
    participant DriverSvc as Driver & Vehicle Service
    actor Driver

    Passenger->>Account: Register or login
    Account-->>Passenger: JWT containing userId and role

    Driver->>Account: Register or login
    Account-->>Driver: JWT containing userId and role
    Driver->>DriverSvc: Create driver profile, vehicle, service area, location, availability
    DriverSvc-->>Driver: Driver AVAILABLE

    Passenger->>Fare: Request fare estimate with JWT
    Fare-->>Passenger: Estimated distance and fare

    Passenger->>Ride: Create ride with JWT
    Ride->>Fare: Internal fare estimate with X-Internal-Api-Key
    alt Fare estimate succeeds
        Fare-->>Ride: Estimate response
    else Fare estimate fails
        Fare--x Ride: Service failure
        Ride-->>Passenger: Ride creation fails
    end

    Ride->>Ride: Create ride as REQUESTED
    Ride->>DriverSvc: Find eligible drivers with X-Internal-Api-Key
    alt Eligible driver found and reserved
        Ride->>DriverSvc: PATCH availability UNAVAILABLE
        DriverSvc-->>Ride: Reserved driver
        Ride->>Ride: Set status ASSIGNED
        Ride-->>Passenger: Ride ASSIGNED
    else No eligible driver or reservation failure
        DriverSvc-->>Ride: No eligible driver / reservation failure
        Ride->>Ride: Keep status REQUESTED
        Ride-->>Passenger: Ride REQUESTED
    end

    Driver->>Ride: Get assigned ride with JWT
    Ride-->>Driver: Assigned ride details

    Driver->>Ride: Accept assigned ride with JWT
    Ride->>Ride: Set status ACCEPTED
    Ride-->>Driver: Ride ACCEPTED

    Driver->>Ride: Start ride with JWT
    Ride->>Ride: Set status IN_PROGRESS
    Ride-->>Driver: Ride IN_PROGRESS

    Driver->>Ride: Complete ride with actual distance using JWT
    Ride->>Ride: Set status COMPLETED
    Ride-->>Driver: Ride COMPLETED

    Ride->>Fare: After commit, create final fare with X-Internal-Api-Key
    Fare-->>Ride: Final fare stored or failure logged

    Ride->>DriverSvc: After commit, release driver availability with X-Internal-Api-Key
    DriverSvc-->>Ride: Driver released or failure logged

    Passenger->>Fare: Record payment with JWT
    Fare-->>Passenger: Payment SUCCESS
    Passenger->>Fare: Retrieve payment with JWT
    Fare-->>Passenger: Payment details
    Passenger->>Fare: Retrieve receipt with JWT
    Fare-->>Passenger: Receipt details
```

## Implemented Interservice Interactions

Ride -> Driver uses synchronous REST with `X-Internal-Api-Key` on `/internal/drivers/**` for eligible lookup, driver identity resolution, and PATCH availability reservation/release. Public Driver endpoints under `/api/v1/drivers/**` remain JWT/RBAC protected. The Ride Driver client uses Java `HttpClient` / `JdkClientHttpRequestFactory` so PATCH availability calls work correctly.

Ride -> Fare uses synchronous REST with `X-Internal-Api-Key` on `/internal/fares/**`. Fare estimate occurs before ride persistence. Final fare creation occurs after the completed ride commits.

## Ride Lifecycle and Cancellation

```text
REQUESTED -> ASSIGNED -> ACCEPTED -> IN_PROGRESS -> COMPLETED
```

Cancellation is allowed only from `REQUESTED`, `ASSIGNED`, and `ACCEPTED`.

## Failure Behavior

Fare estimate failure prevents successful ride creation. Driver assignment or reservation failure can leave the ride in `REQUESTED`. Ride completion is committed before final-fare and driver-release follow-up calls; those follow-up failures are logged and do not roll back the completed ride.

## Final E2E Evidence

The final live E2E run passed 21/21 checks: 16 positive and 5 negative. It verified Account -> Driver -> Ride -> Fare/Payment end-to-end, including `ASSIGNED`, `ACCEPTED`, `IN_PROGRESS`, `COMPLETED`, final fare creation, payment `SUCCESS`, receipt retrieval, and expected `401`, `403`, and `409` behavior.
