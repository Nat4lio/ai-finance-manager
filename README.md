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

```bash
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
- Spring Security currently uses HTTP Basic as a temporary development gate for all endpoints except health. Replace this with user registration/login and JWT before implementing multi-user financial endpoints or exposing the API outside localhost.
- Never collect or store a user's bank login credentials. Use a regulated/open-banking provider's consent flow and store only the tokens/identifiers required by that integration, encrypted at rest.
- Keep AI provider keys in environment variables; never commit them to Git.

## Planned modules
- `auth`: registration, login, password hashing and JWT
- `accounts`: financial accounts and bank connections
- `transactions`: normalized transaction model, search, deduplication and categorization
- `banking`: provider adapter and consent/synchronization flows
- `analytics`: monthly summaries, budgets and savings rate
- `ai`: transaction categorization and evidence-based financial Q&A
