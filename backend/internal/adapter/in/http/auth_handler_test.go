package http

import (
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
	"time"

	"github.com/google/uuid"
	"github.com/labstack/echo/v4"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/mock"
)

func createAuthTestContext(method, path string, body interface{}) (echo.Context, *httptest.ResponseRecorder) {
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

func TestAH_GoogleAuth(t *testing.T) {
	type testCase struct {
		Name        string
		GoogleToken string
		RequestBody interface{} // <--- string/map
		CallUC      bool
		UCOut       dto.GoogleAuthResponse
		UCErr       error
		WantStatus  int
		WantBody    map[string]interface{}
	}

	var (
		testTime        = time.Date(2026, 1, 1, 0, 0, 0, 0, time.UTC)
		validUID        = uuid.New()
		testGoogleToken = "new-google-token"
	)

	var testCases = []testCase{
		{
			Name:        "failure - invalid json",
			GoogleToken: "",
			RequestBody: "{invalid json",
			CallUC:      false,
			UCOut:       dto.GoogleAuthResponse{},
			UCErr:       nil,
			WantStatus:  http.StatusBadRequest,
			WantBody: map[string]interface{}{
				"error": "invalid json",
			},
		},
		{
			Name:        "failure - empty google-token",
			GoogleToken: "",
			RequestBody: map[string]interface{}{
				"google_token": "",
			},
			CallUC:     true,
			UCOut:      dto.GoogleAuthResponse{},
			UCErr:      uc_errors.EmptyGoogleTokenError,
			WantStatus: http.StatusBadRequest,
			WantBody: map[string]interface{}{
				"error": "empty google token",
			},
		},
		{
			Name:        "failure - token validation error",
			GoogleToken: testGoogleToken,
			RequestBody: map[string]interface{}{
				"google_token": testGoogleToken,
			},
			CallUC:     true,
			UCOut:      dto.GoogleAuthResponse{},
			UCErr:      uc_errors.Wrap(uc_errors.GoogleTokenValidationError, errors.New("db failed")),
			WantStatus: http.StatusInternalServerError,
			WantBody: map[string]interface{}{
				"error": "failed to validate google token",
			},
		},
		{
			Name:        "failure - internal error",
			GoogleToken: testGoogleToken,
			RequestBody: map[string]interface{}{
				"google_token": testGoogleToken,
			},
			CallUC:     true,
			UCOut:      dto.GoogleAuthResponse{},
			UCErr:      uc_errors.Wrap(uc_errors.CreateUserError, errors.New("db failed")),
			WantStatus: http.StatusInternalServerError,
			WantBody: map[string]interface{}{
				"error": "failed to create user",
			},
		},
		{
			Name:        "success - auth",
			GoogleToken: testGoogleToken,
			RequestBody: map[string]interface{}{
				"google_token": testGoogleToken,
			},
			CallUC: true,
			UCOut: dto.GoogleAuthResponse{
				AccessToken:  "new-access",
				RefreshToken: "new-refresh",
				User: dto.UserResponse{
					ID:            validUID,
					GoogleSub:     "a123fd",
					Email:         "new123user@gmail.com",
					FullName:      nil,
					Picture:       nil,
					FirstName:     nil,
					LastName:      nil,
					EmailVerified: true,
					GoogleDomain:  nil,
					Locale:        nil,
					CreatedAt:     testTime,
					UpdatedAt:     &testTime,
				},
			},
			UCErr:      nil,
			WantStatus: http.StatusCreated,
			WantBody: map[string]interface{}{
				"access_token":  "new-access",
				"refresh_token": "new-refresh",
				"user": map[string]interface{}{
					"id":             validUID.String(),
					"google_sub":     "a123fd",
					"email":          "new123user@gmail.com",
					"email_verified": true,
					"created_at":     testTime.Format(time.RFC3339),
					"updated_at":     testTime.Format(time.RFC3339),
				},
			},
		},
	}

	for _, tt := range testCases {
		t.Run(tt.Name, func(t *testing.T) {
			mockGoogleAuthUC := mocks.NewGoogleAuthUseCase(t)
			mockRefreshTokenUC := mocks.NewRefreshTokenUseCase(t)
			mockLogoutUC := mocks.NewLogoutUseCase(t)

			if tt.CallUC {
				mockGoogleAuthUC.On("Execute", mock.Anything, mock.MatchedBy(func(req dto.GoogleAuthRequest) bool {
					return req.GoogleToken == tt.GoogleToken
				})).Return(tt.UCOut, tt.UCErr)
			}

			var logBuffer bytes.Buffer
			logger := slog.New(slog.NewJSONHandler(&logBuffer, nil))

			handler := NewAuthHandler(
				logger,
				mockGoogleAuthUC,
				mockRefreshTokenUC,
				mockLogoutUC,
			)

			c, rec := createAuthTestContext(
				http.MethodPost,
				"/auth/google",
				tt.RequestBody,
			)

			err := handler.GoogleAuth(c)

			assert.NoError(t, err)
			assert.Equal(t, tt.WantStatus, rec.Code)

			expectedJSON, err := json.Marshal(tt.WantBody)
			assert.NoError(t, err)

			assert.JSONEq(t, string(expectedJSON), rec.Body.String())

			mockGoogleAuthUC.AssertExpectations(t)
		})
	}
}

func TestAH_RefreshToken(t *testing.T) {
	type testCase struct {
		Name         string
		RefreshToken string
		RequestBody  interface{} // <--- string/map
		CallUC       bool
		UCOut        dto.RefreshTokenResponse
		UCErr        error
		WantStatus   int
		WantBody     map[string]interface{}
	}

	var (
		validUID         = uuid.New()
		testRefreshToken = "new-refresh-token"
	)

	var testCases = []testCase{
		{
			Name:         "failure - invalid json",
			RefreshToken: "",
			RequestBody:  "{invalid json",
			CallUC:       false,
			UCOut:        dto.RefreshTokenResponse{},
			UCErr:        nil,
			WantStatus:   http.StatusBadRequest,
			WantBody: map[string]interface{}{
				"error": "invalid json",
			},
		},
		{
			Name:         "failure - validation",
			RefreshToken: "",
			RequestBody: map[string]interface{}{
				"refresh_token": "",
			},
			CallUC:     true,
			UCOut:      dto.RefreshTokenResponse{},
			UCErr:      uc_errors.EmptyRefreshTokenError,
			WantStatus: http.StatusBadRequest,
			WantBody: map[string]interface{}{
				"error": "empty refresh token",
			},
		},
		{
			Name:         "failure - token expired/revoked",
			RefreshToken: testRefreshToken,
			RequestBody: map[string]interface{}{
				"refresh_token": testRefreshToken,
			},
			CallUC:     true,
			UCOut:      dto.RefreshTokenResponse{},
			UCErr:      uc_errors.ExpiredRefreshTokenError,
			WantStatus: http.StatusUnauthorized,
			WantBody: map[string]interface{}{
				"error": "expired refresh token",
			},
		},
		{
			Name:         "failure - token not found",
			RefreshToken: testRefreshToken,
			RequestBody: map[string]interface{}{
				"refresh_token": testRefreshToken,
			},
			CallUC:     true,
			UCOut:      dto.RefreshTokenResponse{},
			UCErr:      uc_errors.RefreshTokenNotFoundError,
			WantStatus: http.StatusNotFound,
			WantBody: map[string]interface{}{
				"error": "refresh token not found",
			},
		},
		{
			Name:         "failure - internal error",
			RefreshToken: testRefreshToken,
			RequestBody: map[string]interface{}{
				"refresh_token": testRefreshToken,
			},
			CallUC:     true,
			UCOut:      dto.RefreshTokenResponse{},
			UCErr:      uc_errors.Wrap(uc_errors.RevokeRefreshTokenError, errors.New("db failed")),
			WantStatus: http.StatusInternalServerError,
			WantBody: map[string]interface{}{
				"error": "failed to revoke refresh token",
			},
		},
		{
			Name:         "success - logout",
			RefreshToken: testRefreshToken,
			RequestBody: map[string]interface{}{
				"refresh_token": testRefreshToken,
			},
			CallUC: true,
			UCOut: dto.RefreshTokenResponse{
				AccessToken:  "new-access",
				RefreshToken: "new-refresh",
				User:         dto.UserResponse{ID: validUID},
			},
			UCErr:      nil,
			WantStatus: http.StatusOK,
			WantBody: map[string]interface{}{
				"access_token":  "new-access",
				"refresh_token": "new-refresh",
				"user": map[string]interface{}{
					"id":             validUID.String(),
					"google_sub":     "",
					"email":          "",
					"email_verified": false,
					"created_at":     "0001-01-01T00:00:00Z",
					"updated_at":     interface{}(nil),
				},
			},
		},
	}

	for _, tt := range testCases {
		t.Run(tt.Name, func(t *testing.T) {
			mockGoogleAuthUC := mocks.NewGoogleAuthUseCase(t)
			mockRefreshTokenUC := mocks.NewRefreshTokenUseCase(t)
			mockLogoutUC := mocks.NewLogoutUseCase(t)

			if tt.CallUC {
				mockRefreshTokenUC.On("Execute", mock.Anything, dto.RefreshTokenRequest{
					RefreshToken: tt.RefreshToken,
				}).Return(tt.UCOut, tt.UCErr)
			}

			var logBuffer bytes.Buffer
			logger := slog.New(slog.NewJSONHandler(&logBuffer, nil))

			handler := NewAuthHandler(
				logger,
				mockGoogleAuthUC,
				mockRefreshTokenUC,
				mockLogoutUC,
			)

			c, rec := createAuthTestContext(
				http.MethodPost,
				"/auth/refresh",
				tt.RequestBody,
			)

			err := handler.RefreshToken(c)

			assert.NoError(t, err)
			assert.Equal(t, tt.WantStatus, rec.Code)

			expectedJSON, err := json.Marshal(tt.WantBody)
			assert.NoError(t, err)

			assert.JSONEq(t, string(expectedJSON), rec.Body.String())

			mockGoogleAuthUC.AssertExpectations(t)
		})
	}
}

func TestAH_Logout(t *testing.T) {
	type testCase struct {
		Name         string
		RefreshToken string
		RequestBody  interface{} // <--- string/map
		CallUC       bool
		UCOut        dto.LogoutResponse
		UCErr        error
		WantStatus   int
		WantBody     map[string]interface{}
	}

	var (
		validUID         = uuid.New()
		testRefreshToken = "new-refresh-token"
	)

	var testCases = []testCase{
		{
			Name:         "failure - invalid json",
			RefreshToken: "",
			RequestBody:  "{invalid json",
			CallUC:       false,
			UCOut:        dto.LogoutResponse{},
			UCErr:        nil,
			WantStatus:   http.StatusBadRequest,
			WantBody: map[string]interface{}{
				"error": "invalid json",
			},
		},
		{
			Name:         "failure - validation",
			RefreshToken: "",
			RequestBody: map[string]interface{}{
				"refresh_token": "",
			},
			CallUC:     true,
			UCOut:      dto.LogoutResponse{},
			UCErr:      uc_errors.EmptyRefreshTokenError,
			WantStatus: http.StatusBadRequest,
			WantBody: map[string]interface{}{
				"error": "empty refresh token",
			},
		},
		{
			Name:         "failure - internal error",
			RefreshToken: testRefreshToken,
			RequestBody: map[string]interface{}{
				"refresh_token": testRefreshToken,
			},
			CallUC:     true,
			UCOut:      dto.LogoutResponse{},
			UCErr:      uc_errors.Wrap(uc_errors.RevokeRefreshTokenError, errors.New("db failed")),
			WantStatus: http.StatusInternalServerError,
			WantBody: map[string]interface{}{
				"error": "failed to revoke refresh token",
			},
		},
		{
			Name:         "success - logout",
			RefreshToken: testRefreshToken,
			RequestBody: map[string]interface{}{
				"refresh_token": testRefreshToken,
			},
			CallUC: true,
			UCOut: dto.LogoutResponse{
				UserID: validUID,
				Logout: true,
			},
			UCErr:      nil,
			WantStatus: http.StatusOK,
			WantBody: map[string]interface{}{
				"user_id": validUID.String(),
				"logout":  true,
			},
		},
	}

	for _, tt := range testCases {
		t.Run(tt.Name, func(t *testing.T) {
			mockGoogleAuthUC := mocks.NewGoogleAuthUseCase(t)
			mockRefreshTokenUC := mocks.NewRefreshTokenUseCase(t)
			mockLogoutUC := mocks.NewLogoutUseCase(t)

			if tt.CallUC {
				mockLogoutUC.On("Execute", mock.Anything, dto.LogoutRequest{
					RefreshToken: tt.RefreshToken,
				}).Return(tt.UCOut, tt.UCErr)
			}

			var logBuffer bytes.Buffer
			logger := slog.New(slog.NewJSONHandler(&logBuffer, nil))

			handler := NewAuthHandler(
				logger,
				mockGoogleAuthUC,
				mockRefreshTokenUC,
				mockLogoutUC,
			)

			c, rec := createAuthTestContext(
				http.MethodPost,
				"/auth/logout",
				tt.RequestBody,
			)

			err := handler.Logout(c)

			assert.NoError(t, err)
			assert.Equal(t, tt.WantStatus, rec.Code)

			expectedJSON, err := json.Marshal(tt.WantBody)
			assert.NoError(t, err)

			assert.JSONEq(t, string(expectedJSON), rec.Body.String())

			mockGoogleAuthUC.AssertExpectations(t)
		})
	}
}
