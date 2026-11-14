# RideLink Driver and Vehicle Service

The Driver and Vehicle Service manages driver profiles, vehicles, service areas, simulated locations and driver availability.

## Service Information

- Port: `8082`
- Database: `ridelink_driver_db`
- Java: `21`
- Spring Boot: `3.5.16`
- Database engine: MySQL 8

## Required Environment Variables

| Variable | Description |
|---|---|
| `DB_URL` | JDBC connection URL for `ridelink_driver_db` |
| `DB_USERNAME` | Driver service database username |
| `DB_PASSWORD` | Driver service database password |
| `JWT_SECRET` | Shared HS256 secret used by the Account Service |
| `INTERNAL_API_KEY` | Shared key used for service-to-service requests |

The `JWT_SECRET` must exactly match the secret used to issue tokens in the Account Service. Do not commit actual passwords or secrets.

## Local PowerShell Configuration

Replace the placeholder values before running the service:

```powershell
$env:DB_URL = 'jdbc:mysql://localhost:3306/ridelink_driver_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC'
$env:DB_USERNAME = 'ridelink_driver_user'
$env:DB_PASSWORD = '<database-password>'
$env:JWT_SECRET = '<same-secret-as-account-service>'
$env:INTERNAL_API_KEY = '<shared-internal-api-key>'

mvn spring-boot:run
```

The database tables must match:

```text
src/main/resources/database/schema.sql
```

## Driver JWT Endpoints

These endpoints require:

```http
Authorization: Bearer <driver-jwt>
```

The JWT must contain:

- `userId`
- `email`
- `role` with the value `DRIVER`

| Method | Endpoint | Purpose |
|---|---|---|
| `POST` | `/api/v1/drivers/profile` | Create the authenticated driver's profile |
| `GET` | `/api/v1/drivers/me` | Get the authenticated driver's profile |
| `PATCH` | `/api/v1/drivers/me` | Update service area or profile location |
| `POST` | `/api/v1/drivers/me/vehicles` | Add a vehicle |
| `GET` | `/api/v1/drivers/me/vehicle` | Get the active vehicle |
| `PATCH` | `/api/v1/drivers/me/vehicle` | Update the active vehicle |
| `PATCH` | `/api/v1/drivers/me/vehicle/status` | Activate or deactivate a vehicle |
| `PATCH` | `/api/v1/drivers/me/availability` | Update driver availability |
| `PATCH` | `/api/v1/drivers/me/location` | Update simulated location |

The account ID is always read from the JWT. It is not accepted from the client request.

## Internal Endpoints

These endpoints require:

```http
X-Internal-Api-Key: <shared-internal-api-key>
```

| Method | Endpoint | Purpose |
|---|---|---|
| `GET` | `/api/v1/drivers/eligible?serviceArea={area}` | Find eligible drivers |
| `GET` | `/api/v1/drivers/by-account/{accountId}` | Resolve the driver ID for an account |
| `PATCH` | `/api/v1/drivers/{driverId}/availability/internal` | Reserve or release a driver |

A driver is eligible only when:

- Availability is `AVAILABLE`
- The driver has an `ACTIVE` vehicle
- The service area matches

Reserve a driver:

```json
{
  "availabilityStatus": "UNAVAILABLE"
}
```

Release a driver:

```json
{
  "availabilityStatus": "AVAILABLE"
}
```

Reservation is atomic. Attempting to reserve a driver who is no longer available returns HTTP `409 Conflict`.

## Swagger/OpenAPI

After starting the service:

- Swagger UI: `http://localhost:8082/swagger-ui/index.html`
- OpenAPI JSON: `http://localhost:8082/v3/api-docs`

Swagger provides separate authorization schemes for JWT and `X-Internal-Api-Key`.

## Build and Test

Run all tests:

```powershell
mvn test
```

Create the executable JAR:

```powershell
mvn clean package
```

The current test suite covers business rules, request validation, JWT authorization and internal API-key protection.