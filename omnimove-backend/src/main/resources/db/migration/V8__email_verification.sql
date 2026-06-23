-- =================================================================
-- V8: Email verification & password reset columns
-- =================================================================

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS verified                     BOOLEAN      NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS verification_token          VARCHAR(100),
    ADD COLUMN IF NOT EXISTS verification_token_expiry   TIMESTAMP,
    ADD COLUMN IF NOT EXISTS failed_login_attempts       INTEGER      NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS reset_password_token        VARCHAR(100),
    ADD COLUMN IF NOT EXISTS reset_password_token_expiry TIMESTAMP;

-- Mark users that existed before this migration as already verified
-- (admins and sim users from V2/V4 don't need to go through the email flow)
UPDATE users SET verified = TRUE WHERE verified = FALSE;

-- Index for fast token lookups
CREATE INDEX IF NOT EXISTS idx_users_verification_token   ON users(verification_token);
CREATE INDEX IF NOT EXISTS idx_users_reset_password_token ON users(reset_password_token);
