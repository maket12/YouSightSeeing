package usecase

import (
	"YouSightSeeing/backend/internal/app/dto"
	"YouSightSeeing/backend/internal/app/uc_errors"
	"YouSightSeeing/backend/internal/domain/port"
	"context"
)

type GetRouteUC struct {
	route      port.RouteRepository
	routePoint port.RoutePointRepository
}

func NewGetRouteUC(
	route port.RouteRepository,
	routePoint port.RoutePointRepository,
) *GetRouteUC {
	return &GetRouteUC{
		route:      route,
		routePoint: routePoint,
	}
}

func (uc *GetRouteUC) Execute(ctx context.Context, req dto.GetRouteRequest) (dto.GetRouteResponse, error) {
	route, err := uc.route.Get(ctx, req.RouteID)
	if err != nil {
		return dto.GetRouteResponse{}, uc_errors.Wrap(
			uc_errors.GetRouteError, err,
		)
	}

	routePoints, err := uc.routePoint.Get(ctx, req.RouteID)
	if err != nil {
		return dto.GetRouteResponse{}, uc_errors.Wrap(
			uc_errors.GetRoutePointsError, err,
		)
	}

	pointsResp := make([]dto.RoutePointResponse, len(routePoints))
	for i, routePoint := range routePoints {
		pointsResp[i] = dto.RoutePointResponse{
			Position:   routePoint.Position,
			PlaceID:    routePoint.PlaceID,
			Name:       routePoint.Name,
			Address:    routePoint.Address,
			Categories: routePoint.Categories,
			Latitude:   routePoint.Latitude,
			Longitude:  routePoint.Longitude,
		}
	}

	return dto.GetRouteResponse{Route: dto.RouteResponse{
		ID:             route.ID,
		UserID:         route.UserID,
		Title:          route.Title,
		StartLatitude:  route.StartLatitude,
		StartLongitude: route.StartLongitude,
		Distance:       route.Distance,
		Duration:       route.Duration,
		Categories:     route.Categories,
		MaxPlaces:      route.MaxPlaces,
		IncludeFood:    route.IncludeFood,
		IsPublic:       route.IsPublic,
		ShareCode:      route.ShareCode,
		Points:         pointsResp,
		CreatedAt:      route.CreatedAt,
	}}, nil
}
