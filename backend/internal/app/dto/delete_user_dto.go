package dto

import "github.com/google/uuid"

type DeleteUser struct {
	ID uuid.UUID `json:"id"`
}

type DeleteUserResponse struct {
	Deleted bool `json:"deleted"`
}
