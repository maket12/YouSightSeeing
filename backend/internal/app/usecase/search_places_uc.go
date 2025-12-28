package usecase

import (
	"YouSightSeeing/backend/internal/app/dto"
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
	if req.Lat == 0 || req.Lon == 0 {
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

	respPlaces := make([]dto.Place, 0, len(places))
	for _, p := range places {
		respPlaces = append(respPlaces, dto.Place{
			Name:        p.Name,
			Address:     p.Address,
			Categories:  p.Categories,
			Coordinates: p.Coordinates,
			PlaceID:     p.ID,
		})
	}

	return dto.SearchPlacesResponse{Places: respPlaces}, nil
}
