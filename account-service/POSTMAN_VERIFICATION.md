# Account Postman verification

Run: 2026-09-28T06:43:16.827Z
Runner: Newman 6.2.2 (Postman collection runner)
Database: MySQL 8.0.46
Service: http://localhost:8081

Result: 19 requests, 21 assertions, zero failures.

| Request | HTTP result |
| --- | --- |
| Register passenger | 201 |
| Login passenger | 200 |
| Register driver | 201 |
| Login driver | 200 |
| My profile | 200 |
| Update my profile | 200 |
| Missing token | 401 |
| Wrong password | 401 |
| Duplicate email | 409 |
| Admin self-registration forbidden | 403 |
| Short password | 400 |
| Cannot escalate own role | 400 |
| Passenger cannot administer | 403 |
| Login existing admin | 200 |
| Change role | 200 |
| Restore passenger role | 200 |
| Deactivate user | 200 |
| Inactive login rejected | 403 |
| Reactivate user | 200 |

The run used the versioned Account collection, temporary passenger/driver accounts and a temporary administrator promoted through the documented database procedure. Only accounts created for this run were removed afterward. No tokens or passwords are included in this report.

This verifies the Account collection through Newman, not a completed manual Postman desktop walkthrough or the four-service integration flow.
