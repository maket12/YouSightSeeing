package port

import (
	"YouSightSeeing/backend/internal/domain/entity"
	"context"
)

type PlacesService interface {
	Search(ctx context.Context, filter entity.PlacesSearchFilter) ([]entity.Place, error)
}
