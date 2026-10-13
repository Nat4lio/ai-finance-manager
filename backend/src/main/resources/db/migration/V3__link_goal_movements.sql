ALTER TABLE financial_transaction
    ADD COLUMN savings_goal_id UUID REFERENCES savings_goal(id) ON DELETE SET NULL;

CREATE INDEX idx_transaction_savings_goal_id ON financial_transaction(savings_goal_id);
