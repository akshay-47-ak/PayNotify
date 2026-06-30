ALTER TABLE payment_request
    MODIFY COLUMN status VARCHAR(50) NOT NULL,
    ADD COLUMN cashier_id BIGINT NULL,
    ADD COLUMN cashier_session_id VARCHAR(100) NULL,
    ADD COLUMN branch_id BIGINT NULL,
    ADD COLUMN expires_at DATETIME NULL,
    ADD COLUMN paid_at DATETIME NULL,
    ADD COLUMN confirmed_at DATETIME NULL,
    ADD COLUMN confirmed_by BIGINT NULL,
    ADD COLUMN matched_notification_id BIGINT NULL;

CREATE INDEX idx_payment_attempt_terminal_status ON payment_request (terminal_id, status);
CREATE INDEX idx_payment_attempt_txn_id ON payment_request (transaction_ref);
CREATE INDEX idx_payment_attempt_cashier_session ON payment_request (cashier_session_id);
CREATE INDEX idx_payment_attempt_created_at ON payment_request (created_at);

ALTER TABLE payment_notification_log
    ADD COLUMN terminal_id VARCHAR(50) NULL,
    ADD COLUMN app_name VARCHAR(100) NULL,
    ADD COLUMN raw_title VARCHAR(500) NULL,
    ADD COLUMN raw_message TEXT NULL,
    ADD COLUMN extracted_txn_id VARCHAR(100) NULL,
    ADD COLUMN amount DECIMAL(18,2) NULL,
    ADD COLUMN notification_received_at DATETIME NULL,
    ADD COLUMN matched_payment_attempt_id BIGINT NULL,
    ADD COLUMN status VARCHAR(50) NULL,
    ADD COLUMN dedupe_hash VARCHAR(64) NULL;

CREATE INDEX idx_notification_terminal_app_time ON payment_notification_log (terminal_id, app_name, notification_received_at);
CREATE INDEX idx_notification_dedupe_hash ON payment_notification_log (dedupe_hash);
CREATE INDEX idx_notification_matched_attempt ON payment_notification_log (matched_payment_attempt_id);
CREATE UNIQUE INDEX unique_dedupe_hash ON payment_notification_log (dedupe_hash);
