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

type orsMatrixResponseRaw struct {
	Durations [][]float64 `json:"durations"`
	Distances [][]float64 `json:"distances"`
}

type RouteMatrixCalculator struct {
	APIKey     string
	BaseURL    string
	HTTPClient *http.Client
}

func NewRouteMatrixCalculator(apiKey string) *RouteMatrixCalculator {
	return &RouteMatrixCalculator{
		APIKey:     apiKey,
		BaseURL:    "https://api.openrouteservice.org/v2/matrix",
		HTTPClient: &http.Client{Timeout: 30 * time.Second},
	}
}

func (a *RouteMatrixCalculator) CalculateMatrix(
	ctx context.Context,
	req entity.ORSMatrixRequest,
) (*entity.RouteMatrix, error) {
	if len(req.Locations) == 0 {
		return nil, fmt.Errorf("matrix request must contain at least 1 location")
	}

	if len(req.Metrics) == 0 {
		req.Metrics = []string{"duration"}
	}

	reqBody, err := json.Marshal(req)
	if err != nil {
		return nil, fmt.Errorf("failed to marshal ORS matrix request: %w", err)
	}

	profile := "foot-walking"
	url := fmt.Sprintf("%s/%s", a.BaseURL, profile)

	httpReq, err := http.NewRequestWithContext(ctx, http.MethodPost, url, bytes.NewBuffer(reqBody))
	if err != nil {
		return nil, fmt.Errorf("failed to create matrix http request: %w", err)
	}

	httpReq.Header.Set("Authorization", a.APIKey)
	httpReq.Header.Set("Content-Type", "application/json")
	httpReq.Header.Set("Accept", "application/json")

	resp, err := a.HTTPClient.Do(httpReq)
	if err != nil {
		return nil, fmt.Errorf("ors matrix api request failed: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("ors matrix api returned error status: %d", resp.StatusCode)
	}

	var raw orsMatrixResponseRaw
	if err := json.NewDecoder(resp.Body).Decode(&raw); err != nil {
		return nil, fmt.Errorf("failed to decode ors matrix response: %w", err)
	}

	return &entity.RouteMatrix{
		Durations: raw.Durations,
		Distances: raw.Distances,
	}, nil
}
