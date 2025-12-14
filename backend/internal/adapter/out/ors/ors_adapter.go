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

type ORSAdapter struct {
	APIKey     string
	BaseURL    string
	HTTPClient *http.Client
}

func NewORSAdapter(apikey string) *ORSAdapter {
	return &ORSAdapter{
		APIKey:     apikey,
		BaseURL:    "https://api.openrouteservice.org/v2/directions",
		HTTPClient: &http.Client{Timeout: 30 * time.Second},
	}
}

func (a *ORSAdapter) CalculateRoute(ctx context.Context, req entity.ORSRequest) (map[string]interface{}, error) {
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
	var result map[string]interface{}
	if err := json.NewDecoder(resp.Body).Decode(&result); err != nil {
		return nil, fmt.Errorf("failed to decode ors response: %w", err)
	}
	return result, nil
}
