package usecase

import (
	"YouSightSeeing/backend/internal/app/dto"
	"YouSightSeeing/backend/internal/app/uc_errors"
	"YouSightSeeing/backend/internal/app/utils"
	"YouSightSeeing/backend/internal/domain/entity"
	"YouSightSeeing/backend/internal/domain/port"
	"context"
)

type CalculateRouteUC struct {
	RouteService port.RouteCalculator
}

func NewCalculateRouteUC(service port.RouteCalculator) *CalculateRouteUC {
	return &CalculateRouteUC{
		RouteService: service,
	}
}

func (uc *CalculateRouteUC) Execute(ctx context.Context, req dto.CalculateRouteRequest) (dto.CalculateRouteResponse, error) {
	//validation
	if len(req.Coordinates) < 2 {
		return dto.CalculateRouteResponse{}, uc_errors.ErrInvalidRoutePoints
	}

	sortedPoints := utils.SortPointsNearestNeighbor(req.Coordinates)

	orsReq := entity.ORSRequest{
		Coordinates:  sortedPoints,
		Instructions: false,
		Geometry:     true,
	}

	routeEntity, err := uc.RouteService.CalculateRoute(ctx, orsReq)
	if err != nil {
		return dto.CalculateRouteResponse{}, uc_errors.Wrap(uc_errors.ErrRouteCalculationFailed, err)
	}

	return dto.CalculateRouteResponse{
		Points:   routeEntity.Geometry,
		Distance: routeEntity.Distance,
		Duration: routeEntity.Duration,
	}, nil
}
