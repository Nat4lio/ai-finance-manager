CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE app_user (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(320) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    base_currency CHAR(3) NOT NULL DEFAULT 'EUR',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE bank_connection (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    provider_code VARCHAR(80) NOT NULL,
    provider_connection_id VARCHAR(255),
    institution_name VARCHAR(160),
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    consent_expires_at TIMESTAMPTZ,
    last_synced_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_bank_connection_status CHECK (status IN ('PENDING','ACTIVE','EXPIRED','REVOKED','ERROR'))
);
CREATE INDEX idx_bank_connection_user_id ON bank_connection(user_id);

CREATE TABLE financial_account (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    bank_connection_id UUID REFERENCES bank_connection(id) ON DELETE SET NULL,
    provider_account_id VARCHAR(255),
    name VARCHAR(160) NOT NULL,
    account_type VARCHAR(32) NOT NULL,
    currency CHAR(3) NOT NULL DEFAULT 'EUR',
    current_balance NUMERIC(19,4),
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_provider_account_per_connection UNIQUE (bank_connection_id, provider_account_id)
);
CREATE INDEX idx_financial_account_user_id ON financial_account(user_id);

CREATE TABLE category (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES app_user(id) ON DELETE CASCADE,
    name VARCHAR(80) NOT NULL,
    kind VARCHAR(16) NOT NULL,
    color VARCHAR(16),
    is_system BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_category_kind CHECK (kind IN ('INCOME','EXPENSE','TRANSFER'))
);
CREATE UNIQUE INDEX uq_category_user_name_kind ON category(user_id, lower(name), kind) WHERE user_id IS NOT NULL;
CREATE UNIQUE INDEX uq_system_category_name_kind ON category(lower(name), kind) WHERE user_id IS NULL AND is_system = true;

CREATE TABLE financial_transaction (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    account_id UUID NOT NULL REFERENCES financial_account(id) ON DELETE CASCADE,
    category_id UUID REFERENCES category(id) ON DELETE SET NULL,
    provider_transaction_id VARCHAR(255),
    source VARCHAR(16) NOT NULL DEFAULT 'MANUAL',
    description VARCHAR(500) NOT NULL,
    merchant_name VARCHAR(200),
    amount NUMERIC(19,4) NOT NULL,
    currency CHAR(3) NOT NULL DEFAULT 'EUR',
    booked_at DATE NOT NULL,
    pending BOOLEAN NOT NULL DEFAULT false,
    ai_category_confidence NUMERIC(5,4),
    user_category_confirmed BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_transaction_source CHECK (source IN ('MANUAL','BANK_API','IMPORT')),
    CONSTRAINT chk_ai_category_confidence CHECK (ai_category_confidence IS NULL OR (ai_category_confidence >= 0 AND ai_category_confidence <= 1))
);
CREATE INDEX idx_transaction_user_booked_at ON financial_transaction(user_id, booked_at DESC);
CREATE INDEX idx_transaction_account_booked_at ON financial_transaction(account_id, booked_at DESC);
CREATE INDEX idx_transaction_user_category ON financial_transaction(user_id, category_id);
CREATE UNIQUE INDEX uq_provider_transaction ON financial_transaction(account_id, provider_transaction_id) WHERE provider_transaction_id IS NOT NULL;

CREATE TABLE savings_goal (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    name VARCHAR(120) NOT NULL,
    target_amount NUMERIC(19,4) NOT NULL,
    current_amount NUMERIC(19,4) NOT NULL DEFAULT 0,
    target_date DATE,
    currency CHAR(3) NOT NULL DEFAULT 'EUR',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_goal_amounts CHECK (target_amount > 0 AND current_amount >= 0)
);
CREATE INDEX idx_savings_goal_user_id ON savings_goal(user_id);

CREATE TABLE ai_insight (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    insight_type VARCHAR(40) NOT NULL,
    title VARCHAR(160) NOT NULL,
    summary TEXT NOT NULL,
    evidence JSONB NOT NULL DEFAULT '{}'::jsonb,
    model_name VARCHAR(120),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_ai_insight_user_created_at ON ai_insight(user_id, created_at DESC);
