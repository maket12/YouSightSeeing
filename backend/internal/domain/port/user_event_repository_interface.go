package port

import (
	"YouSightSeeing/backend/internal/domain/entity"
	"context"

	"github.com/google/uuid"
)

type UserEventRepository interface {
	Create(ctx context.Context, event *entity.UserEvent) error
	GetByUserID(ctx context.Context, userID uuid.UUID, limit, offset int) ([]entity.UserEvent, error)
	GetRecentByUserID(ctx context.Context, userID uuid.UUID, limit int) ([]entity.UserEvent, error)
	// GetGlobalStatsByPlaceIDs returns aggregated event counts for the provided place IDs
	// keyed by place_id. Implementations should return counts for at least generated and saved events.
	GetGlobalStatsByPlaceIDs(ctx context.Context, placeIDs []string) (map[string]PlaceGlobalStats, error)
}

type PlaceGlobalStats struct {
	GeneratedCount int `json:"generated_count"`
	SavedCount     int `json:"saved_count"`
}
