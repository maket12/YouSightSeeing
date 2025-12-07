package entity

import "time"

type GoogleClaims struct {
	Sub           string    `json:"sub"`
	Email         string    `json:"email"`
	EmailVerified bool      `json:"email_verified"`
	Picture       *string   `json:"picture"`
	FamilyName    *string   `json:"family_name"`
	GivenName     *string   `json:"given_name"`
	HD            *string   `json:"hd"`
	Locale        *string   `json:"locale"`
	Name          *string   `json:"name"`
	ExpiresAt     time.Time `json:"exp"`
}
