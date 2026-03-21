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
    status,
    created_at,
    updated_at
)
VALUES
(1, 'CHK-001', 'Bancolombia', 1000, 1100, 101, 0, 101, CURRENT_DATE, 'ACTIVA', NOW(), NOW());