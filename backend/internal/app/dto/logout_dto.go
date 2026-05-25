package dto

import "github.com/google/uuid"

type LogoutRequest struct {
	RefreshToken string `json:"refresh_token"`
}

type LogoutResponse struct {
	UserID uuid.UUID `json:"user_id"`
	Logout bool      `json:"logout"`
}
