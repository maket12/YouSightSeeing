package usecase

import (
	"YouSightSeeing/backend/internal/app/dto"
	"YouSightSeeing/backend/internal/app/mappers"
	"YouSightSeeing/backend/internal/app/uc_errors"
	"YouSightSeeing/backend/internal/domain/entity"
	"YouSightSeeing/backend/internal/domain/port"
	"context"
	"database/sql"
	"errors"
	"time"

	"github.com/google/uuid"
)

type RefreshTokenUC struct {
	Users           port.UserRepository
	RefreshTokens   port.TokenRepository
	TokensGenerator port.TokensGenerator
	AccessTokenTTL  time.Duration
	RefreshTokenTTL time.Duration
}

func NewRefreshTokenUC(
	users port.UserRepository,
	refreshTokens port.TokenRepository,
	tokensGenerator port.TokensGenerator,
	accessTokenTTL time.Duration,
	refreshTokenTTL time.Duration) *RefreshTokenUC {
	return &RefreshTokenUC{
		Users:           users,
		RefreshTokens:   refreshTokens,
		TokensGenerator: tokensGenerator,
		AccessTokenTTL:  accessTokenTTL,
		RefreshTokenTTL: refreshTokenTTL,
	}
}

func (uc *RefreshTokenUC) Execute(ctx context.Context, in dto.RefreshTokenRequest) (dto.RefreshTokenResponse, error) {
	// Validation
	if in.RefreshToken == "" {
		return dto.RefreshTokenResponse{}, uc_errors.EmptyRefreshTokenError
	}

	// Info about given token
	oldRefreshToken, err := uc.getOldRefreshToken(ctx, in.RefreshToken)
	if err != nil {
		return dto.RefreshTokenResponse{}, err
	}

	// User(output)
	user, err := uc.Users.GetByID(ctx, oldRefreshToken.UserID)
	if err != nil {
		return dto.RefreshTokenResponse{}, uc_errors.Wrap(uc_errors.GetUserError, err)
	}

	// Tokens pair
	accessToken, refreshToken, err := uc.getTokensPair(ctx, user.ID, oldRefreshToken)
	if err != nil {
		return dto.RefreshTokenResponse{}, err
	}

	return dto.RefreshTokenResponse{
		AccessToken:  accessToken,
		RefreshToken: refreshToken,
		User:         mappers.MapUserIntoUserResponse(user),
	}, nil
}

// getOldRefreshToken Gives information about given refresh token
func (uc *RefreshTokenUC) getOldRefreshToken(ctx context.Context, token string) (*entity.RefreshToken, error) {
	hashedToken := hashToken(token)

	refreshToken, err := uc.RefreshTokens.GetByHash(ctx, hashedToken)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, uc_errors.RefreshTokenNotFoundError
		}
		return nil, uc_errors.Wrap(uc_errors.GetRefreshTokenByHashError, err)
	}

	if refreshToken.IsRevoked {
		return nil, uc_errors.RevokedRefreshTokenError
	}

	if refreshToken.ExpiresAt.Before(time.Now()) {
		if err := uc.RefreshTokens.Revoke(ctx, hashedToken, "expired"); err != nil {
			return nil, uc_errors.Wrap(uc_errors.RevokeRefreshTokenError, err)
		}
		return nil, uc_errors.ExpiredRefreshTokenError
	}

	return refreshToken, nil
}

// rotateRefreshToken Rotates given refresh token
func (uc *RefreshTokenUC) rotateRefreshToken(ctx context.Context, oldToken *entity.RefreshToken) (string, error) {
	if err := uc.RefreshTokens.Revoke(ctx, oldToken.TokenHash, "rotating"); err != nil {
		return "", uc_errors.Wrap(uc_errors.RevokeRefreshTokenError, err)
	}

	newTokenString, err := uc.TokensGenerator.GenerateRefreshToken(ctx)
	if err != nil {
		return "", uc_errors.Wrap(uc_errors.GenerateRefreshTokenError, err)
	}

	newToken := &entity.RefreshToken{
		ID:        uuid.New(),
		UserID:    oldToken.UserID,
		TokenHash: hashToken(newTokenString),
		IssuedAt:  time.Now().UTC(),
		ExpiresAt: time.Now().Add(uc.RefreshTokenTTL).UTC(),
	}

	if err := uc.RefreshTokens.Create(ctx, newToken); err != nil {
		return "", uc_errors.Wrap(uc_errors.CreateRefreshTokenError, err)
	}

	return newTokenString, nil
}

// getTokensPair Gives a pair of tokens for output
func (uc *RefreshTokenUC) getTokensPair(ctx context.Context, userID uuid.UUID, oldRefreshToken *entity.RefreshToken) (string, string, error) {
	accessToken, err := uc.TokensGenerator.GenerateAccessToken(ctx, userID)
	if err != nil {
		return "", "", uc_errors.Wrap(uc_errors.GenerateAccessTokenError, err)
	}

	refreshToken, err := uc.rotateRefreshToken(ctx, oldRefreshToken)
	if err != nil {
		return "", "", err
	}

	return accessToken, refreshToken, nil
}
