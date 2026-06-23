CREATE TABLE IF NOT EXISTS favorite_route (
                                              id           BIGSERIAL PRIMARY KEY,
                                              user_id      BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    mode         VARCHAR(20)  NOT NULL,
    origin_name  VARCHAR(150) NOT NULL,
    dest_name    VARCHAR(150) NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_favorite_route UNIQUE (user_id, mode, origin_name, dest_name)
    );

CREATE INDEX IF NOT EXISTS idx_favorite_route_user ON favorite_route(user_id);