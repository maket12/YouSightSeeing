package port

import (
	"YouSightSeeing/backend/internal/domain/entity"
	"context"
)

type RouteCalculator interface {
	CalculateRoute(ctx context.Context, req entity.ORSRequest) (*entity.ORSRoute, error)
}

type RouteMatrixCalculator interface {
	CalculateMatrix(ctx context.Context, req entity.ORSMatrixRequest) (*entity.RouteMatrix, error)
}
