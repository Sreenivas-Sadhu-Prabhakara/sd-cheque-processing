-- Cheque Processing service domain — Postgres schema.
-- READY TO HYDRATE, NOT YET WIRED (see platform-infra/postgres/hydrate.sh).

CREATE TABLE IF NOT EXISTS cheque (
    cheque_id               VARCHAR(40)  PRIMARY KEY,
    cheque_number           VARCHAR(20)  NOT NULL CHECK (cheque_number ~ '^\d{6,}$'),
    drawer_account_ref      VARCHAR(40)  NOT NULL,
    beneficiary_account_ref VARCHAR(40)  NOT NULL,
    amount_minor            BIGINT       NOT NULL CHECK (amount_minor > 0),
    currency                CHAR(3)      NOT NULL,
    status                  VARCHAR(10)  NOT NULL
        CHECK (status IN ('LODGED','PRESENTED','CLEARED','RETURNED','STOPPED')),
    return_reason           VARCHAR(140),
    lodged_at               TIMESTAMPTZ  NOT NULL,
    presented_at            TIMESTAMPTZ,
    settled_at              TIMESTAMPTZ,
    -- the lodgement rule, enforced at the DB layer too:
    CONSTRAINT no_self_deposit CHECK (drawer_account_ref <> beneficiary_account_ref),
    -- a RETURNED cheque must carry its reason:
    CONSTRAINT returned_has_reason CHECK (status <> 'RETURNED' OR return_reason IS NOT NULL)
);

CREATE INDEX IF NOT EXISTS idx_cheque_status      ON cheque (status);
CREATE INDEX IF NOT EXISTS idx_cheque_beneficiary ON cheque (beneficiary_account_ref);
CREATE INDEX IF NOT EXISTS idx_cheque_drawer      ON cheque (drawer_account_ref);
