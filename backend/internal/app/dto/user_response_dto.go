package dto

import (
	"time"

	"github.com/google/uuid"
)

type UserResponse struct {
	ID            uuid.UUID  `json:"id"`
	GoogleSub     string     `json:"google_sub"`
	Email         string     `json:"email"`
	FullName      *string    `json:"full_name,omitempty"`
	Picture       *string    `json:"picture,omitempty"`
	FirstName     *string    `json:"first_name,omitempty"`
	LastName      *string    `json:"last_name,omitempty"`
	EmailVerified bool       `json:"email_verified"`
	GoogleDomain  *string    `json:"google_domain,omitempty"`
	Locale        *string    `json:"locale,omitempty"`
	CreatedAt     time.Time  `json:"created_at"`
	UpdatedAt     *time.Time `json:"updated_at"`
}
