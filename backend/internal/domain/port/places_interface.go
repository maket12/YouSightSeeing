package port

import (
	"YouSightSeeing/backend/internal/domain/entity"
	"context"
)

type PlacesProvider interface {
	SearchPlaces(ctx context.Context, filter entity.PlacesSearchFilter) ([]entity.Place, error)
}
