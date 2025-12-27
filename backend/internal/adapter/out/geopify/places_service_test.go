package geoapify

import (
	"YouSightSeeing/backend/internal/domain/entity"
	"context"
	"fmt"
	"os"
	"testing"
)

func TestPlacesService_SearchPlaces_Integration(t *testing.T) {
	apiKey := os.Getenv("GEOAPIFY_API_KEY")
	if apiKey == "" {
		t.Skip("Skipping integration test: GEOAPIFY_API_KEY not set")
	}

	service := NewPlacesService(apiKey)

	filter := entity.PlacesSearchFilter{
		Lat:        55.0302,
		Lon:        82.9204,
		Radius:     300,
		Categories: []string{"tourism.sights"},
		Limit:      100,
	}

	places, err := service.SearchPlaces(context.Background(), filter)
	if err != nil {
		t.Fatalf("SearchPlaces failed: %v", err)
	}

	if len(places) == 0 {
		t.Log("Warning: no places found, but request succeeded")
	}

	for _, p := range places {
		fmt.Printf("Found place: %s (%s)\n", p.Name, p.Address)
	}
}
