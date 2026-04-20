package dto

import "github.com/google/uuid"

type CategoryPreference struct {
	Category string  `json:"category"`
	Weight   float64 `json:"weight"`
}

type GetUserPreferencesRequest struct {
	UserID uuid.UUID `json:"-"`
}

type GetUserPreferencesResponse struct {
	Preferences []CategoryPreference `json:"preferences"`
}

type UpdateUserPreferencesRequest struct {
	UserID      uuid.UUID            `json:"-"`
	Preferences []CategoryPreference `json:"preferences"`
}

type UpdateUserPreferencesResponse struct {
	Updated     bool                 `json:"updated"`
	Preferences []CategoryPreference `json:"preferences"`
}
