ALTER TABLE user_preferences
    ADD COLUMN IF NOT EXISTS only_bus_when_raining BOOLEAN NOT NULL DEFAULT TRUE;