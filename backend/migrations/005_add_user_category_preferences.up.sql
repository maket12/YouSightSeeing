CREATE TABLE IF NOT EXISTS user_category_preferences (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    category TEXT NOT NULL,
    weight DOUBLE PRECISION NOT NULL DEFAULT 0.5,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT user_category_preferences_weight_check
    CHECK (weight >= 0 AND weight <= 1),

    CONSTRAINT user_category_preferences_user_category_unique
    UNIQUE (user_id, category)
);

CREATE INDEX idx_user_category_preferences_user_id
    ON user_category_preferences(user_id);

CREATE INDEX idx_user_category_preferences_category
    ON user_category_preferences(category);