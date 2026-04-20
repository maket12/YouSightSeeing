package usecase

import (
	"YouSightSeeing/backend/internal/app/dto"
	"YouSightSeeing/backend/internal/app/uc_errors"
	"YouSightSeeing/backend/internal/domain/entity"
	"YouSightSeeing/backend/internal/domain/port"
	"context"
	"time"

	"github.com/google/uuid"
)

type SaveRouteUC struct {
	route port.RouteRepository
}

func NewSaveRouteUC(route port.RouteRepository) *SaveRouteUC {
	return &SaveRouteUC{route: route}
}

func (uc *SaveRouteUC) Execute(ctx context.Context, req dto.SaveRouteRequest) error {
	route := &entity.Route{
		ID:             uuid.New(),
		UserID:         req.UserID,
		Title:          req.Title,
		StartLatitude:  req.StartLatitude,
		StartLongitude: req.StartLongitude,
		Distance:       req.Distance,
		Duration:       req.Duration,
		Categories:     req.Categories,
		MaxPlaces:      req.MaxPlaces,
		IncludeFood:    req.IncludeFood,
		IsPublic:       req.IsPublic,
		ShareCode:      req.ShareCode,
		CreatedAt:      time.Now().UTC(),
		UpdatedAt:      time.Time{}.UTC(),
	}

	if err := uc.route.Create(ctx, route); err != nil {
		return uc_errors.Wrap(uc_errors.CreateRouteError, err)
	}

	return nil
}
