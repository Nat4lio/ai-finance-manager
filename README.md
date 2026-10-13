# AI Finance Manager

Personal finance application built with Java 21, Spring Boot and PostgreSQL. It includes a responsive dashboard, authentication, manual income and expense entry, and a local demonstration account with sample financial data.

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

Flyway applies the PostgreSQL schema migrations automatically. Hibernate validates the mapped `AppUser` entity against the migrated schema at startup. Authenticated users can record manual income and expenses through the dashboard or `POST /api/v1/transactions`; these entries update the selected account balance and dashboard summaries together.

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
- `accounts`: financial accounts and balances for the dashboard (bank connections planned)
- `transactions`: transaction history, manual income/expense entry and dashboard spending summaries (search planned)
- `banking`: provider adapter and consent/synchronization flows
- `analytics`: monthly summaries, budgets and savings rate
- `ai`: transaction categorization and evidence-based financial Q&A

## Tutorial rápido (Windows / PowerShell)

O projeto inclui um **dashboard web responsivo**. A interface e a API são servidas pela mesma aplicação Spring Boot. O modo de demonstração cria um utilizador e movimentos fictícios no PostgreSQL; os valores do painel são lidos da base de dados.

### 1. Pré-requisitos

- JDK 21
- Maven 3.9+
- Docker Desktop em execução

Confirma as ferramentas no PowerShell:

```powershell
java -version
mvn --version
docker info
```

Se `docker` não for reconhecido depois da instalação, fecha e volta a abrir o terminal/VS Code para atualizar o `PATH`. Confirma que o Docker Desktop está iniciado e que `docker info` consegue comunicar com o daemon.

### 2. Iniciar a base de dados

Na pasta principal do repositório:

```powershell
docker compose up -d postgres
docker compose ps
```

Espera até o serviço `postgres` aparecer como saudável antes de iniciar a API.

### 3. Criar a chave JWT e iniciar a API

Na mesma janela do PowerShell, cria uma chave de desenvolvimento aleatória e inicia o backend:

```powershell
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$bytes = New-Object byte[] 32
$rng.GetBytes($bytes)
$env:JWT_SECRET_BASE64 = [Convert]::ToBase64String($bytes)
$env:DEMO_MODE = "true"
$rng.Dispose()

Set-Location .\backend
mvn spring-boot:run
```

Mantém esta janela aberta. A aplicação fica disponível em `http://localhost:8080`. A chave só existe nesta janela do PowerShell; gera outra se abrires uma janela nova. `DEMO_MODE=true` cria os dados de exemplo se ainda não existirem. Deixa-o desligado fora do desenvolvimento: a conta de demonstração é pública e a palavra-passe estática não é segura para produção.

### 4. Abrir o dashboard

Abre `http://localhost:8080` no browser. Inicia sessão com:

- **Email:** `demo@example.com`
- **Palavra-passe:** `Demo123!Finance`

O painel mostra património, rendimentos e despesas do mês, fluxo de caixa dos últimos seis meses, despesas por categoria, transações recentes e objetivos de poupança. Os valores fictícios são persistidos em PostgreSQL e a API só devolve os dados do utilizador autenticado.

Usa **+ Receita** ou **+ Despesa** para registar um movimento. Preenche o valor, a descrição (por exemplo, salário, MB WAY ou presente de aniversário), a categoria, a conta e a data. A categoria pode ser escolhida das sugestões ou escrita livremente. O saldo e os indicadores do painel são atualizados depois de guardar. Numa conta sem contas financeiras, o primeiro movimento cria automaticamente uma **Conta principal** na moeda base do utilizador.

O seletor no topo do dashboard permite ver o último mês, os últimos 3 ou 6 meses e o último ano. Em **Datas personalizadas**, escolhe as datas inicial e final e carrega em **Aplicar**; os totais, o gráfico, as categorias e as transações passam a corresponder ao intervalo escolhido. Os intervalos têm de ser válidos e não podem exceder cinco anos.

Em **Objetivos**, carrega em **Criar objetivo** para definir o nome, o valor pretendido e, opcionalmente, uma data. **Adicionar dinheiro** aumenta o progresso e regista uma despesa na conta; **Retirar dinheiro** reduz o progresso e regista uma receita. Não é possível retirar mais do que o valor já guardado. **Eliminar** remove o objetivo, mas mantém no histórico da conta os movimentos que já foram registados.

Para confirmar o estado técnico do serviço, abre `http://localhost:8080/api/v1/health` no browser ou executa `Invoke-RestMethod http://localhost:8080/actuator/health` numa segunda janela do PowerShell.

### 5. Experimentar registo e login

Na segunda janela do PowerShell, cria uma conta de teste:

```powershell
$body = @{
    email = "teste@example.com"
    password = "Uma-password-segura-123"
    displayName = "Conta de teste"
} | ConvertTo-Json

$registo = Invoke-RestMethod `
    -Method Post `
    -Uri http://localhost:8080/api/v1/auth/register `
    -ContentType "application/json" `
    -Body $body

$registo
```

O resultado inclui `accessToken`, `tokenType` (`Bearer`) e `expiresIn` (segundos). Também podes iniciar sessão na interface web com esta conta. Contas normais começam sem dados financeiros; a conta `demo@example.com` é preenchida apenas quando `DEMO_MODE=true`.

```powershell
$loginBody = @{
    email = "teste@example.com"
    password = "Uma-password-segura-123"
} | ConvertTo-Json

$login = Invoke-RestMethod `
    -Method Post `
    -Uri http://localhost:8080/api/v1/auth/login `
    -ContentType "application/json" `
    -Body $loginBody

$login
```

As rotas da API que exigem autenticação aceitam o token no cabeçalho `Authorization: Bearer <token>`. Por exemplo, o endpoint Actuator `info`:

```powershell
Invoke-RestMethod `
    -Uri http://localhost:8080/actuator/info `
    -Headers @{ Authorization = "Bearer $($login.accessToken)" }
```

### 6. Executar os testes

Os testes usam uma base H2 descartável e não precisam do Docker nem da base PostgreSQL:

```powershell
Set-Location .\backend
mvn test
```

Para parar a API, usa `Ctrl+C` na janela onde está a correr. Para parar o PostgreSQL sem apagar os dados:

```powershell
docker compose down
```

`docker compose down -v` também apaga o volume e **elimina todos os dados locais** da base de dados.
