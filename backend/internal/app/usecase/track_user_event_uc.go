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

type TrackUserEventUC struct {
	repo port.UserEventRepository
}

func NewTrackUserEventUC(repo port.UserEventRepository) *TrackUserEventUC {
	return &TrackUserEventUC{
		repo: repo,
	}
}

func (uc *TrackUserEventUC) Execute(
	ctx context.Context,
	req dto.TrackUserEventRequest,
) (dto.TrackUserEventResponse, error) {
	if req.UserID.String() == "" {
		return dto.TrackUserEventResponse{}, uc_errors.InvalidUserID
	}

	if !isValidUserEventType(req.EventType) {
		return dto.TrackUserEventResponse{}, uc_errors.ErrInvalidUserEventType
	}

	err := uc.repo.Create(ctx, &entity.UserEvent{
		ID:        uuid.New(),
		UserID:    req.UserID,
		EventType: req.EventType,
		RouteID:   req.RouteID,
		PlaceID:   req.PlaceID,
		Category:  req.Category,
		CreatedAt: time.Now().UTC(),
	})
	if err != nil {
		return dto.TrackUserEventResponse{}, err
	}

	return dto.TrackUserEventResponse{
		Created: true,
	}, nil
}

func isValidUserEventType(eventType string) bool {
	switch eventType {
	case entity.UserEventRouteGenerated,
		entity.UserEventRouteSaved,
		entity.UserEventRouteOpened,
		entity.UserEventRouteCompleted,
		entity.UserEventPlaceViewed:
		return true
	default:
		return false
	}
}
