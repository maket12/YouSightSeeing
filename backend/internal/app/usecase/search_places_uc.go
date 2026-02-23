package usecase

import (
	"YouSightSeeing/backend/internal/app/dto"
	"YouSightSeeing/backend/internal/app/mappers"
	"YouSightSeeing/backend/internal/app/uc_errors"
	"YouSightSeeing/backend/internal/domain/entity"
	"YouSightSeeing/backend/internal/domain/port"
	"context"
)

type SearchPlacesUC struct {
	PlacesProvider port.PlacesService
}

func NewSearchPlacesUC(provider port.PlacesService) *SearchPlacesUC {
	return &SearchPlacesUC{
		PlacesProvider: provider,
	}
}

func (uc *SearchPlacesUC) Execute(ctx context.Context, req dto.SearchPlacesRequest) (dto.SearchPlacesResponse, error) {
	if req.Lat < -90 || req.Lat > 90 || req.Lon < -180 || req.Lon > 180 {
		return dto.SearchPlacesResponse{}, uc_errors.ErrInvalidCoordinates
	}
	if req.Radius <= 0 {
		return dto.SearchPlacesResponse{}, uc_errors.ErrInvalidSearchRadius
	}
	if len(req.Categories) == 0 {
		req.Categories = []string{"tourism.sights", "catering.cafe"}
	}

	filter := entity.PlacesSearchFilter{
		Lat:        req.Lat,
		Lon:        req.Lon,
		Radius:     req.Radius,
		Categories: req.Categories,
		Limit:      req.Limit,
	}

	places, err := uc.PlacesProvider.Search(ctx, filter)
	if err != nil {
		return dto.SearchPlacesResponse{}, uc_errors.Wrap(uc_errors.ErrSearchPlacesFailed, err)
	}

	return dto.SearchPlacesResponse{
		Places: mappers.MapPlacesIntoResponse(places),
	}, nil
}
