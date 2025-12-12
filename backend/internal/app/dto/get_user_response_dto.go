package dto

import (
	"time"

	"github.com/google/uuid"
)

type GetUserResponse struct {
	ID            uuid.UUID  `json:"id"`
	GoogleSub     string     `json:"google_sub"`
	Email         string     `json:"email"`
	FullName      *string    `json:"full_name"`
	Picture       *string    `json:"picture"`
	FirstName     *string    `json:"first_name"`
	LastName      *string    `json:"last_name"`
	EmailVerified bool       `json:"email_verified"`
	GoogleDomain  *string    `json:"google_domain"`
	Locale        *string    `json:"locale"`
	CreatedAt     time.Time  `json:"created_at"`
	UpdatedAt     *time.Time `json:"updated_at"`
}
