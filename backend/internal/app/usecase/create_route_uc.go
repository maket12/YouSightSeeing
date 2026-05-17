package usecase

import (
	"YouSightSeeing/backend/internal/app/dto"
	"YouSightSeeing/backend/internal/app/mappers"
	"YouSightSeeing/backend/internal/app/uc_errors"
	"YouSightSeeing/backend/internal/domain/port"
	"context"

	"github.com/avito-tech/go-transaction-manager/trm/v2"
	"github.com/google/uuid"
)

type CreateRouteUC struct {
	trManager  trm.Manager
	route      port.RouteRepository
	routePoint port.RoutePointRepository
}

func NewCreateRouteUC(
	trManager trm.Manager,
	route port.RouteRepository,
	routePoint port.RoutePointRepository,
) *CreateRouteUC {
	return &CreateRouteUC{
		trManager:  trManager,
		route:      route,
		routePoint: routePoint,
	}
}

func (uc *CreateRouteUC) Execute(ctx context.Context, req dto.CreateRouteRequest) (dto.CreateRouteResponse, error) {
	route, routePoints := mappers.MapCreateRouteToEntities(req)

	err := uc.trManager.Do(ctx, func(txCtx context.Context) error {
		createErr := uc.route.Create(txCtx, route)
		if createErr != nil {
			return uc_errors.Wrap(uc_errors.CreateRouteError, createErr)
		}

		for _, routePoint := range routePoints {
			createErr = uc.routePoint.Create(txCtx, routePoint)
			if createErr != nil {
				return uc_errors.Wrap(uc_errors.CreateRoutePointError, createErr)
			}
		}

		return nil
	})

	if err != nil {
		return dto.CreateRouteResponse{RouteID: uuid.Nil}, err
	}

	return dto.CreateRouteResponse{RouteID: route.ID}, nil
}
