package entity

import "github.com/google/uuid"

type RoutePoint struct {
	RouteID    uuid.UUID `db:"route_id"`
	Position   int       `db:"position"`
	PlaceID    *string   `db:"place_id"`
	Name       string    `db:"name"`
	Address    string    `db:"address"`
	Categories []string  `db:"categories"`
	Latitude   float64   `db:"latitude"`
	Longitude  float64   `db:"longitude"`
}
