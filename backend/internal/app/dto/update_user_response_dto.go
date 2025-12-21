package dto

import "github.com/google/uuid"

type UpdateUserResponse struct {
	ID      uuid.UUID    `json:"id"`
	Updated bool         `json:"updated"`
	User    UserResponse `json:"user"`
}
