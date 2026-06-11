-- Sample data for local exploration after hydration. Idempotent.
INSERT INTO cheque (cheque_id, cheque_number, drawer_account_ref, beneficiary_account_ref,
                    amount_minor, currency, status, return_reason, lodged_at, presented_at, settled_at)
VALUES
    ('CHQ-SEED-0001', '100001', 'CA-SEED-0002', 'CA-SEED-0001',  50000, 'INR', 'CLEARED',  NULL,
        now() - interval '3 days', now() - interval '2 days', now() - interval '1 day'),
    ('CHQ-SEED-0002', '100002', 'CA-SEED-0001', 'SA-SEED-0001', 120000, 'INR', 'PRESENTED', NULL,
        now() - interval '1 day',  now() - interval '4 hours', NULL),
    ('CHQ-SEED-0003', '100003', 'CA-SEED-0003', 'SA-SEED-0002',  30000, 'INR', 'RETURNED', 'insufficient-funds',
        now() - interval '2 days', now() - interval '1 day',   now() - interval '6 hours')
ON CONFLICT (cheque_id) DO NOTHING;
