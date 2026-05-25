package usecase

import (
	"YouSightSeeing/backend/internal/app/dto"
	"YouSightSeeing/backend/internal/app/uc_errors"
	"YouSightSeeing/backend/internal/domain/port"
	"context"

	"github.com/google/uuid"
)

type GetRouteListUC struct {
	route      port.RouteRepository
	routePoint port.RoutePointRepository
}

func NewGetRouteListUC(
	route port.RouteRepository,
	routePoint port.RoutePointRepository,
) *GetRouteListUC {
	return &GetRouteListUC{
		route:      route,
		routePoint: routePoint,
	}
}

func (uc *GetRouteListUC) Execute(ctx context.Context, req dto.GetRouteListRequest) (dto.GetRouteListResponse, error) {
	if req.UserID == uuid.Nil {
		return dto.GetRouteListResponse{}, uc_errors.InvalidUserID
	}

	if req.Limit <= 0 {
		req.Limit = 20
	}
	if req.Limit > 100 {
		req.Limit = 100
	}
	if req.Offset < 0 {
		req.Offset = 0
	}

	routes, err := uc.route.GetListByUserID(ctx, req.UserID, req.Limit, req.Offset)
	if err != nil {
		return dto.GetRouteListResponse{}, uc_errors.Wrap(
			uc_errors.GetRouteListError, err,
		)
	}

	response := make([]dto.RouteResponse, len(routes))
	for i, route := range routes {
		routePoints, getErr := uc.routePoint.Get(ctx, route.ID)
		if getErr != nil {
			return dto.GetRouteListResponse{}, uc_errors.Wrap(
				uc_errors.GetRoutePointsError, getErr,
			)
		}

		pointsResp := make([]dto.RoutePointResponse, len(routePoints))
		for j, routePoint := range routePoints {
			pointsResp[j] = dto.RoutePointResponse{
				Position:   routePoint.Position,
				PlaceID:    routePoint.PlaceID,
				Name:       routePoint.Name,
				Address:    routePoint.Address,
				Categories: routePoint.Categories,
				Latitude:   routePoint.Latitude,
				Longitude:  routePoint.Longitude,
			}
		}

		response[i] = dto.RouteResponse{
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
		}
	}

	return dto.GetRouteListResponse{Routes: response}, nil
}
