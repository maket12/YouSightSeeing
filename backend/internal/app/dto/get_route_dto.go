package dto

import (
	"time"

	"github.com/google/uuid"
)

type GetRouteRequest struct {
	RouteID uuid.UUID `json:"route_id"`
	UserID  uuid.UUID `json:"-"`
}

type RoutePointResponse struct {
	Position   int      `json:"position"`
	PlaceID    *string  `json:"place_id"`
	Name       string   `json:"name"`
	Address    string   `json:"address"`
	Categories []string `json:"categories"`
	Latitude   float64  `json:"latitude"`
	Longitude  float64  `json:"longitude"`
}

type RouteResponse struct {
	ID             uuid.UUID            `json:"id"`
	UserID         uuid.UUID            `json:"user_id"`
	Title          string               `json:"title"`
	StartLatitude  float64              `json:"start_latitude"`
	StartLongitude float64              `json:"start_longitude"`
	Distance       int64                `json:"distance"`
	Duration       int                  `json:"duration"` // in seconds
	Categories     []string             `json:"categories"`
	MaxPlaces      int                  `json:"max_places"`
	IncludeFood    bool                 `json:"include_food"`
	IsPublic       bool                 `json:"is_public"`
	ShareCode      *string              `json:"share_code"`
	Points         []RoutePointResponse `json:"points"`
	CreatedAt      time.Time            `json:"created_at"`
}

type GetRouteResponse struct {
	Route RouteResponse `json:"route"`
}
