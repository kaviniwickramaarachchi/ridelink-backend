# Architecture and Communication Rationale

Project: RideLink - Backend Microservices for a Ride-Sharing Platform
Course: IT3130 Application Development
Group: G112

## 1. Architecture Decision

RideLink uses four business microservices:

- Account Service
- Driver & Vehicle Service
- Ride Management Service
- Fare & Payment Service

This service split follows the main business responsibilities of a ride-sharing backend. The Account Service owns identity, registration, login, roles, password hashing, and JWT issuing. The Driver & Vehicle Service owns driver profiles, driver availability, driver location, service area, and vehicle records. The Ride Management Service owns the ride lifecycle, including request, assignment, acceptance, start, completion, and cancellation. The Fare & Payment Service owns fare estimation, final fare storage, simulated payment, payment retrieval, and receipt retrieval.

The four-service structure creates clear business boundaries and gives each team member a focused service area. It supports independent service ownership, independent databases, separation of responsibilities, independent development, independent testing, and maintainability. Account logic, driver/vehicle logic, ride lifecycle logic, and fare/payment logic are not mixed into one shared application.

Microservices are not automatically better than a monolith. RideLink accepts realistic costs in exchange for clearer service boundaries. These costs include increased operational complexity, network communication between services, distributed failure handling, data consistency across service boundaries, more configuration, and more testing than a single monolithic backend would require. For this assignment, those trade-offs are acceptable because the project is organized around separate backend services, team ownership, interservice communication, and service-specific persistence.

## 2. Microservices vs Monolithic Architecture

| Area | Microservices | Monolithic Architecture | RideLink Relevance |
| --- | --- | --- | --- |
| Deployment structure | Multiple independently executable services | One deployable backend application | RideLink separates Account, Driver, Ride, and Fare responsibilities. |
| Business boundaries | Explicit boundaries per service | Boundaries are packages/modules inside one application | The four RideLink domains map cleanly to four service boundaries. |
| Data ownership | Each service owns its database/schema | One application often owns one shared database | RideLink uses service-specific persistence and avoids direct cross-service database access. |
| Coupling | Services communicate through APIs | Internal calls and shared models can create tighter coupling | RideLink keeps cross-domain interaction explicit through REST contracts. |
| Team development | Members can work on separate services | Developers often work in one shared application | The four-member group can align ownership with the four required services. |
| Testing | Service tests plus integration contract checks | Simpler local integration, but broader shared regression scope | RideLink runs focused tests per service and tracks changes by service branch/PR. |
| Scalability | Services can scale independently | Whole application usually scales together | RideLink can conceptually scale ride/driver workloads separately in a production setting. |
| Failure handling | Must handle remote service failures | Most internal calls fail inside one process/transaction | RideLink documents explicit fallback and non-rollback behavior across service calls. |
| Operational complexity | Higher: ports, configs, databases, service availability | Lower: one app, fewer moving parts | RideLink accepts extra complexity for learning and assignment objectives. |
| Suitability for RideLink | Suitable for demonstrating service boundaries and communication | Simpler, but less aligned with the assignment's service ownership model | Microservices fit the required academic structure. |

Microservices were selected for this academic RideLink scenario because the group has four members and the project has exactly four required business services. The structure supports clear ownership, service-specific persistence, interservice communication learning objectives, independent testing, and Git traceability through feature branches and pull requests.

## 3. Communication Decision

The implemented interservice communication is synchronous REST/JSON:

A. Ride Service -> Driver & Vehicle Service
B. Ride Service -> Fare & Payment Service

Ride -> Driver uses internal Driver endpoints under `/internal/drivers/**`. These calls include `X-Internal-Api-Key` and are authorized as internal service calls. Public Driver endpoints under `/api/v1/drivers/**` remain JWT/RBAC-protected for drivers.

Ride -> Fare uses internal Fare endpoints under `/internal/fares/**`. These calls also include `X-Internal-Api-Key`. Ride creation calls Fare for an internal estimate before saving the ride. Ride completion triggers final fare creation after the completed ride state has committed.

Synchronous REST is appropriate because immediate request/response behavior is required. Driver assignment depends on current driver eligibility and reservation results. Ride creation needs an immediate fare estimate. Ride completion needs final fare creation so payment and receipt operations can follow. REST/OpenAPI is also practical for Swagger and Postman demonstration.

## 4. Synchronous REST vs Asynchronous Messaging

| Area | Synchronous REST/JSON | Asynchronous Messaging / Event-Driven Approach |
| --- | --- | --- |
| Response timing | Caller receives an immediate success/failure response | Processing may happen later |
| Coupling | Caller depends on target service availability and contract | Producer and consumer can be more loosely coupled through events |
| Failure handling | Failures are returned through HTTP status/errors | Failures need retries, dead-letter handling, and monitoring |
| Consistency | Supports immediate decision-making | Often uses eventual consistency |
| Complexity | Easier to run and demonstrate for this assignment | Requires broker setup, consumers, retries, and observability |
| Retry behavior | Caller can retry or apply local fallback | Broker/consumer infrastructure usually handles retry policies |
| Suitability for immediate RideLink decisions | Strong fit for driver eligibility, reservation, fare estimate, and final fare decisions | Less direct fit where the user needs an immediate decision |

Synchronous REST was selected for the implemented core interactions because RideLink's main workflows require immediate decisions. A ride request should know whether it was created and whether an eligible driver was assigned. A driver reservation conflict should be handled immediately. Fare estimate and final fare operations are also request/response style decisions in this project.

Asynchronous messaging was considered as an alternative architecture style but was not implemented. It could be useful in a production RideLink system for notifications, analytics, audit events, reporting, and other non-critical background processing. Those asynchronous features are outside the implemented assignment scope.

## 5. Interservice Failure Strategy

The implemented failure strategy is explicit:

- Fare estimate failure during ride creation: ride creation does not complete successfully.
- No eligible driver: the ride can remain `REQUESTED`.
- Driver reservation conflict: Ride catches the conflict and continues to the next eligible driver candidate where available.
- Driver reservation service failure: the failure is logged and the ride can remain `REQUESTED`.
- Final fare failure after ride completion: the failure is logged and does not roll back the already `COMPLETED` ride.
- Driver release failure after completion/cancellation: release is best-effort; failure is logged and does not undo the completed or cancelled ride state.

RideLink does not implement a distributed transaction coordinator. The project uses local transactions plus explicit follow-up calls where needed.

The verified Ride status names are `REQUESTED`, `ASSIGNED`, `ACCEPTED`, `IN_PROGRESS`, `COMPLETED`, and `CANCELLED`.

## 6. Data Ownership and Stable Identifiers

RideLink follows database-per-service ownership. Each service owns its own database or schema and other services must not directly access that database. Cross-service references are represented by stable identifiers, such as account IDs, driver IDs, ride IDs, and passenger IDs.

| Service | Database |
| --- | --- |
| Account Service | `ridelink_account_db` |
| Driver & Vehicle Service | `ridelink_driver_db` |
| Ride Management Service | `ridelink_ride_db` |
| Fare & Payment Service | `ridelink_fare_payment_db` |

All four services use JPA `ddl-auto=validate`, so the schema must exist and match the mapped entities before service startup.

## 7. Trade-offs and Limitations

RideLink intentionally keeps the architecture within assignment scope. The current design does not include an API Gateway, service registry, message broker, or distributed transaction coordinator. Payment is simulated rather than integrated with a real payment provider. Deployment is local-development oriented, with separate Spring Boot services and MySQL databases rather than production orchestration.

Synchronous REST means service availability matters. If Driver & Vehicle Service is unavailable, Ride Management cannot complete driver lookup or reservation. If Fare & Payment Service is unavailable, fare estimate and final fare operations are affected. These are acceptable scope decisions for the assignment, but they are not production-complete architecture features.

## 8. Final Verification Evidence

| Service | Tests |
| --- | ---: |
| Account Service | 22 |
| Driver & Vehicle Service | 24 |
| Ride Management Service | 146 |
| Fare & Payment Service | 34 |
| **Total** | **226** |

The final live E2E run passed 21/21 checks: 16 positive checks and 5 negative checks. It verified Account -> Driver -> Ride -> Fare/Payment end-to-end, including driver assignment, acceptance, `IN_PROGRESS`, completion, final fare creation, payment `SUCCESS`, receipt retrieval, and expected `401`, `403`, and `409` behaviors.

## 9. Final Architecture Rationale Summary

RideLink uses four backend microservices because the business domain naturally separates into account identity, driver and vehicle management, ride lifecycle management, and fare/payment processing. This architecture supports the Group G112 team structure, independent development, service-specific persistence, focused testing, and explicit REST-based interservice communication. The project accepts the added complexity of distributed services because it directly supports the IT3130 learning objectives around microservice boundaries, API communication, security, persistence ownership, and collaborative Git workflow, while remaining honest about limitations such as local deployment, synchronous service dependencies, simulated payment, and the absence of production infrastructure components.
