package dto

import "github.com/google/uuid"

type LogoutResponse struct {
	UserID uuid.UUID `json:"user_id"`
	Logout bool      `json:"logout"`
}
