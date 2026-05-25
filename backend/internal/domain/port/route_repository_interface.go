package port

import (
	"YouSightSeeing/backend/internal/domain/entity"
	"context"

	"github.com/google/uuid"
)

type RouteRepository interface {
	Create(ctx context.Context, route *entity.Route) error
	Get(ctx context.Context, id uuid.UUID) (*entity.Route, error)
	Update(ctx context.Context, route *entity.Route) error
	Delete(ctx context.Context, id uuid.UUID) error
	GetListByUserID(ctx context.Context, userID uuid.UUID, limit, offset int) ([]entity.Route, error)
	GetByShareCode(ctx context.Context, code string) (*entity.Route, error)
}
