package dto

import "github.com/google/uuid"

type UpdateUserPictureRequest struct {
	ID      uuid.UUID `json:"id"`
	Picture string    `json:"picture"`
}

type UpdateUserPictureResponse struct {
	ID      uuid.UUID    `json:"id"`
	Updated bool         `json:"updated"`
	User    UserResponse `json:"user"`
}
