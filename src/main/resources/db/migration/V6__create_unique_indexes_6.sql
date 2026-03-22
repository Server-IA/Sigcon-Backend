DROP INDEX IF EXISTS uk_checks_checkbook_number_active;

CREATE UNIQUE INDEX IF NOT EXISTS uk_checks_number_active
ON checks (number_check, checkbooks_id)
WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_checks_status_issue_date
ON checks (status_check, issue_date)
WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_checks_financial_movement
ON checks (financial_movement_id)
WHERE deleted_at IS NULL;
