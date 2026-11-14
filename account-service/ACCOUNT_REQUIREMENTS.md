# Account Service: PDF requirement checklist

Reference: RideLink Group Development Specification, baseline 23 September 2026, sections 4-7, 12-16. This checklist covers the Account implementation only.

## Functional contract (section 7)

| Capability | Implementation | Verification |
| --- | --- | --- |
| Create passenger/driver account | POST /api/v1/auth/register | 201, unique normalized email, minimum 8-character password, ACTIVE default, BCrypt hash; ADMIN registration rejected |
| Login | POST /api/v1/auth/login | Valid credentials and ACTIVE account required; JWT expires in 3600 seconds |
| Read own account | GET /api/v1/users/me | PASSENGER, DRIVER and ADMIN can read their own profile; missing/invalid token rejected |
| Update own account | PATCH /api/v1/users/me | Only name/phoneNumber accepted; cannot supply another user's ID or change protected fields |
| Admin activate/deactivate | PATCH /api/v1/admin/users/{userId}/status | ADMIN only; ACTIVE/INACTIVE supported; inactive accounts cannot log in or use Account APIs |
| Admin change role | PATCH /api/v1/admin/users/{userId}/role | ADMIN only; PASSENGER/DRIVER/ADMIN supported; fresh JWT contains updated role |

The PDF does not define account deletion, an admin user-list endpoint, password reset, refresh tokens or an Account frontend. Deactivation is the specified account-status control. No extra service contracts were invented.

## Administrator setup

Public registration must never create ADMIN accounts. Register a local account normally and promote that exact email through the trusted database procedure in README.md. Then log in and use the two ADMIN endpoints. Automated tests create their own admin fixtures; there is no permanent default admin or embedded admin password.

## Security and API standards (section 6)

- Spring Security enforces authentication and ADMIN route access. Method security also protects admin service methods.
- JWTs use HS256 and the shared, externally configured secret; userId, email, role, iat and exp are present and required on validation.
- Passwords use BCrypt and are excluded from response DTOs.
- Account reads its own current role/status when authenticating a token; role demotion and deactivation apply immediately here.
- Other services must agree on the same raw UTF-8 secret/HS256 format. Cross-service compatibility is pending team integration.
- REST/JSON uses /api/v1, camelCase and ISO-8601 UTC timestamps.
- Common error shape: timestamp, status, error, message, path, fieldErrors. Swagger error responses now reference ApiError.
- Database password/JWT secret remain in ignored local configuration or environment variables.

## Persistence and structure (sections 4, 5, 14, 15)

- Java 21, Spring Boot 3.5.16, Maven wrapper and MySQL 8.0.46; port 8081.
- Own database: ridelink_account_db. Table users includes all nine required fields, BIGINT AUTO_INCREMENT ID and unique email.
- database/schema.sql is manually applied; Hibernate ddl-auto=validate verifies mapping.
- Controllers, services, repositories, entities, request/response DTOs, security, config and exceptions are separated.
- No driver/vehicle/ride/payment data or cross-database queries are included.

## Verification and remaining group work

- 22 JUnit tests passed (18 HTTP/security integration tests plus 4 Mockito business tests). Tests run without MySQL using H2 and mocks; the application runtime uses MySQL.
- Tests cover all three roles, admin promotion/reactivation, rejected non-admin changes, duplicate email, malformed requests, field restrictions, expired/tampered/incomplete JWTs and the six OpenAPI operations/error schemas.
- Account Postman collection includes positive, negative and admin scenarios; see POSTMAN_VERIFICATION.md for the live run result.
- Account GitHub Actions workflow is present, but a hosted CI result is separate from local verification.
- Shared JWT verification, the combined four-service Postman flow, team report/diagrams, evidence, peer review and release tagging remain group deliverables.
- Latest local verification: 2026-09-30. Independent Nimbus verification confirms HS256/raw UTF-8 shared-secret compatibility and rejects a different secret. Ride Service end-to-end integration remains to be run.
