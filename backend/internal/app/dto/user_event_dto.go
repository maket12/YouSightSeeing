package dto

import "github.com/google/uuid"

type TrackUserEventRequest struct {
	UserID    uuid.UUID  `json:"-"`
	EventType string     `json:"event_type"`
	RouteID   *uuid.UUID `json:"route_id,omitempty"`
	PlaceID   *string    `json:"place_id,omitempty"`
	Category  *string    `json:"category,omitempty"`
}

type TrackUserEventResponse struct {
	Created bool `json:"created"`
}
