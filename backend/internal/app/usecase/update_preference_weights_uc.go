package usecase

import (
	"YouSightSeeing/backend/internal/app/dto"
	"YouSightSeeing/backend/internal/app/uc_errors"
	"YouSightSeeing/backend/internal/domain/entity"
	"YouSightSeeing/backend/internal/domain/port"
	"context"
	"database/sql"
	"errors"
	"strings"
	"time"

	"github.com/google/uuid"
)

const (
	defaultPreferenceWeight = 0.5
	minPreferenceWeight     = 0.0
	maxPreferenceWeight     = 1.0
)

type UpdatePreferenceWeightsUC struct {
	preferencesRepo port.UserCategoryPreferencesRepository
}

func NewUpdatePreferenceWeightsUC(
	preferencesRepo port.UserCategoryPreferencesRepository,
) *UpdatePreferenceWeightsUC {
	return &UpdatePreferenceWeightsUC{
		preferencesRepo: preferencesRepo,
	}
}

func (uc *UpdatePreferenceWeightsUC) Execute(
	ctx context.Context,
	req dto.UpdatePreferenceWeightsRequest,
) (dto.UpdatePreferenceWeightsResponse, error) {
	if req.UserID == uuid.Nil {
		return dto.UpdatePreferenceWeightsResponse{}, uc_errors.InvalidUserID
	}

	category := strings.TrimSpace(req.Category)
	if category == "" {
		return dto.UpdatePreferenceWeightsResponse{}, uc_errors.ErrEmptyPreferenceCategory
	}

	delta := preferenceDeltaByEventType(req.EventType)
	if delta <= 0 {
		return dto.UpdatePreferenceWeightsResponse{
			Updated:  false,
			Category: category,
			Weight:   0,
		}, nil
	}

	currentWeight := defaultPreferenceWeight

	currentPreference, err := uc.preferencesRepo.GetByUserIDAndCategory(ctx, req.UserID, category)
	if err != nil {
		if !errors.Is(err, sql.ErrNoRows) {
			return dto.UpdatePreferenceWeightsResponse{}, err
		}
	} else {
		currentWeight = currentPreference.Weight
	}

	newWeight := increasePreferenceWeight(currentWeight, delta)

	err = uc.preferencesRepo.Upsert(ctx, &entity.UserCategoryPreference{
		ID:        uuid.New(),
		UserID:    req.UserID,
		Category:  category,
		Weight:    newWeight,
		UpdatedAt: time.Now().UTC(),
	})
	if err != nil {
		return dto.UpdatePreferenceWeightsResponse{}, err
	}

	return dto.UpdatePreferenceWeightsResponse{
		Updated:  true,
		Category: category,
		Weight:   newWeight,
	}, nil
}

func preferenceDeltaByEventType(eventType string) float64 {
	switch eventType {
	case entity.UserEventPlaceViewed:
		return 0.03
	case entity.UserEventRouteOpened:
		return 0.05
	case entity.UserEventRouteSaved:
		return 0.10
	case entity.UserEventRouteCompleted:
		return 0.15
	default:
		return 0
	}
}

func increasePreferenceWeight(currentWeight float64, delta float64) float64 {
	if currentWeight < minPreferenceWeight {
		currentWeight = minPreferenceWeight
	}
	if currentWeight > maxPreferenceWeight {
		currentWeight = maxPreferenceWeight
	}

	newWeight := currentWeight + delta*(1-currentWeight)

	if newWeight < minPreferenceWeight {
		return minPreferenceWeight
	}
	if newWeight > maxPreferenceWeight {
		return maxPreferenceWeight
	}

	return newWeight
}
