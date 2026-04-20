package dto

import (
	"github.com/google/uuid"
)

type SaveRouteRequest struct {
	UserID uuid.UUID
	Title  string

	StartLatitude  float64
	StartLongitude float64

	Distance int64
	Duration int // in seconds

	Categories  []string
	MaxPlaces   int
	IncludeFood bool

	IsPublic  bool
	ShareCode *string
}
