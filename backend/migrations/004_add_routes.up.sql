CREATE TABLE IF NOT EXISTS routes (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title VARCHAR(64) NOT NULL,

    start_lat DOUBLE PRECISION NOT NULL,
    start_lon DOUBLE PRECISION NOT NULL,

    distance INT NOT NULL DEFAULT 0,
    duration INTERVAL NOT NULL,

    categories TEXT[] NOT NULL,
    max_places INT NOT NULL,
    include_food BOOLEAN NOT NULL DEFAULT FALSE,

    is_public BOOLEAN NOT NULL DEFAULT FALSE,
    share_code VARCHAR(32) UNIQUE,

    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_routes_user_id ON routes(user_id);
CREATE INDEX idx_routes_categories ON routes USING GIN (categories);