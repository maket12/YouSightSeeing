package gpf

import (
	"YouSightSeeing/backend/internal/domain/entity"
	"context"
	"os"
	"testing"

	"github.com/stretchr/testify/suite"
)

type PlacesServiceTestSuite struct {
	suite.Suite
	service *PlacesService
	apiKey  string
	ctx     context.Context
}

func TestPlacesServiceSuite(t *testing.T) {
	if testing.Short() {
		t.Skip("Skipping integration tests in short mode")
	}
	suite.Run(t, new(PlacesServiceTestSuite))
}

func (s *PlacesServiceTestSuite) SetupSuite() {
	s.apiKey = os.Getenv("GEOPIFY_API_KEY")
	if s.apiKey == "" {
		s.T().Skip("Skipping integration test: GEOPIFY_API_KEY not set")
	}

	s.service = NewPlacesService(s.apiKey)
	s.ctx = context.Background()
}

func (s *PlacesServiceTestSuite) TestSearch_Success() {
	filter := entity.PlacesSearchFilter{
		Lat:        55.0302,
		Lon:        82.9204,
		Radius:     500,
		Categories: []string{"tourism.sights"},
		Limit:      10,
	}

	places, err := s.service.Search(s.ctx, filter)
	s.NoError(err)
	s.NotEmpty(places, "Should return at least one place")

	firstPlace := places[0]
	s.NotEmpty(firstPlace.ID, "Place ID should not be empty")
	s.NotEmpty(firstPlace.Name, "Place Name should not be empty")
	s.NotEmpty(firstPlace.Coordinates, "Place Coordinates should not be empty")
	s.Len(firstPlace.Coordinates, 2, "Coordinates should contain [lon, lat]")
}

func (s *PlacesServiceTestSuite) TestSearch_NoResults() {
	filter := entity.PlacesSearchFilter{
		Lat:        0.0,
		Lon:        0.0,
		Radius:     100,
		Categories: []string{"tourism.sights"},
		Limit:      10,
	}

	places, err := s.service.Search(s.ctx, filter)
	s.NoError(err)
	s.Empty(places, "Should return empty list for remote location")
}

func (s *PlacesServiceTestSuite) TestSearch_InvalidAPIKey() {
	badService := NewPlacesService("invalid-api-key")
	filter := entity.PlacesSearchFilter{
		Lat:        55.0302,
		Lon:        82.9204,
		Radius:     500,
		Categories: []string{"tourism.sights"},
	}

	places, err := badService.Search(s.ctx, filter)
	s.Error(err)
	s.Nil(places)
	s.Contains(err.Error(), "geoapify api returned error status")
}

func (s *PlacesServiceTestSuite) TestSearch_LimitRespected() {
	limit := 3
	filter := entity.PlacesSearchFilter{
		Lat:        48.8566,
		Lon:        2.3522,
		Radius:     2000,
		Categories: []string{"catering.restaurant"},
		Limit:      limit,
	}

	places, err := s.service.Search(s.ctx, filter)
	s.NoError(err)
	s.Len(places, limit, "Should return exactly %d places", limit)
}

func (s *PlacesServiceTestSuite) TestSearch_WithMultipleCategories() {
	filter := entity.PlacesSearchFilter{
		Lat:        55.0302,
		Lon:        82.9204,
		Radius:     1000,
		Categories: []string{"catering.restaurant", "tourism.sights"},
		Limit:      20,
	}

	places, err := s.service.Search(s.ctx, filter)
	s.NoError(err)
	s.NotEmpty(places)
}
