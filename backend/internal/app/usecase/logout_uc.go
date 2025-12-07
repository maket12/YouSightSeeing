package usecase

import (
	"YouSightSeeing/backend/internal/app/dto"
	"YouSightSeeing/backend/internal/app/uc_errors"
	"YouSightSeeing/backend/internal/app/utils"
	"YouSightSeeing/backend/internal/domain/port"
	"context"
)

type LogoutUC struct {
	RefreshTokens port.TokenRepository
}

func NewLogoutUC(refreshTokens port.TokenRepository) *LogoutUC {
	return &LogoutUC{
		RefreshTokens: refreshTokens,
	}
}

func (uc *LogoutUC) Execute(ctx context.Context, in dto.LogoutRequest) (dto.LogoutResponse, error) {
	// Validation
	if in.RefreshToken == "" {
		return dto.LogoutResponse{}, uc_errors.EmptyRefreshTokenError
	}

	// Request
	hashedToken := utils.HashToken(in.RefreshToken)

	refreshToken, err := uc.RefreshTokens.GetByHash(ctx, hashedToken)
	if err != nil {
		return dto.LogoutResponse{}, uc_errors.Wrap(uc_errors.GetRefreshTokenByHashError, err)
	}

	if err := uc.RefreshTokens.Revoke(ctx, hashedToken, "logout"); err != nil {
		return dto.LogoutResponse{}, uc_errors.Wrap(uc_errors.RevokeRefreshTokenError, err)
	}

	return dto.LogoutResponse{
		Logout: true,
	}, nil
}
