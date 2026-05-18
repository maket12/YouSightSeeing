CREATE TABLE IF NOT EXISTS route_points (
    id UUID PRIMARY KEY,
    route_id UUID NOT NULL REFERENCES routes(id) ON DELETE CASCADE,

    position INT NOT NULL,

    place_id TEXT,
    name TEXT NOT NULL,
    address TEXT NOT NULL DEFAULT '',
    categories TEXT[] NOT NULL DEFAULT '{}',

    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL
    );

CREATE INDEX IF NOT EXISTS idx_route_points_route_id ON route_points(route_id);
CREATE INDEX IF NOT EXISTS idx_route_points_place_id ON route_points(place_id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_route_points_route_position ON route_points(route_id, position);