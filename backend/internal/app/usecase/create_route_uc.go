package usecase

import (
	"YouSightSeeing/backend/internal/app/dto"
	"YouSightSeeing/backend/internal/app/mappers"
	"YouSightSeeing/backend/internal/app/uc_errors"
	"YouSightSeeing/backend/internal/domain/port"
	"context"
	"strings"

	"github.com/google/uuid"
)

type CreateRouteUC struct {
	txManager  port.TransactionManager
	route      port.RouteRepository
	routePoint port.RoutePointRepository
}

func NewCreateRouteUC(
	txManager port.TransactionManager,
	route port.RouteRepository,
	routePoint port.RoutePointRepository,
) *CreateRouteUC {
	return &CreateRouteUC{
		txManager:  txManager,
		route:      route,
		routePoint: routePoint,
	}
}

func (uc *CreateRouteUC) Execute(ctx context.Context, req dto.CreateRouteRequest) (dto.CreateRouteResponse, error) {
	if req.UserID == uuid.Nil {
		return dto.CreateRouteResponse{}, uc_errors.InvalidUserID
	}

	if strings.TrimSpace(req.Title) == "" {
		req.Title = "Saved route"
	}

	if len(req.Points) == 0 {
		return dto.CreateRouteResponse{}, uc_errors.ErrInvalidRoutePoints
	}

	route, routePoints := mappers.MapCreateRouteToEntities(req)

	err := uc.txManager.WithinTransaction(ctx, func(txCtx context.Context) error {
		if err := uc.route.Create(txCtx, route); err != nil {
			return uc_errors.Wrap(uc_errors.CreateRouteError, err)
		}

		for _, routePoint := range routePoints {
			if err := uc.routePoint.Create(txCtx, routePoint); err != nil {
				return uc_errors.Wrap(uc_errors.CreateRoutePointError, err)
			}
		}

		return nil
	})

	if err != nil {
		return dto.CreateRouteResponse{RouteID: uuid.Nil}, err
	}

	return dto.CreateRouteResponse{RouteID: route.ID}, nil
}
