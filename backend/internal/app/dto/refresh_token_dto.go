package dto

type RefreshTokenRequest struct {
	RefreshToken string `json:"refresh_token"`
}

type RefreshTokenResponse struct {
	AccessToken  string       `json:"access_token"`
	RefreshToken string       `json:"refresh_token,omitempty"`
	User         UserResponse `json:"user"`
}
