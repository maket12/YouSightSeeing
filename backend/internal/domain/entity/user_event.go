package entity

import (
	"time"

	"github.com/google/uuid"
)

const (
	UserEventRouteGenerated = "route_generated"
	UserEventRouteSaved     = "route_saved"
	UserEventRouteOpened    = "route_opened"
	UserEventRouteCompleted = "route_completed"
	UserEventPlaceViewed    = "place_viewed"
)

type UserEvent struct {
	ID        uuid.UUID  `db:"id"`
	UserID    uuid.UUID  `db:"user_id"`
	EventType string     `db:"event_type"`
	RouteID   *uuid.UUID `db:"route_id"`
	PlaceID   *string    `db:"place_id"`
	Category  *string    `db:"category"`
	CreatedAt time.Time  `db:"created_at"`
}
