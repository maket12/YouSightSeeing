package dto

import (
	"github.com/google/uuid"
)

type RoutePointRequest struct {
	Position   int      `json:"position"`
	PlaceID    *string  `json:"place_id"`
	Name       string   `json:"name"`
	Address    string   `json:"address"`
	Categories []string `json:"categories"`
	Latitude   float64  `json:"latitude"`
	Longitude  float64  `json:"longitude"`
}

type CreateRouteRequest struct {
	UserID         uuid.UUID
	Title          string              `json:"title"`
	StartLatitude  float64             `json:"start_latitude"`
	StartLongitude float64             `json:"start_longitude"`
	Distance       int64               `json:"distance"`
	Duration       int                 `json:"duration"` // in seconds
	Categories     []string            `json:"categories"`
	MaxPlaces      int                 `json:"max_places"`
	IncludeFood    bool                `json:"include_food"`
	IsPublic       bool                `json:"is_public"`
	ShareCode      *string             `json:"share_code"`
	Points         []RoutePointRequest `json:"points"`
}

type CreateRouteResponse struct {
	RouteID uuid.UUID `json:"route_id"`
}
