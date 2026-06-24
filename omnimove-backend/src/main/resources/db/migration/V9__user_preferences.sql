CREATE TABLE IF NOT EXISTS user_preferences (
                                                user_id                 BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    default_journey_mode    VARCHAR(20)  NOT NULL DEFAULT 'FAST',
    avoid_high_occupancy    BOOLEAN      NOT NULL DEFAULT false,
    show_walking            BOOLEAN      NOT NULL DEFAULT true,
    prefer_bike_over_bus    BOOLEAN      NOT NULL DEFAULT false,
    notify_delays           BOOLEAN      NOT NULL DEFAULT true,
    notify_ticket_expiry    BOOLEAN      NOT NULL DEFAULT true,
    notify_eco_tip          BOOLEAN      NOT NULL DEFAULT false
    );