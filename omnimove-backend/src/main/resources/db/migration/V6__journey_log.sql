CREATE TABLE IF NOT EXISTS journey_log (
                                           id           BIGSERIAL PRIMARY KEY,
                                           user_id      BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    mode         VARCHAR(20)    NOT NULL,
    distance_km  DOUBLE PRECISION NOT NULL,
    cost_euros   DOUBLE PRECISION NOT NULL DEFAULT 0,
    co2_grams    DOUBLE PRECISION NOT NULL DEFAULT 0,
    green_index  INTEGER NOT NULL,
    origin_name  VARCHAR(150),
    dest_name    VARCHAR(150),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
    );

CREATE INDEX IF NOT EXISTS idx_journey_log_user ON journey_log(user_id);
CREATE INDEX IF NOT EXISTS idx_journey_log_created ON journey_log(created_at);