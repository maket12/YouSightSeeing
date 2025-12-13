package dto

import "github.com/google/uuid"

type GetUserRequest struct {
	ID uuid.UUID `json:"id"`
}
