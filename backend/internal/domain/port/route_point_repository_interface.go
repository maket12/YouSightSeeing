package port

import (
	"YouSightSeeing/backend/internal/domain/entity"
	"context"

	"github.com/google/uuid"
)

type RoutePointRepository interface {
	Create(ctx context.Context, routePoint *entity.RoutePoint) error
	Get(ctx context.Context, routeID uuid.UUID) ([]entity.RoutePoint, error)
}
