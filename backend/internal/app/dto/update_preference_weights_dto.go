package dto

import "github.com/google/uuid"

type UpdatePreferenceWeightsRequest struct {
	UserID    uuid.UUID `json:"-"`
	EventType string    `json:"event_type"`
	Category  string    `json:"category"`
}

type UpdatePreferenceWeightsResponse struct {
	Updated  bool    `json:"updated"`
	Category string  `json:"category"`
	Weight   float64 `json:"weight"`
}
