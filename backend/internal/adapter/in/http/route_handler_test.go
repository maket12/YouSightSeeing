package http_test

import (
	adapterhttp "YouSightSeeing/backend/internal/adapter/in/http"
	"YouSightSeeing/backend/internal/app/dto"
	"YouSightSeeing/backend/internal/app/uc_errors"
	"YouSightSeeing/backend/internal/app/usecase/mocks"
	"bytes"
	"encoding/json"
	"errors"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/labstack/echo/v4"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/mock"
)

func createRouteTestContext(method, path string, body interface{}) (echo.Context, *httptest.ResponseRecorder) {
	e := echo.New()

	var reqBody []byte
	if body != nil {
		reqBody, _ = json.Marshal(body)
	}

	req := httptest.NewRequest(method, path, bytes.NewBuffer(reqBody))
	if body != nil {
		req.Header.Set("Content-Type", "application/json")
	}

	rec := httptest.NewRecorder()
	c := e.NewContext(req, rec)

	return c, rec
}

func TestRH_CalculateRoute(t *testing.T) {
	type testCase struct {
		Name        string
		RequestBody interface{} // <--- string/map
		CallUC      bool
		UCOut       dto.CalculateRouteResponse
		UCErr       error
		WantStatus  int
		WantBody    map[string]interface{}
	}

	var testCoordinates = make([][]float64, 2)

	var testCases = []testCase{
		{
			Name:        "failure - invalid json",
			RequestBody: "{invalid json",
			CallUC:      false,
			UCOut:       dto.CalculateRouteResponse{},
			UCErr:       nil,
			WantStatus:  http.StatusBadRequest,
			WantBody: map[string]interface{}{
				"error": "invalid json",
			},
		},
		{
			Name: "failure - uc error",
			RequestBody: map[string]interface{}{
				"coordinates": testCoordinates,
			},
			CallUC:     true,
			UCOut:      dto.CalculateRouteResponse{},
			UCErr:      uc_errors.Wrap(uc_errors.ErrRouteCalculationFailed, errors.New("ors failed")),
			WantStatus: http.StatusInternalServerError,
			WantBody: map[string]interface{}{
				"error": "failed to calculate route",
			},
		},
		{
			Name: "success - calculate route",
			RequestBody: map[string]interface{}{
				"coordinates": testCoordinates,
			},
			CallUC: true,
			UCOut: dto.CalculateRouteResponse{
				Points:   testCoordinates,
				Distance: 100,
				Duration: 100,
			},
			UCErr:      nil,
			WantStatus: http.StatusOK,
			WantBody: map[string]interface{}{
				"points": [][]float64{
					testCoordinates[0],
					testCoordinates[1],
				},
				"distance": 100,
				"duration": 100,
			},
		},
	}

	for _, tt := range testCases {
		t.Run(tt.Name, func(t *testing.T) {
			mockCalculateRouteUC := mocks.NewCalculateRouteUseCase(t)

			if tt.CallUC {
				mockCalculateRouteUC.On("Execute", mock.Anything, mock.Anything).
					Return(tt.UCOut, tt.UCErr)
			}

			var logBuffer bytes.Buffer
			logger := slog.New(slog.NewJSONHandler(&logBuffer, nil))

			handler := adapterhttp.NewRouteHandler(
				logger,
				mockCalculateRouteUC,
			)

			c, rec := createRouteTestContext(
				http.MethodGet,
				"/api/routes/calculate",
				tt.RequestBody,
			)

			err := handler.CalculateRoute(c)

			assert.NoError(t, err)
			assert.Equal(t, tt.WantStatus, rec.Code)

			expectedJSON, err := json.Marshal(tt.WantBody)
			assert.NoError(t, err)

			assert.JSONEq(t, string(expectedJSON), rec.Body.String())

			mockCalculateRouteUC.AssertExpectations(t)
		})
	}
}
