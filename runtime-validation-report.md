# Runtime Validation Report

**Generated**: 2026-10-13  
**Target**: `C:\Users\jaana\Desktop\App`

## Summary

| Step | Status | Exit Code | Details |
|---|---|---:|---|
| Startup | PASS | 0 | Isolated Spring Boot instance started on port 8083 with H2 and the test schema; `/actuator/health` and `/api/v1/health` returned 200. |
| Integration Tests | PASS | 0 | `mvn -f backend\pom.xml verify`; 16 tests, 0 failures, 0 errors, 0 skipped. |
| API E2E | PASS | 0 | Live HTTP flows covered registration/login, authorization, dashboard filters, income/expense creation, goal create/move/delete, transaction deletion, balance corrections, and user isolation. |
| Browser E2E | PASS | n/a (manual) | Integrated browser exercised sign-in, income/expense forms, goal create/contribute/withdraw/delete, transaction deletion, and custom date filtering against the running app. |

**Overall**: PASS — automated integration tests and live API/browser flows passed. PostgreSQL migration execution remains an environment-specific validation gap.

## Test Evidence

- `backend/target/surefire-reports/`
- API integration coverage: `AuthApiIntegrationTest`, `DashboardApiIntegrationTest`, `GoalApiIntegrationTest`, `HealthApiIntegrationTest`, `TransactionApiIntegrationTest`
- HTTP end-to-end flow: isolated H2 server on port 8083; all scripted status and persistence assertions passed.
- Browser flows: integrated browser against the same isolated server; the new-account transaction form was exercised after removing its unintended `required` constraint.

## Environment and Gaps

- **Docker**: unavailable; `Get-Command docker` found no executable. H2 was used for isolated integration and HTTP tests.
- **Node.js / Playwright**: unavailable; `Get-Command node` found no executable, so Playwright automation could not be installed or run. The integrated browser was used to exercise the actual UI.
- **Database fidelity**: the PostgreSQL Flyway migration `V3__link_goal_movements.sql` was not executed against PostgreSQL in this environment. Verify it against the deployment PostgreSQL instance before release.
- **Resolved issue found during browser testing**: the transaction account selector was required even when the UI offered only an empty “create a primary account” option. Removing that constraint restored income/expense submission for a new account.
- Temporary E2E accounts and transactions were stored only in the in-memory H2 database; the isolated process was stopped after validation.
