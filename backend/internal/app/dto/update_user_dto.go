package dto

type UpdateUser struct {
	Email     string  `json:"email"`
	FullName  *string `json:"full_name"`
	Picture   *string `json:"picture"`
	FirstName *string `json:"first_name"`
	LastName  *string `json:"last_name"`
}
