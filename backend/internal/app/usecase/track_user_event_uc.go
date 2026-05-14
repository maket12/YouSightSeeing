package usecase

import (
	"YouSightSeeing/backend/internal/app/dto"
	"YouSightSeeing/backend/internal/app/uc_errors"
	"YouSightSeeing/backend/internal/domain/entity"
	"YouSightSeeing/backend/internal/domain/port"
	"context"
	"strings"
	"time"

	"github.com/google/uuid"
)

type TrackUserEventUC struct {
	repo                port.UserEventRepository
	preferenceWeightsUC UpdatePreferenceWeightsUseCase
}

func NewTrackUserEventUC(repo port.UserEventRepository) *TrackUserEventUC {
	return &TrackUserEventUC{
		repo: repo,
	}
}

func NewTrackUserEventUCWithPreferenceUpdater(
	repo port.UserEventRepository,
	preferenceWeightsUC UpdatePreferenceWeightsUseCase,
) *TrackUserEventUC {
	return &TrackUserEventUC{
		repo:                repo,
		preferenceWeightsUC: preferenceWeightsUC,
	}
}

func (uc *TrackUserEventUC) Execute(
	ctx context.Context,
	req dto.TrackUserEventRequest,
) (dto.TrackUserEventResponse, error) {
	if req.UserID == uuid.Nil {
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
		Category:  normalizeOptionalString(req.Category),
		CreatedAt: time.Now().UTC(),
	})
	if err != nil {
		return dto.TrackUserEventResponse{}, err
	}

	if uc.preferenceWeightsUC != nil && req.Category != nil {
		category := strings.TrimSpace(*req.Category)
		if category != "" {
			_, err := uc.preferenceWeightsUC.Execute(ctx, dto.UpdatePreferenceWeightsRequest{
				UserID:    req.UserID,
				EventType: req.EventType,
				Category:  category,
			})
			if err != nil {
				return dto.TrackUserEventResponse{}, err
			}
		}
	}

	return dto.TrackUserEventResponse{
		Created: true,
	}, nil
}

func normalizeOptionalString(value *string) *string {
	if value == nil {
		return nil
	}

	trimmed := strings.TrimSpace(*value)
	if trimmed == "" {
		return nil
	}

	return &trimmed
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
