package usecase

import (
	"YouSightSeeing/backend/internal/app/dto"
	"context"
)

type GoogleAuthUseCase interface {
	Execute(ctx context.Context, in dto.GoogleAuthRequest) (dto.GoogleAuthResponse, error)
}

type RefreshTokenUseCase interface {
	Execute(ctx context.Context, in dto.RefreshTokenRequest) (dto.RefreshTokenResponse, error)
}

type LogoutUseCase interface {
	Execute(ctx context.Context, in dto.LogoutRequest) (dto.LogoutResponse, error)
}

type GetUserUseCase interface {
	Execute(ctx context.Context, in dto.GetUserRequest) (dto.GetUserResponse, error)
}

type UpdateUserUseCase interface {
	Execute(ctx context.Context, in dto.UpdateUserRequest) (dto.UpdateUserResponse, error)
}

type UpdateUserPictureUseCase interface {
	Execute(ctx context.Context, in dto.UpdateUserPictureRequest) (dto.UpdateUserPictureResponse, error)
}

type CalculateRouteUseCase interface {
	Execute(ctx context.Context, req dto.CalculateRouteRequest) (dto.CalculateRouteResponse, error)
}
