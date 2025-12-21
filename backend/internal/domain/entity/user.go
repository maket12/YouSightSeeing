package entity

import (
	"time"

	"github.com/google/uuid"
)

type User struct {
	ID            uuid.UUID  `db:"id"`
	GoogleSub     string     `db:"google_sub"`
	Email         string     `db:"email"`
	FullName      *string    `db:"full_name"`
	Picture       *string    `db:"picture"`
	FirstName     *string    `db:"first_name"`
	LastName      *string    `db:"last_name"`
	EmailVerified bool       `db:"email_verified"`
	GoogleDomain  *string    `db:"google_domain"`
	Locale        *string    `db:"locale"`
	CreatedAt     time.Time  `db:"created_at"`
	UpdatedAt     *time.Time `db:"updated_at"`
}
