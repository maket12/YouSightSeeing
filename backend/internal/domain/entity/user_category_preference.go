package entity

import (
	"time"

	"github.com/google/uuid"
)

type UserCategoryPreference struct {
	ID        uuid.UUID `db:"id"`
	UserID    uuid.UUID `db:"user_id"`
	Category  string    `db:"category"`
	Weight    float64   `db:"weight"`
	UpdatedAt time.Time `db:"updated_at"`
}
