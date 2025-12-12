package dto

import "github.com/google/uuid"

type GetUserByID struct {
	ID uuid.UUID `json:"id"`
}
