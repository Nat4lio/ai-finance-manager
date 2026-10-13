# AI Finance Manager

Personal finance application built with Java 21, Spring Boot, PostgreSQL and Flyway. This is the initial backend foundation.

## Requirements
- JDK 21
- Maven 3.9+
- Docker Desktop (or Docker Engine + Compose)

## Start PostgreSQL
From this directory:

```bash
docker compose up -d postgres
```

## Run the backend

Generate a signing key in PowerShell before starting the application. Keep the value in the current shell only; do not commit it:

```powershell
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$bytes = New-Object byte[] 32
$rng.GetBytes($bytes)
$env:JWT_SECRET_BASE64 = [Convert]::ToBase64String($bytes)
$rng.Dispose()
```

```powershell
cd backend
mvn spring-boot:run
```

The API runs at `http://localhost:8080`.

Health endpoint:

```bash
curl http://localhost:8080/api/v1/health
```

Actuator health endpoint:

```bash
curl http://localhost:8080/actuator/health
```

Flyway applies `V1__initial_schema.sql` automatically. Hibernate validates the schema against mapped entities; as the first slice currently has no JPA entities, the database migration is the starting schema for the next implementation step.

## Important security notes
- The PostgreSQL password in `docker-compose.yml` is for local development only. Do not use it in production.
- Registration and login are available at `POST /api/v1/auth/register` and `POST /api/v1/auth/login`. Both return a 15-minute Bearer JWT; other API routes require that token. Passwords are stored with BCrypt, and email uniqueness is case-insensitive.
- `JWT_SECRET_BASE64` is required and must decode to at least 32 random bytes. Keep it outside source control. A token signed with a different secret becomes invalid, so production deployments need managed secret storage and a planned rotation procedure.
- JWTs are stateless and cannot be revoked before expiry yet. Before public production use, add a refresh/revocation strategy, login throttling, account recovery, and MFA as appropriate. Terminate TLS at a trusted HTTPS endpoint; never send tokens over plain HTTP.
- No CORS origins are enabled yet. Configure an explicit allowlist when the frontend origin is known; do not use a wildcard with authenticated requests.
- Never collect or store a user's bank login credentials. Use a regulated/open-banking provider's consent flow and store only the tokens/identifiers required by that integration, encrypted at rest.
- Keep AI provider keys in environment variables; never commit them to Git.

## Planned modules
- `auth`: registration, login, password hashing and JWT (initial slice implemented)
- `accounts`: financial accounts and bank connections
- `transactions`: normalized transaction model, search, deduplication and categorization
- `banking`: provider adapter and consent/synchronization flows
- `analytics`: monthly summaries, budgets and savings rate
- `ai`: transaction categorization and evidence-based financial Q&A
