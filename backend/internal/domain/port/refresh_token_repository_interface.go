package port

import (
	"YouSightSeeing/backend/internal/domain/entity"
	"context"
	"time"

	"github.com/google/uuid"
)

type TokenRepository interface {
	Create(ctx context.Context, token *entity.RefreshToken) error
	GetByHash(ctx context.Context, tokenHash string) (*entity.RefreshToken, error)
	GetByID(ctx context.Context, id uuid.UUID) (*entity.RefreshToken, error)
	GetByUserID(ctx context.Context, userID uuid.UUID) (*entity.RefreshToken, error)

	Revoke(ctx context.Context, tokenHash string, reason string) error
	RevokeByID(ctx context.Context, id uuid.UUID, reason string) error

	DeleteExpired(ctx context.Context) error
	DeleteRevoked(ctx context.Context, olderThan *time.Time) error

	GetList(ctx context.Context, userID uuid.UUID) ([]entity.RefreshToken, error)
	GetListActive(ctx context.Context, userID uuid.UUID, isActive bool) ([]entity.RefreshToken, error)

	IsValid(ctx context.Context, tokenHash string) (bool, error)
}
