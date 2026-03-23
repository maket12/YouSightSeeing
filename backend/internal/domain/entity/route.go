package entity

import (
	"time"

	"github.com/google/uuid"
	"github.com/lib/pq"
)

type Route struct {
	ID     uuid.UUID `db:"id"`
	UserID uuid.UUID `db:"user_id"`
	Title  string    `db:"title"`

	StartLatitude  float64 `db:"start_latitude"`
	StartLongitude float64 `db:"start_longitude"`

	Distance int64         `db:"distance"`
	Duration time.Duration `db:"duration"`

	Categories  pq.StringArray `db:"categories"`
	MaxPlaces   int            `db:"max_places"`
	IncludeFood bool           `db:"include_food"`

	IsPublic  bool    `db:"is_public"`
	ShareCode *string `db:"share_code"`

	CreatedAt time.Time `db:"created_at"`
	UpdatedAt time.Time `db:"updated_at"`
}
