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
	params := s.buildParams(filter)
	reqURL := s.buildRequestURL(params)

	resp, err := s.doRequest(ctx, reqURL)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	raw, err := s.decodeResponse(resp)
	if err != nil {
		return nil, err
	}

	return s.mapPlaces(raw), nil
}

func (s *PlacesService) buildParams(filter entity.PlacesSearchFilter) url.Values {
	params := url.Values{}
	params.Add("apiKey", s.APIKey)

	if len(filter.Categories) > 0 {
		params.Add("categories", strings.Join(filter.Categories, ","))
	}

	params.Add("filter", fmt.Sprintf("circle:%f,%f,%d", filter.Lon, filter.Lat, filter.Radius))
	params.Add("bias", fmt.Sprintf("proximity:%f,%f", filter.Lon, filter.Lat))

	if filter.Limit > 0 {
		params.Add("limit", fmt.Sprintf("%d", filter.Limit))
	} else {
		params.Add("limit", "20")
	}
	return params
}

func (s *PlacesService) buildRequestURL(params url.Values) string {
	return fmt.Sprintf("%s?%s", s.BaseURL, params.Encode())
}

func (s *PlacesService) doRequest(ctx context.Context, reqURL string) (*http.Response, error) {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, reqURL, nil)
	if err != nil {
		return nil, fmt.Errorf("failed to create request: %w", err)
	}
	resp, err := s.HTTPClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("geoapify api request failed: %w", err)
	}
	return resp, nil
}

func (s *PlacesService) decodeResponse(resp *http.Response) (gpfResponseRaw, error) {
	if resp.StatusCode != http.StatusOK {
		return gpfResponseRaw{}, fmt.Errorf("geoapify api returned error status: %d", resp.StatusCode)
	}
	var raw gpfResponseRaw
	if err := json.NewDecoder(resp.Body).Decode(&raw); err != nil {
		return gpfResponseRaw{}, fmt.Errorf("failed to decode geoapify response: %w", err)
	}
	return raw, nil
}

func (s *PlacesService) mapPlaces(raw gpfResponseRaw) []entity.Place {
	places := make([]entity.Place, 0, len(raw.Features))
	for _, f := range raw.Features {
		name, ok := resolveName(f.Properties.Name, f.Properties.Formatted)
		if !ok {
			continue
		}
		places = append(places, entity.Place{
			ID:          f.Properties.PlaceID,
			Name:        name,
			Address:     f.Properties.Formatted,
			Categories:  f.Properties.Categories,
			Coordinates: f.Geometry.Coordinates,
		})
	}
	return places
}

func resolveName(name, formatted string) (string, bool) {
	if name != "" {
		return name, true
	}
	if formatted != "" {
		return formatted, true
	}
	return "", false
}
