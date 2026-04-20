package usecase

import (
	"YouSightSeeing/backend/internal/app/dto"
	"YouSightSeeing/backend/internal/app/uc_errors"
	"YouSightSeeing/backend/internal/domain/port"
	"context"
)

type GetUserPreferencesUC struct {
	repo port.UserCategoryPreferencesRepository
}

func NewGetUserPreferencesUC(repo port.UserCategoryPreferencesRepository) *GetUserPreferencesUC {
	return &GetUserPreferencesUC{
		repo: repo,
	}
}

func (uc *GetUserPreferencesUC) Execute(
	ctx context.Context,
	req dto.GetUserPreferencesRequest,
) (dto.GetUserPreferencesResponse, error) {
	if req.UserID.String() == "" {
		return dto.GetUserPreferencesResponse{}, uc_errors.InvalidUserID
	}

	items, err := uc.repo.GetByUserID(ctx, req.UserID)
	if err != nil {
		return dto.GetUserPreferencesResponse{}, err
	}

	resp := dto.GetUserPreferencesResponse{
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
