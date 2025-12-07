package uc_errors

import "errors"

var (
	EmptyGoogleTokenError      = errors.New("empty google token")
	GoogleTokenValidationError = errors.New("failed to validate google token")
	EmptyEmailError            = errors.New("empty email")
	EmptyGoogleSubError        = errors.New("empty google sub")
	EmailNotVerifiedError      = errors.New("email not verified")
	CreateUserError            = errors.New("failed to create user")

	CreateRefreshTokenError      = errors.New("failed to create refresh token")
	GetRefreshTokenByUserIDError = errors.New("failed to get refresh token by user id")
	RevokeRefreshTokenError      = errors.New("failed to revoke refresh token")

	GenerateAccessTokenError  = errors.New("failed to generate new access token")
	GenerateRefreshTokenError = errors.New("failed to generate new refresh token")
)
