-- Additive migration; preserves all existing accounts and password hashes.
USE crave;

SET @email_password_version_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.columns
           WHERE table_schema = DATABASE() AND table_name = 'user_account' AND column_name = 'password_version'),
    'SELECT 1',
    'ALTER TABLE user_account ADD COLUMN password_version BIGINT NOT NULL DEFAULT 0'
);
PREPARE email_password_version_statement FROM @email_password_version_sql;
EXECUTE email_password_version_statement;
DEALLOCATE PREPARE email_password_version_statement;

CREATE TABLE IF NOT EXISTS email_verification_challenges (
    challenge_id VARCHAR(64) NOT NULL,
    email VARCHAR(100) NOT NULL,
    purpose VARCHAR(24) NOT NULL,
    user_id VARCHAR(10) NULL,
    account_binding VARCHAR(64) NULL,
    code_salt VARCHAR(32) NOT NULL,
    code_digest VARCHAR(64) NOT NULL,
    issued_at_ms BIGINT NOT NULL,
    expires_at_ms BIGINT NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    consumed BOOLEAN NOT NULL DEFAULT FALSE,
    window_started_at_ms BIGINT NOT NULL,
    window_send_count INT NOT NULL,
    send_history VARCHAR(100) NOT NULL DEFAULT '',
    CONSTRAINT pk_email_verification_challenges PRIMARY KEY (challenge_id),
    CONSTRAINT uq_email_verification_scope UNIQUE (email, purpose),
    CONSTRAINT ck_email_verification_purpose CHECK (purpose IN ('REGISTER', 'PASSWORD_RESET', 'PASSWORD_CHANGE')),
    CONSTRAINT ck_email_verification_attempts CHECK (attempts BETWEEN 0 AND 5),
    CONSTRAINT ck_email_verification_send_count CHECK (window_send_count BETWEEN 1 AND 5)
) ENGINE=InnoDB;

SET @email_send_history_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.columns
           WHERE table_schema = DATABASE() AND table_name = 'email_verification_challenges' AND column_name = 'send_history'),
    'SELECT 1',
    'ALTER TABLE email_verification_challenges ADD COLUMN send_history VARCHAR(100) NOT NULL DEFAULT '''''
);
PREPARE email_send_history_statement FROM @email_send_history_sql;
EXECUTE email_send_history_statement;
DEALLOCATE PREPARE email_send_history_statement;
