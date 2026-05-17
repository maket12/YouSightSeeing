CREATE TABLE IF NOT EXISTS routes (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title VARCHAR(64) NOT NULL,

    start_latitude DOUBLE PRECISION NOT NULL,
    start_longitude DOUBLE PRECISION NOT NULL,

    distance INT NOT NULL DEFAULT 0,
    duration INT NOT NULL DEFAULT 0,  -- in seconds

    categories TEXT[] NOT NULL,
    max_places INT NOT NULL,
    include_food BOOLEAN NOT NULL DEFAULT FALSE,

    is_public BOOLEAN NOT NULL DEFAULT FALSE,
    share_code VARCHAR(32) UNIQUE,

    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS route_points (
    id UUID PRIMARY KEY,
    route_id UUID NOT NULL REFERENCES routes(id) ON DELETE CASCADE,
    position INT NOT NULL,
    place_id TEXT,
    name TEXT NOT NULL,
    address TEXT,
    categories TEXT[] NOT NULL,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL
);

CREATE INDEX idx_routes_user_id ON routes(user_id);
CREATE INDEX idx_routes_categories ON routes USING GIN (categories);