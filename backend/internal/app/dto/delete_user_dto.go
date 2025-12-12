package dto

import "github.com/google/uuid"

type DeleteUser struct {
	ID uuid.UUID `json:"id"`
}
