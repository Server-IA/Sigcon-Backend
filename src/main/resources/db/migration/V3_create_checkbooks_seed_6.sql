INSERT INTO checkbooks (
    bank_account_id,
    checkbook_number,
    issuing_bank,
    check_start_number,
    check_end_number,
    total_checks,
    used_checks,
    available_checks,
    received_date,
    activation_date,
    status,
    created_at,
    updated_at
)
SELECT 
    ba.id,
    'CHK-001',
    'Bancolombia',
    1000,
    1100,
    101,
    0,
    101,
    CURRENT_DATE,
    CURRENT_DATE,
    'ACTIVA',
    NOW(),
    NOW()
FROM bank_accounts ba
WHERE ba.deleted_at IS NULL
  AND NOT EXISTS (
      SELECT 1 
      FROM checkbooks cb
      WHERE cb.checkbook_number = 'CHK-001'
        AND cb.bank_account_id = ba.id
        AND cb.deleted_at IS NULL
  )
LIMIT 1;