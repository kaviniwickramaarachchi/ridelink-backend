# RideLink Account Service

Owner: Malsha K.W.A.K (IT24200716). Java 21 LTS, Spring Boot 3.5.16, Maven 3.9.16 wrapper, MySQL Server 8.0.46. REST/JSON with Spring Data JPA, Spring Security, BCrypt, HS256 JWT, springdoc 2.8.9, JUnit 5 and Mockito.

This service owns only `ridelink_account_db.users`. It does not query the other services' databases. No frontend is required.

## First-time setup

1. Install Java 21 and MySQL Server 8.0.46. Maven is supplied by the wrapper.
2. In MySQL Workbench, open and execute `database/schema.sql`, or run `mysql -u root -p` and then `SOURCE D:/Ride-Link/ridelink-backend/account-service/database/schema.sql;` (adjust the path on another machine). This creates only the Account database and table. Hibernate uses `ddl-auto=validate` and never creates/changes production tables.
3. In this directory create `application-local.properties` with `DB_PASSWORD=your_password` and `JWT_SECRET=your_shared_random_secret`. This file is ignored by Git. Default database username is `root`; override with `DB_USERNAME` if needed.
4. Run the following from `account-service`:

```powershell
.\mvnw.cmd verify
.\mvnw.cmd spring-boot:run
```

On macOS/Linux use `sh mvnw verify` and `sh mvnw spring-boot:run`. Alternatively, after building: `java -jar target/account-service-0.0.1-SNAPSHOT.jar`.

Swagger: http://localhost:8081/swagger-ui/index.html
OpenAPI JSON: http://localhost:8081/v3/api-docs

## Configuration

| Variable | Meaning/default |
| --- | --- |
| DB_URL | jdbc:mysql://localhost:3306/ridelink_account_db?serverTimezone=UTC |
| DB_USERNAME | root |
| DB_PASSWORD | Required; no committed default |
| JWT_SECRET | Required; at least 32 UTF-8 bytes |

Environment variables override the local properties. `.env.example` documents the variables; Spring does not automatically load `.env` files. The ignored `account-service/application-local.properties` is loaded when launching from either the repository root (for example, an IDE) or the service directory. For any other working directory, supply the environment variables or set the IDE working directory to `account-service`. All services must use the same secret as **raw UTF-8 bytes**, HS256, and the numeric `userId` claim. Tokens also include `email`, `role`, `iat`, `exp` and `sub`; expiry is exactly 3600 seconds. No refresh tokens. Account has no internal service API in the agreed contract, so it does not need `INTERNAL_API_KEY`.

## API contract

| Method | Path | Access | Body |
| --- | --- | --- | --- |
| POST | /api/v1/auth/register | Public | name, email, password, phoneNumber, role |
| POST | /api/v1/auth/login | Public | email, password |
| GET | /api/v1/users/me | Bearer JWT | None |
| PATCH | /api/v1/users/me | Bearer JWT | name and/or phoneNumber |
| PATCH | /api/v1/admin/users/{userId}/status | ADMIN | accountStatus: ACTIVE or INACTIVE |
| PATCH | /api/v1/admin/users/{userId}/role | ADMIN | role: PASSENGER, DRIVER or ADMIN |

Registration allows PASSENGER/DRIVER only; accounts start ACTIVE. Email uniqueness is case-insensitive. Passwords require at least eight characters and at most 72 UTF-8 bytes (BCrypt limit). Phone numbers accept 7-20 characters consisting of digits, spaces, +, parentheses and hyphens. Unknown request fields are rejected so profile updates cannot change email, password, role or status. Responses never expose passwords/hashes.

Login returns `accessToken`, `tokenType`, `expiresIn`, and `user`. Add `Authorization: Bearer <accessToken>` to protected requests. Account reloads its own user on protected calls so inactive/deleted accounts and role changes take effect immediately in this service. Other services validating JWTs offline may retain the old role/status until token expiry; no additional cross-service revocation API is defined by the specification.

Errors use timestamp/status/error/message/path/fieldErrors. Invalid input returns 400; missing/invalid authentication 401; role restrictions and inactive login 403; missing user 404; duplicate email 409. An old token for a deactivated account returns 401.

## First administrator

ADMIN cannot self-register. Register an ordinary local demo account, then use a trusted database administrator connection to promote that **specific** account once:

```sql
UPDATE ridelink_account_db.users
SET role = 'ADMIN', updated_at = UTC_TIMESTAMP(6)
WHERE email = 'admin@example.com';
```

Log in again and use the admin APIs for subsequent role/status changes. There is no default administrator or committed administrator password.

## Postman and testing

Import `../postman/RideLink-Account.postman_collection.json` and `../postman/RideLink-Account.local.postman_environment.json`. Set `demoPassword` locally, select the environment, then run the Account and Negative Tests folders. Registration creates timestamped passenger/driver demo emails and captures IDs; login captures JWTs. Admin requests are separate and require `adminEmail`, `adminPassword`, and an already promoted account. Do not export populated secret values to Git.

`verify` runs 22 JUnit tests (Mockito business tests and HTTP/security integration tests using H2) and builds the executable JAR. These tests do not require MySQL. The runtime uses MySQL only. Tests cover duplicate emails, validation, hashing, login, inactive accounts, JWT claims/expiry/signatures, profile field restrictions, admin permissions and immediate local revocation. The Account-only GitHub Actions workflow builds with Java 21 on relevant PRs/pushes; other service owners provide their own checks.

## Structure

Controllers delegate to services; repositories access the owned database; DTOs isolate API models; security config validates JWTs and enforces roles; the exception handler returns the common error shape. `database/schema.sql` is the manually applied MySQL schema. Other microservices remain outside this implementation.

Version `0.0.1-SNAPSHOT` is a development build. Group integration, assessment release tagging, report/evidence and any required GenAI declaration remain group deliverables.

## Account feature completion

See [the PDF requirement checklist](ACCOUNT_REQUIREMENTS.md) for endpoint, security, database and verification coverage. The first administrator is created by the documented trusted-database promotion procedure above; there is no public admin registration. The agreed contract has no delete-user or list-all-users endpoint. Account deactivation is handled by the ADMIN status endpoint.
