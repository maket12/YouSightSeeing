package ors

import (
	"YouSightSeeing/backend/internal/domain/entity"
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"time"
)

type orsResponseRaw struct {
	Features []struct {
		Geometry struct {
			Coordinates [][]float64 `json:"coordinates"`
		} `json:"geometry"`
		Properties struct {
			Summary struct {
				Distance float64 `json:"distance"`
				Duration float64 `json:"duration"`
			} `json:"summary"`
		} `json:"properties"`
	} `json:"features"`
}

type RouteCalculator struct {
	APIKey     string
	BaseURL    string
	HTTPClient *http.Client
}

func NewRouteCalculator(apiKey string) *RouteCalculator {
	return &RouteCalculator{
		APIKey:     apiKey,
		BaseURL:    "https://api.openrouteservice.org/v2/directions",
		HTTPClient: &http.Client{Timeout: 30 * time.Second},
	}
}

func (a *RouteCalculator) CalculateRoute(ctx context.Context, req entity.ORSRequest) (*entity.Route, error) {
	reqBodyResponse, err := json.Marshal(req)
	if err != nil {
		return nil, fmt.Errorf("failed to marshal ORS request: %w", err)
	}

	profile := "foot-walking"
	url := fmt.Sprintf("%s/%s/geojson", a.BaseURL, profile)

	httpReq, err := http.NewRequestWithContext(ctx, "POST", url, bytes.NewBuffer(reqBodyResponse))
	if err != nil {
		return nil, fmt.Errorf("failed to create http request: %w", err)
	}

	httpReq.Header.Set("Authorization", a.APIKey)
	httpReq.Header.Set("Content-type", "application/json")

	resp, err := a.HTTPClient.Do(httpReq)
	if err != nil {
		return nil, fmt.Errorf("ors api request failed: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("ors api returned error status: %d", resp.StatusCode)
	}
	var raw orsResponseRaw
	if err := json.NewDecoder(resp.Body).Decode(&raw); err != nil {
		return nil, fmt.Errorf("failed to decode ors response: %w", err)
	}
	if len(raw.Features) == 0 {
		return nil, fmt.Errorf("no features in response")
	}

	feature := raw.Features[0]

	return &entity.Route{
		Geometry: feature.Geometry.Coordinates,
		Distance: feature.Properties.Summary.Distance,
		Duration: feature.Properties.Summary.Duration,
	}, nil
}
