package dto

type GoogleAuthRequest struct {
	GoogleToken string `json:"google_token" validate:"required"`
}
