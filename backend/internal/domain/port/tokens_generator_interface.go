package port

import (
	"YouSightSeeing/backend/internal/domain/entity"
	"context"

	"github.com/google/uuid"
)

type TokensGenerator interface {
	GenerateAccessToken(ctx context.Context, userID uuid.UUID) (string, error)
	GenerateRefreshToken(ctx context.Context, userID uuid.UUID) (string, error)
	ParseAccessToken(ctx context.Context, token string) (*entity.AccessClaims, error)
	ParseRefreshToken(ctx context.Context, token string) (*entity.RefreshClaims, error)
}
