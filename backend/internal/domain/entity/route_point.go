package entity

import (
	"github.com/google/uuid"
	"github.com/lib/pq"
)

type RoutePoint struct {
	ID         uuid.UUID      `db:"id"`
	RouteID    uuid.UUID      `db:"route_id"`
	Position   int            `db:"position"`
	PlaceID    *string        `db:"place_id"`
	Name       string         `db:"name"`
	Address    string         `db:"address"`
	Categories pq.StringArray `db:"categories"`
	Latitude   float64        `db:"latitude"`
	Longitude  float64        `db:"longitude"`
}
