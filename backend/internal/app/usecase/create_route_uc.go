package usecase

import (
	"YouSightSeeing/backend/internal/app/dto"
	"YouSightSeeing/backend/internal/app/mappers"
	"YouSightSeeing/backend/internal/app/uc_errors"
	"YouSightSeeing/backend/internal/domain/entity"
	"YouSightSeeing/backend/internal/domain/port"
	"context"
	"strings"

	"github.com/google/uuid"
)

type CreateRouteUC struct {
	txManager    port.TransactionManager
	route        port.RouteRepository
	routePoint   port.RoutePointRepository
	eventTracker TrackUserEventUseCase
}

func NewCreateRouteUC(
	txManager port.TransactionManager,
	route port.RouteRepository,
	routePoint port.RoutePointRepository,
	eventTracker TrackUserEventUseCase,
) *CreateRouteUC {
	return &CreateRouteUC{
		txManager:    txManager,
		route:        route,
		routePoint:   routePoint,
		eventTracker: eventTracker,
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

		if err := uc.trackSavedRouteEvents(txCtx, req.UserID, route.ID, routePoints); err != nil {
			return err
		}

		return nil
	})

	if err != nil {
		return dto.CreateRouteResponse{RouteID: uuid.Nil}, err
	}

	return dto.CreateRouteResponse{RouteID: route.ID}, nil
}

func (uc *CreateRouteUC) trackSavedRouteEvents(
	ctx context.Context,
	userID uuid.UUID,
	routeID uuid.UUID,
	routePoints []*entity.RoutePoint,
) error {
	if uc.eventTracker == nil {
		return nil
	}

	for _, routePoint := range routePoints {
		placeID := normalizeSavedRoutePlaceID(routePoint.PlaceID)
		category := savedRouteEventCategory(routePoint.Categories)

		var categoryPtr *string
		if category != "" {
			categoryPtr = &category
		}

		if placeID == nil && categoryPtr == nil {
			continue
		}

		_, err := uc.eventTracker.Execute(ctx, dto.TrackUserEventRequest{
			UserID:    userID,
			EventType: entity.UserEventRouteSaved,
			RouteID:   &routeID,
			PlaceID:   placeID,
			Category:  categoryPtr,
		})
		if err != nil {
			return err
		}
	}

	return nil
}

func normalizeSavedRoutePlaceID(placeID *string) *string {
	if placeID == nil {
		return nil
	}

	trimmed := strings.TrimSpace(*placeID)
	if trimmed == "" {
		return nil
	}

	return &trimmed
}

func savedRouteEventCategory(categories []string) string {
	for _, category := range categories {
		category = strings.TrimSpace(category)
		if strings.HasPrefix(category, "catering.") {
			return "catering.cafe"
		}
	}

	preferredPrefixes := []string{
		"tourism.sights",
		"leisure.park",
		"entertainment.museum",
	}

	for _, prefix := range preferredPrefixes {
		for _, category := range categories {
			category = strings.TrimSpace(category)
			if category == prefix || strings.HasPrefix(category, prefix+".") {
				return prefix
			}
		}
	}

	for _, category := range categories {
		category = strings.TrimSpace(category)
		if category != "" {
			return category
		}
	}

	return ""
}
