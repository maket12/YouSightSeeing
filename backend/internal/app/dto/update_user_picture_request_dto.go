package dto

import "github.com/google/uuid"

type UpdateUserPictureRequest struct {
	ID      uuid.UUID `json:"id"`
	Picture string    `json:"picture"`
}
