//go:build integration
// +build integration

package ors_test

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"github.com/stretchr/testify/suite"

	"YouSightSeeing/backend/internal/adapter/out/ors"
	"YouSightSeeing/backend/internal/domain/entity"
)

type RouteCalculatorTestSuite struct {
	suite.Suite
	ctx context.Context
}

func (s *RouteCalculatorTestSuite) SetupTest() {
	s.ctx = context.Background()
}

func TestRouteCalculatorSuite(t *testing.T) {
	if testing.Short() {
		t.Skip("Skipping integration tests in short mode")
	}
	suite.Run(t, new(RouteCalculatorTestSuite))
}

func (s *RouteCalculatorTestSuite) TestCalculateRoute_Success() {
	mockServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		assert.Equal(s.T(), "POST", r.Method)
		assert.Contains(s.T(), r.URL.Path, "foot-walking/geojson")

		authHeader := r.Header.Get("Authorization")
		assert.Equal(s.T(), "test-api-key", authHeader)

		var req entity.ORSRequest
		err := json.NewDecoder(r.Body).Decode(&req)
		require.NoError(s.T(), err)

		// Проверяем, что пришли координаты
		assert.Len(s.T(), req.Coordinates, 2)
		assert.Equal(s.T(), 8.681495, req.Coordinates[0][0])
		assert.Equal(s.T(), 49.41461, req.Coordinates[0][1])

		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusOK)

		response := map[string]interface{}{
			"features": []map[string]interface{}{
				{
					"geometry": map[string]interface{}{
						"coordinates": [][]float64{
							{8.681496, 49.414601},
							{8.683185, 49.418527},
							{8.687871, 49.420322},
						},
					},
					"properties": map[string]interface{}{
						"summary": map[string]interface{}{
							"distance": 1036.1,
							"duration": 746.0,
						},
					},
				},
			},
		}

		json.NewEncoder(w).Encode(response)
	}))
	defer mockServer.Close()

	calculator := ors.NewRouteCalculator("test-api-key")
	calculator.BaseURL = mockServer.URL

	req := entity.ORSRequest{
		Coordinates: [][]float64{
			{8.681495, 49.41461},
			{8.687872, 49.420318},
		},
		Geometry:     true,
		Instructions: false,
	}

	route, err := calculator.CalculateRoute(s.ctx, req)

	require.NoError(s.T(), err)
	assert.NotNil(s.T(), route)
	assert.Equal(s.T(), 1036.1, route.Distance)
	assert.Equal(s.T(), 746.0, route.Duration)
	assert.Len(s.T(), route.Geometry, 3)
	assert.Equal(s.T(), 8.681496, route.Geometry[0][0])
}

func (s *RouteCalculatorTestSuite) TestCalculateRoute_BadRequest() {
	mockServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusBadRequest)
		w.Write([]byte(`{"error":{"message":"Invalid coordinates"}}`))
	}))
	defer mockServer.Close()

	calculator := ors.NewRouteCalculator("test-api-key")
	calculator.BaseURL = mockServer.URL

	req := entity.ORSRequest{
		Coordinates: [][]float64{},
	}

	route, err := calculator.CalculateRoute(s.ctx, req)

	assert.Error(s.T(), err)
	assert.Nil(s.T(), route)
	assert.Contains(s.T(), err.Error(), "400")
}

func (s *RouteCalculatorTestSuite) TestCalculateRoute_Unauthorized() {
	mockServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusUnauthorized)
		w.Write([]byte(`{"error":{"message":"Invalid API key"}}`))
	}))
	defer mockServer.Close()

	calculator := ors.NewRouteCalculator("invalid-key")
	calculator.BaseURL = mockServer.URL

	req := entity.ORSRequest{
		Coordinates: [][]float64{{8.681495, 49.41461}, {8.687872, 49.420318}},
	}

	route, err := calculator.CalculateRoute(s.ctx, req)

	assert.Error(s.T(), err)
	assert.Nil(s.T(), route)
	assert.Contains(s.T(), err.Error(), "401")
}

func (s *RouteCalculatorTestSuite) TestCalculateRoute_ServerError() {
	mockServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusInternalServerError)
		w.Write([]byte(`{"error":{"message":"Internal server error"}}`))
	}))
	defer mockServer.Close()

	calculator := ors.NewRouteCalculator("test-api-key")
	calculator.BaseURL = mockServer.URL

	req := entity.ORSRequest{
		Coordinates: [][]float64{{8.681495, 49.41461}, {8.687872, 49.420318}},
	}

	route, err := calculator.CalculateRoute(s.ctx, req)

	assert.Error(s.T(), err)
	assert.Nil(s.T(), route)
	assert.Contains(s.T(), err.Error(), "500")
}

func (s *RouteCalculatorTestSuite) TestCalculateRoute_InvalidJSON() {
	mockServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
		w.Write([]byte(`{invalid json}`))
	}))
	defer mockServer.Close()

	calculator := ors.NewRouteCalculator("test-api-key")
	calculator.BaseURL = mockServer.URL

	req := entity.ORSRequest{
		Coordinates: [][]float64{{8.681495, 49.41461}, {8.687872, 49.420318}},
	}

	route, err := calculator.CalculateRoute(s.ctx, req)

	assert.Error(s.T(), err)
	assert.Nil(s.T(), route)
	assert.Contains(s.T(), err.Error(), "decode")
}

func (s *RouteCalculatorTestSuite) TestCalculateRoute_EmptyFeatures() {
	mockServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
		w.Write([]byte(`{"features":[]}`))
	}))
	defer mockServer.Close()

	calculator := ors.NewRouteCalculator("test-api-key")
	calculator.BaseURL = mockServer.URL

	req := entity.ORSRequest{
		Coordinates: [][]float64{{8.681495, 49.41461}, {8.687872, 49.420318}},
	}

	route, err := calculator.CalculateRoute(s.ctx, req)

	assert.Error(s.T(), err)
	assert.Nil(s.T(), route)
	assert.Contains(s.T(), err.Error(), "no features")
}

func (s *RouteCalculatorTestSuite) TestCalculateRoute_NetworkError() {
	calculator := ors.NewRouteCalculator("test-api-key")
	calculator.BaseURL = "http://invalid.host.that.does.not.exist:9999"
	calculator.HTTPClient.Timeout = 100 * time.Millisecond

	req := entity.ORSRequest{
		Coordinates: [][]float64{{8.681495, 49.41461}, {8.687872, 49.420318}},
	}

	route, err := calculator.CalculateRoute(s.ctx, req)

	assert.Error(s.T(), err)
	assert.Nil(s.T(), route)
	assert.Contains(s.T(), err.Error(), "request failed")
}

func (s *RouteCalculatorTestSuite) TestCalculateRoute_ContextCancellation() {
	mockServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		time.Sleep(5 * time.Second)
		w.WriteHeader(http.StatusOK)
	}))
	defer mockServer.Close()

	calculator := ors.NewRouteCalculator("test-api-key")
	calculator.BaseURL = mockServer.URL

	ctx, cancel := context.WithTimeout(s.ctx, 100*time.Millisecond)
	defer cancel()

	req := entity.ORSRequest{
		Coordinates: [][]float64{{8.681495, 49.41461}, {8.687872, 49.420318}},
	}

	route, err := calculator.CalculateRoute(ctx, req)

	assert.Error(s.T(), err)
	assert.Nil(s.T(), route)
}

func (s *RouteCalculatorTestSuite) TestCalculateRoute_MultipleRoutePoints() {
	mockServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		var req entity.ORSRequest
		json.NewDecoder(r.Body).Decode(&req)

		assert.Len(s.T(), req.Coordinates, 4)

		w.WriteHeader(http.StatusOK)
		response := map[string]interface{}{
			"features": []map[string]interface{}{
				{
					"geometry": map[string]interface{}{
						"coordinates": [][]float64{
							{8.681496, 49.414601},
							{8.683185, 49.418527},
							{8.685000, 49.419000},
							{8.687871, 49.420322},
						},
					},
					"properties": map[string]interface{}{
						"summary": map[string]interface{}{
							"distance": 2000.5,
							"duration": 1200.0,
						},
					},
				},
			},
		}
		json.NewEncoder(w).Encode(response)
	}))
	defer mockServer.Close()

	calculator := ors.NewRouteCalculator("test-api-key")
	calculator.BaseURL = mockServer.URL

	req := entity.ORSRequest{
		Coordinates: [][]float64{
			{8.681495, 49.41461},
			{8.683185, 49.418527},
			{8.685000, 49.419000},
			{8.687872, 49.420318},
		},
		Geometry: true,
	}

	route, err := calculator.CalculateRoute(s.ctx, req)

	require.NoError(s.T(), err)
	assert.NotNil(s.T(), route)
	assert.Equal(s.T(), 2000.5, route.Distance)
	assert.Equal(s.T(), 1200.0, route.Duration)
	assert.Len(s.T(), route.Geometry, 4)
}

func (s *RouteCalculatorTestSuite) TestCalculateRoute_LargeDistance() {
	mockServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
		response := map[string]interface{}{
			"features": []map[string]interface{}{
				{
					"geometry": map[string]interface{}{
						"coordinates": [][]float64{
							{0.0, 0.0},
							{1.0, 1.0},
						},
					},
					"properties": map[string]interface{}{
						"summary": map[string]interface{}{
							"distance": 157249.6,
							"duration": 62999.0,
						},
					},
				},
			},
		}
		json.NewEncoder(w).Encode(response)
	}))
	defer mockServer.Close()

	calculator := ors.NewRouteCalculator("test-api-key")
	calculator.BaseURL = mockServer.URL

	req := entity.ORSRequest{
		Coordinates: [][]float64{{0.0, 0.0}, {1.0, 1.0}},
	}

	route, err := calculator.CalculateRoute(s.ctx, req)

	require.NoError(s.T(), err)
	assert.NotNil(s.T(), route)
	assert.Equal(s.T(), 157249.6, route.Distance)
	assert.Equal(s.T(), 62999.0, route.Duration)
}
