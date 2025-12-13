package uc_errors

import "errors"

var (
	EmptyGoogleTokenError      = errors.New("empty google token")
	GoogleTokenValidationError = errors.New("failed to validate google token")
	EmptyEmailError            = errors.New("empty email")
	EmptyGoogleSubError        = errors.New("empty google sub")
	EmailNotVerifiedError      = errors.New("email not verified")
	EmptyRefreshTokenError     = errors.New("empty refresh token")
	InvalidUserID              = errors.New("invalid user id")

	CreateUserError        = errors.New("failed to create user")
	GetUserError           = errors.New("failed to get user")
	UpdateUserError        = errors.New("failed to update user")
	UpdateUserPictureError = errors.New("failed to update user's picture")

	CreateRefreshTokenError      = errors.New("failed to create refresh token")
	GetRefreshTokenByUserIDError = errors.New("failed to get refresh token by user id")
	GetRefreshTokenByHashError   = errors.New("failed to get refresh token by hash")
	RevokeRefreshTokenError      = errors.New("failed to revoke refresh token")

	RefreshTokenNotFoundError = errors.New("refresh token not found")
	ExpiredRefreshTokenError  = errors.New("expired refresh token")
	RevokedRefreshTokenError  = errors.New("refresh token has already been revoked")

	GenerateAccessTokenError  = errors.New("failed to generate new access token")
	GenerateRefreshTokenError = errors.New("failed to generate new refresh token")
)
