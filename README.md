# AI Finance Manager

Personal finance application built with Java 21, Spring Boot and PostgreSQL. It provides a responsive dashboard, account registration and login, and manual income and expense tracking.

## Requirements

- JDK 21
- Maven 3.9+
- Docker Desktop (or Docker Engine + Compose)

## Start PostgreSQL

From the repository root:

```powershell
docker compose up -d postgres
```

## Configure and run the backend

Generate a JWT signing key in the same PowerShell window. Keep it outside source control:

```powershell
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$bytes = New-Object byte[] 32
$rng.GetBytes($bytes)
$env:JWT_SECRET_BASE64 = [Convert]::ToBase64String($bytes)
$rng.Dispose()

Set-Location .\backend
mvn spring-boot:run
```

The application is available at `http://localhost:8080`. Flyway applies database migrations at startup; Hibernate validates the mapped entities against the schema.

## Authentication

Open `http://localhost:8080` and create an account with a username and password, or sign in with an existing username. Registration and login issue a short-lived JWT.

Authentication API endpoints:

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/v1/auth/register` | Create an account and return a JWT |
| `POST` | `/api/v1/auth/login` | Authenticate and return a JWT |

Passwords are stored with BCrypt. Usernames are unique without case sensitivity and must contain 3–64 letters, numbers, dots, hyphens, or underscores. Registration requires only a username and password.

## Dashboard features

After signing in, the dashboard shows net worth, income and expenses, cash flow, category spending, recent transactions, and savings goals. Users can:

- Add income and expenses; a primary account is created automatically when needed.
- Filter dashboard data by preset or custom date ranges (up to five years).
- Create savings goals, add or withdraw money, and delete goals.
- Delete transactions; account balances and related goal progress are corrected.

The API returns financial data only for the authenticated user. Protected API requests require the 15-minute JWT access token in the `Authorization: Bearer <token>` header.

## Health checks

- `http://localhost:8080/api/v1/health`
- `http://localhost:8080/actuator/health`

## Tests

The test suite uses an isolated in-memory H2 database; Docker is not required:

```powershell
Set-Location .\backend
mvn test
```

## Security notes

- The PostgreSQL password in `docker-compose.yml` is for local development only.
- `JWT_SECRET_BASE64` must decode to at least 32 random bytes. Production deployments should use managed secret storage.
- JWTs are stateless and cannot currently be revoked before expiry. Add refresh/revocation and login throttling before public production use.
- Terminate TLS at a trusted HTTPS endpoint. Never transmit credentials or tokens over plain HTTP.
- Configure an explicit CORS allowlist when a separate frontend origin is introduced.
- Never collect bank login credentials. Use a regulated/open-banking provider's consent flow.
- Keep AI provider keys in environment variables.
