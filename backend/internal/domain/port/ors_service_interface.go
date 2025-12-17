package port

import (
	"YouSightSeeing/backend/internal/domain/entity"
	"context"
)

type RouteCalculator interface {
	CalculateRoute(ctx context.Context, req entity.ORSRequest) (*entity.Route, error)
}
