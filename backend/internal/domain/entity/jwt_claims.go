package entity

import (
	"github.com/google/uuid"
)

type AccessClaims struct {
	Type   string    `json:"type"`
	UserID uuid.UUID `json:"user_id"`
}

type RefreshClaims struct {
	Type string `json:"type"`
}
