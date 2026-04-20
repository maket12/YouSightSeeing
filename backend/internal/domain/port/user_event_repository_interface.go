package port

import (
	"YouSightSeeing/backend/internal/domain/entity"
	"context"

	"github.com/google/uuid"
)

type UserEventRepository interface {
	Create(ctx context.Context, event *entity.UserEvent) error
	GetByUserID(ctx context.Context, userID uuid.UUID, limit, offset int) ([]entity.UserEvent, error)
	GetRecentByUserID(ctx context.Context, userID uuid.UUID, limit int) ([]entity.UserEvent, error)
}
