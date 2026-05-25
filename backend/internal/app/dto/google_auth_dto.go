package dto

type GoogleAuthRequest struct {
	GoogleToken string `json:"google_token" validate:"required"`
}

type GoogleAuthResponse struct {
	AccessToken  string       `json:"access_token"`
	RefreshToken string       `json:"refresh_token,omitempty"`
	User         UserResponse `json:"user"`
}
