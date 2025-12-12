package port

import (
	"YouSightSeeing/backend/internal/domain/entity"
	"context"
)

type GoogleVerifier interface {
	VerifyToken(ctx context.Context, token string) (*entity.GoogleClaims, error)
}
