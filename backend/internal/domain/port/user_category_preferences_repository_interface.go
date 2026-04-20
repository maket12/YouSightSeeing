package port

import (
	"YouSightSeeing/backend/internal/domain/entity"
	"context"

	"github.com/google/uuid"
)

type UserCategoryPreferencesRepository interface {
	Upsert(ctx context.Context, preference *entity.UserCategoryPreference) error
	GetByUserID(ctx context.Context, userID uuid.UUID) ([]entity.UserCategoryPreference, error)
	GetByUserIDAndCategory(ctx context.Context, userID uuid.UUID, category string) (*entity.UserCategoryPreference, error)
	DeleteByUserID(ctx context.Context, userID uuid.UUID) error
}
