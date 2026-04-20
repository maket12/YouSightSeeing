package dto

import "github.com/google/uuid"

type GenerateRouteRequest struct {
	UserID          uuid.UUID `json:"-"`
	StartLat        float64   `json:"start_lat"`
	StartLon        float64   `json:"start_lon"`
	Categories      []string  `json:"categories,omitempty"`
	Radius          int       `json:"radius,omitempty"`
	MaxPlaces       int       `json:"max_places,omitempty"`
	DurationMinutes int       `json:"duration_minutes,omitempty"`
	IncludeFood     bool      `json:"include_food,omitempty"`
}
