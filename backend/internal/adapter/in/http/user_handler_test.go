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

func createUserTestContext(method, path string, body interface{}, userID uuid.UUID) (echo.Context, *httptest.ResponseRecorder) {
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

	if userID != uuid.Nil {
		c.Set("user_id", userID)
	}

	return c, rec
}

func TestUH_GetMe(t *testing.T) {
	type testCase struct {
		Name       string
		UserID     uuid.UUID
		CallUC     bool
		UCOut      dto.GetUserResponse
		UCErr      error
		WantStatus int
		WantBody   map[string]interface{}
	}

	var (
		testTime = time.Date(2026, 1, 1, 0, 0, 0, 0, time.UTC)
		validUID = uuid.New()
	)

	var testCases = []testCase{
		{
			Name:       "failure - unauthorized (no uid)",
			UserID:     uuid.Nil,
			CallUC:     false,
			UCOut:      dto.GetUserResponse{},
			UCErr:      nil,
			WantStatus: http.StatusUnauthorized,
			WantBody: map[string]interface{}{
				"error": "not authenticated",
			},
		},
		{
			Name:       "failure - user not found",
			UserID:     validUID,
			CallUC:     true,
			UCOut:      dto.GetUserResponse{},
			UCErr:      uc_errors.UserNotFoundError,
			WantStatus: http.StatusNotFound,
			WantBody: map[string]interface{}{
				"error": "user not found",
			},
		},
		{
			Name:       "failure - internal error",
			UserID:     validUID,
			CallUC:     true,
			UCOut:      dto.GetUserResponse{},
			UCErr:      uc_errors.Wrap(uc_errors.GetUserError, errors.New("db failed")),
			WantStatus: http.StatusInternalServerError,
			WantBody: map[string]interface{}{
				"error": "failed to get user",
			},
		},
		{
			Name:   "success - get current user",
			UserID: validUID,
			CallUC: true,
			UCOut: dto.GetUserResponse{
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
			WantStatus: http.StatusOK,
			WantBody: map[string]interface{}{
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
			mockGetUserUC := mocks.NewGetUserUseCase(t)
			mockUpdateUserUC := mocks.NewUpdateUserUseCase(t)
			mockUpdateUserPictureUC := mocks.NewUpdateUserPictureUseCase(t)

			if tt.CallUC {
				mockGetUserUC.On("Execute", mock.Anything, dto.GetUserRequest{
					ID: tt.UserID,
				}).Return(tt.UCOut, tt.UCErr)
			}

			var logBuffer bytes.Buffer
			logger := slog.New(slog.NewJSONHandler(&logBuffer, nil))

			handler := NewUserHandler(
				logger,
				mockGetUserUC,
				mockUpdateUserUC,
				mockUpdateUserPictureUC,
			)

			c, rec := createUserTestContext(
				http.MethodGet,
				"/api/users/me",
				nil,
				tt.UserID,
			)

			err := handler.GetMe(c)

			assert.NoError(t, err)
			assert.Equal(t, tt.WantStatus, rec.Code)

			expectedJSON, err := json.Marshal(tt.WantBody)
			assert.NoError(t, err)

			assert.JSONEq(t, string(expectedJSON), rec.Body.String())

			mockGetUserUC.AssertExpectations(t)
		})
	}
}

func TestUH_UpdateMe(t *testing.T) {
	type testCase struct {
		Name        string
		UserID      uuid.UUID
		RequestBody interface{} // <--- string/map
		CallUC      bool
		UCOut       dto.UpdateUserResponse
		UCErr       error
		WantStatus  int
		WantBody    map[string]interface{}
	}

	var (
		validUID  = uuid.New()
		testEmail = "new123user@gmail.com"
		testTime  = time.Date(2026, 1, 1, 0, 0, 0, 0, time.UTC)
	)

	var testCases = []testCase{
		{
			Name:        "failure - unauthorized (no uid)",
			UserID:      uuid.Nil,
			RequestBody: map[string]interface{}{},
			CallUC:      false,
			UCOut:       dto.UpdateUserResponse{},
			UCErr:       nil,
			WantStatus:  http.StatusUnauthorized,
			WantBody: map[string]interface{}{
				"error": "not authenticated",
			},
		},
		{
			Name:        "failure - invalid json",
			UserID:      validUID,
			RequestBody: "{invalid json",
			CallUC:      false,
			UCOut:       dto.UpdateUserResponse{},
			UCErr:       nil,
			WantStatus:  http.StatusBadRequest,
			WantBody: map[string]interface{}{
				"error": "invalid json",
			},
		},
		{
			Name:   "failure - user not found",
			UserID: validUID,
			RequestBody: map[string]interface{}{
				"email": testEmail,
			},
			CallUC:     true,
			UCOut:      dto.UpdateUserResponse{},
			UCErr:      uc_errors.UserNotFoundError,
			WantStatus: http.StatusNotFound,
			WantBody: map[string]interface{}{
				"error": "user not found",
			},
		},
		{
			Name:       "failure - internal error(get)",
			UserID:     validUID,
			CallUC:     true,
			UCOut:      dto.UpdateUserResponse{},
			UCErr:      uc_errors.Wrap(uc_errors.GetUserError, errors.New("db failed")),
			WantStatus: http.StatusInternalServerError,
			WantBody: map[string]interface{}{
				"error": "failed to get user",
			},
		},
		{
			Name:       "failure - internal error(update)",
			UserID:     validUID,
			CallUC:     true,
			UCOut:      dto.UpdateUserResponse{},
			UCErr:      uc_errors.Wrap(uc_errors.UpdateUserError, errors.New("db failed")),
			WantStatus: http.StatusInternalServerError,
			WantBody: map[string]interface{}{
				"error": "failed to update user",
			},
		},
		{
			Name:        "success - nothing to update",
			UserID:      validUID,
			RequestBody: map[string]interface{}{},
			CallUC:      true,
			UCOut: dto.UpdateUserResponse{
				ID:      validUID,
				Updated: false,
			},
			UCErr:      nil,
			WantStatus: http.StatusOK,
			WantBody: map[string]interface{}{
				"id":      validUID,
				"updated": false,
				"user": map[string]interface{}{
					"id":             uuid.Nil,
					"google_sub":     "",
					"email":          "",
					"email_verified": false,
					"created_at":     "0001-01-01T00:00:00Z",
					"updated_at":     interface{}(nil),
				},
			},
		},
		{
			Name:   "success - update current user",
			UserID: validUID,
			RequestBody: map[string]interface{}{
				"email": testEmail,
			},
			CallUC: true,
			UCOut: dto.UpdateUserResponse{
				ID:      validUID,
				Updated: true,
				User: dto.UserResponse{
					ID:            validUID,
					GoogleSub:     "a123fd",
					Email:         testEmail,
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
			WantStatus: http.StatusOK,
			WantBody: map[string]interface{}{
				"id":      validUID,
				"updated": true,
				"user": map[string]interface{}{
					"id":             validUID.String(),
					"google_sub":     "a123fd",
					"email":          testEmail,
					"email_verified": true,
					"created_at":     testTime.Format(time.RFC3339),
					"updated_at":     testTime.Format(time.RFC3339),
				},
			},
		},
	}

	for _, tt := range testCases {
		t.Run(tt.Name, func(t *testing.T) {
			mockGetUserUC := mocks.NewGetUserUseCase(t)
			mockUpdateUserUC := mocks.NewUpdateUserUseCase(t)
			mockUpdateUserPictureUC := mocks.NewUpdateUserPictureUseCase(t)

			if tt.CallUC {
				mockUpdateUserUC.On("Execute", mock.Anything, mock.MatchedBy(func(req dto.UpdateUserRequest) bool {
					return req.ID == tt.UserID
				})).Return(tt.UCOut, tt.UCErr).Once()
			}

			var logBuffer bytes.Buffer
			logger := slog.New(slog.NewJSONHandler(&logBuffer, nil))

			handler := NewUserHandler(
				logger,
				mockGetUserUC,
				mockUpdateUserUC,
				mockUpdateUserPictureUC,
			)

			c, rec := createUserTestContext(
				http.MethodPatch,
				"/api/users/me",
				tt.RequestBody,
				tt.UserID,
			)

			err := handler.UpdateMe(c)

			assert.NoError(t, err)
			assert.Equal(t, tt.WantStatus, rec.Code)

			expectedJSON, err := json.Marshal(tt.WantBody)
			assert.NoError(t, err)

			assert.JSONEq(t, string(expectedJSON), rec.Body.String())

			mockUpdateUserUC.AssertExpectations(t)
		})
	}
}

func TestUH_UpdateMePicture(t *testing.T) {
	type testCase struct {
		Name        string
		UserID      uuid.UUID
		RequestBody interface{} // <--- string/map
		CallUC      bool
		UCOut       dto.UpdateUserPictureResponse
		UCErr       error
		WantStatus  int
		WantBody    map[string]interface{}
	}

	var (
		validUID    = uuid.New()
		testPicture = "newp.jpg"
		testTime    = time.Date(2026, 1, 1, 0, 0, 0, 0, time.UTC)
	)

	var testCases = []testCase{
		{
			Name:        "failure - unauthorized (no uid)",
			UserID:      uuid.Nil,
			RequestBody: map[string]interface{}{},
			CallUC:      false,
			UCOut:       dto.UpdateUserPictureResponse{},
			UCErr:       nil,
			WantStatus:  http.StatusUnauthorized,
			WantBody: map[string]interface{}{
				"error": "not authenticated",
			},
		},
		{
			Name:        "failure - invalid json",
			UserID:      validUID,
			RequestBody: "{invalid json",
			CallUC:      false,
			UCOut:       dto.UpdateUserPictureResponse{},
			UCErr:       nil,
			WantStatus:  http.StatusBadRequest,
			WantBody: map[string]interface{}{
				"error": "invalid json",
			},
		},
		{
			Name:   "failure - user not found",
			UserID: validUID,
			RequestBody: map[string]interface{}{
				"picture": testPicture,
			},
			CallUC:     true,
			UCOut:      dto.UpdateUserPictureResponse{},
			UCErr:      uc_errors.UserNotFoundError,
			WantStatus: http.StatusNotFound,
			WantBody: map[string]interface{}{
				"error": "user not found",
			},
		},
		{
			Name:   "failure - internal error(get)",
			UserID: validUID,
			RequestBody: map[string]interface{}{
				"picture": testPicture,
			},
			CallUC:     true,
			UCOut:      dto.UpdateUserPictureResponse{},
			UCErr:      uc_errors.Wrap(uc_errors.GetUserError, errors.New("db failed")),
			WantStatus: http.StatusInternalServerError,
			WantBody: map[string]interface{}{
				"error": "failed to get user",
			},
		},
		{
			Name:   "failure - internal error(update)",
			UserID: validUID,
			RequestBody: map[string]interface{}{
				"picture": testPicture,
			},
			CallUC:     true,
			UCOut:      dto.UpdateUserPictureResponse{},
			UCErr:      uc_errors.Wrap(uc_errors.UpdateUserPictureError, errors.New("db failed")),
			WantStatus: http.StatusInternalServerError,
			WantBody: map[string]interface{}{
				"error": "failed to update user's picture",
			},
		},
		{
			Name:   "success - update current user",
			UserID: validUID,
			RequestBody: map[string]interface{}{
				"picture": testPicture,
			},
			CallUC: true,
			UCOut: dto.UpdateUserPictureResponse{
				ID:      validUID,
				Updated: true,
				User: dto.UserResponse{
					ID:            validUID,
					GoogleSub:     "a123fd",
					Email:         "",
					FullName:      nil,
					Picture:       &testPicture,
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
			WantStatus: http.StatusOK,
			WantBody: map[string]interface{}{
				"id":      validUID,
				"updated": true,
				"user": map[string]interface{}{
					"id":             validUID.String(),
					"google_sub":     "a123fd",
					"email":          "",
					"picture":        testPicture,
					"email_verified": true,
					"created_at":     testTime.Format(time.RFC3339),
					"updated_at":     testTime.Format(time.RFC3339),
				},
			},
		},
	}

	for _, tt := range testCases {
		t.Run(tt.Name, func(t *testing.T) {
			mockGetUserUC := mocks.NewGetUserUseCase(t)
			mockUpdateUserUC := mocks.NewUpdateUserUseCase(t)
			mockUpdateUserPictureUC := mocks.NewUpdateUserPictureUseCase(t)

			if tt.CallUC {
				mockUpdateUserPictureUC.On("Execute", mock.Anything, mock.MatchedBy(func(req dto.UpdateUserPictureRequest) bool {
					return req.ID == tt.UserID
				})).Return(tt.UCOut, tt.UCErr).Once()
			}

			var logBuffer bytes.Buffer
			logger := slog.New(slog.NewJSONHandler(&logBuffer, nil))

			handler := NewUserHandler(
				logger,
				mockGetUserUC,
				mockUpdateUserUC,
				mockUpdateUserPictureUC,
			)

			c, rec := createUserTestContext(
				http.MethodPut,
				"/api/users/me/picture",
				tt.RequestBody,
				tt.UserID,
			)

			err := handler.UpdateMePicture(c)

			assert.NoError(t, err)
			assert.Equal(t, tt.WantStatus, rec.Code)

			expectedJSON, err := json.Marshal(tt.WantBody)
			assert.NoError(t, err)

			assert.JSONEq(t, string(expectedJSON), rec.Body.String())

			mockUpdateUserPictureUC.AssertExpectations(t)
		})
	}
}
