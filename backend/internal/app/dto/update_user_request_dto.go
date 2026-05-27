package dto

import "github.com/google/uuid"

type UpdateUserRequest struct {
	ID        uuid.UUID `json:"id"`
	Email     *string   `json:"email"`
	FullName  *string   `json:"full_name"`
	Picture   *string   `json:"picture"`
	FirstName *string   `json:"first_name"`
	LastName  *string   `json:"last_name"`
}
