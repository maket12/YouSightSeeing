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

type UpdateUserPreferencesUC struct {
	repo port.UserCategoryPreferencesRepository
}

func NewUpdateUserPreferencesUC(repo port.UserCategoryPreferencesRepository) *UpdateUserPreferencesUC {
	return &UpdateUserPreferencesUC{
		repo: repo,
	}
}

func (uc *UpdateUserPreferencesUC) Execute(
	ctx context.Context,
	req dto.UpdateUserPreferencesRequest,
) (dto.UpdateUserPreferencesResponse, error) {
	if req.UserID.String() == "" {
		return dto.UpdateUserPreferencesResponse{}, uc_errors.InvalidUserID
	}

	for _, pref := range req.Preferences {
		category := strings.TrimSpace(pref.Category)
		if category == "" {
			return dto.UpdateUserPreferencesResponse{}, uc_errors.ErrEmptyPreferenceCategory
		}
		if pref.Weight < 0 || pref.Weight > 1 {
			return dto.UpdateUserPreferencesResponse{}, uc_errors.ErrInvalidPreferenceWeight
		}

		err := uc.repo.Upsert(ctx, &entity.UserCategoryPreference{
			ID:        uuid.New(),
			UserID:    req.UserID,
			Category:  category,
			Weight:    pref.Weight,
			UpdatedAt: time.Now().UTC(),
		})
		if err != nil {
			return dto.UpdateUserPreferencesResponse{}, err
		}
	}

	items, err := uc.repo.GetByUserID(ctx, req.UserID)
	if err != nil {
		return dto.UpdateUserPreferencesResponse{}, err
	}

	resp := dto.UpdateUserPreferencesResponse{
		Updated:     true,
		Preferences: make([]dto.CategoryPreference, 0, len(items)),
	}

	for _, item := range items {
		resp.Preferences = append(resp.Preferences, dto.CategoryPreference{
			Category: item.Category,
			Weight:   item.Weight,
		})
	}

	return resp, nil
}
