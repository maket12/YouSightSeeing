package gpf

import (
	"YouSightSeeing/backend/internal/domain/entity"
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"net/url"
	"strings"
	"time"
)

type gpfResponseRaw struct {
	Type     string `json:"type"`
	Features []struct {
		Type     string `json:"type"`
		Geometry struct {
			Type        string    `json:"type"`
			Coordinates []float64 `json:"coordinates"`
		} `json:"geometry"`
		Properties struct {
			Name       string   `json:"name"`
			Formatted  string   `json:"formatted"` // Полный адрес одной строкой
			PlaceID    string   `json:"place_id"`
			Categories []string `json:"categories"`
		} `json:"properties"`
	} `json:"features"`
}

type PlacesService struct {
	APIKey     string
	BaseURL    string
	HTTPClient *http.Client
}

func NewPlacesService(apiKey string) *PlacesService {
	return &PlacesService{
		APIKey:     apiKey,
		BaseURL:    "https://api.geoapify.com/v2/places",
		HTTPClient: &http.Client{Timeout: 10 * time.Second},
	}
}

func (s *PlacesService) Search(ctx context.Context, filter entity.PlacesSearchFilter) ([]entity.Place, error) {
	params := url.Values{}
	params.Add("apiKey", s.APIKey)

	if len(filter.Categories) > 0 {
		params.Add("categories", strings.Join(filter.Categories, ","))
	}

	circleFilter := fmt.Sprintf("circle:%f,%f,%d", filter.Lon, filter.Lat, filter.Radius)
	params.Add("filter", circleFilter)

	bias := fmt.Sprintf("proximity:%f,%f", filter.Lon, filter.Lat)
	params.Add("bias", bias)

	if filter.Limit > 0 {
		params.Add("limit", fmt.Sprintf("%d", filter.Limit))
	} else {
		params.Add("limit", "20")
	}

	reqURL := fmt.Sprintf("%s?%s", s.BaseURL, params.Encode())

	req, err := http.NewRequestWithContext(ctx, "GET", reqURL, nil)
	if err != nil {
		return nil, fmt.Errorf("failed to create request: %w", err)
	}

	resp, err := s.HTTPClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("geoapify api request failed: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("geoapify api returned error status: %d", resp.StatusCode)
	}

	var raw gpfResponseRaw
	if err := json.NewDecoder(resp.Body).Decode(&raw); err != nil {
		return nil, fmt.Errorf("failed to decode geoapify response: %w", err)
	}

	places := make([]entity.Place, 0, len(raw.Features))
	for _, feature := range raw.Features {
		name := feature.Properties.Name
		if name == "" {
			if feature.Properties.Formatted != "" {
				name = feature.Properties.Formatted
			} else {
				continue
			}
		}

		places = append(places, entity.Place{
			ID:          feature.Properties.PlaceID,
			Name:        name,
			Address:     feature.Properties.Formatted,
			Categories:  feature.Properties.Categories,
			Coordinates: feature.Geometry.Coordinates,
		})
	}

	return places, nil
}
