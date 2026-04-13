CREATE TABLE IF NOT EXISTS user_events (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    
    event_type TEXT NOT NULL,
    route_id UUID NULL,
    place_id TEXT NULL,
    category TEXT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT user_events_event_type_check
    CHECK (
              event_type IN (
              'route_generated',
              'route_saved',
              'route_opened',
              'route_completed',
              'place_viewed'
                            )
    )
    );

CREATE INDEX idx_user_events_user_id
    ON user_events(user_id);

CREATE INDEX idx_user_events_event_type
    ON user_events(event_type);

CREATE INDEX idx_user_events_place_id
    ON user_events(place_id);

CREATE INDEX idx_user_events_category
    ON user_events(category);

CREATE INDEX idx_user_events_created_at
    ON user_events(created_at);