package usecase_test

import (
	"context"
	"database/sql"
	"errors"
	"testing"
	"time"

	"github.com/google/uuid"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/mock"

	"YouSightSeeing/backend/internal/app/dto"
	"YouSightSeeing/backend/internal/app/uc_errors"
	"YouSightSeeing/backend/internal/app/usecase"
	"YouSightSeeing/backend/internal/domain/entity"

	"YouSightSeeing/backend/internal/domain/port/mocks"
)

func TestRefreshTokenUC_Execute(t *testing.T) {
	userID := uuid.New()
	validTokenString := "valid_refresh_token"

	validUser := &entity.User{
		ID:    userID,
		Email: "test@example.com",
	}

	validOldToken := &entity.RefreshToken{
		ID:        uuid.New(),
		UserID:    userID,
		TokenHash: "hashed_old_token",
		IsRevoked: false,
		ExpiresAt: time.Now().Add(24 * time.Hour), // Valid
	}

	expiredToken := &entity.RefreshToken{
		ID:        uuid.New(),
		UserID:    userID,
		TokenHash: "hashed_expired_token",
		IsRevoked: false,
		ExpiresAt: time.Now().Add(-24 * time.Hour), // Expired
	}

	revokedToken := &entity.RefreshToken{
		ID:        uuid.New(),
		UserID:    userID,
		TokenHash: "hashed_revoked_token",
		IsRevoked: true,
		ExpiresAt: time.Now().Add(24 * time.Hour),
	}

	tests := []struct {
		name      string
		input     dto.RefreshTokenRequest
		mockSetup func(
			u *mocks.UserRepository,
			tr *mocks.TokenRepository,
			tg *mocks.TokensGenerator,
		)
		wantErr error
	}{
		{
			name:  "Error: Empty Token",
			input: dto.RefreshTokenRequest{RefreshToken: ""},
			mockSetup: func(u *mocks.UserRepository, tr *mocks.TokenRepository, tg *mocks.TokensGenerator) {
			},
			wantErr: uc_errors.EmptyRefreshTokenError,
		},
		{
			name:  "Error: Token Not Found",
			input: dto.RefreshTokenRequest{RefreshToken: "unknown_token"},
			mockSetup: func(u *mocks.UserRepository, tr *mocks.TokenRepository, tg *mocks.TokensGenerator) {
				tr.On("GetByHash", mock.Anything, mock.Anything).Return(nil, sql.ErrNoRows)
			},
			wantErr: uc_errors.RefreshTokenNotFoundError,
		},
		{
			name:  "Error: DB Error on Get Token",
			input: dto.RefreshTokenRequest{RefreshToken: "db_error_token"},
			mockSetup: func(u *mocks.UserRepository, tr *mocks.TokenRepository, tg *mocks.TokensGenerator) {
				tr.On("GetByHash", mock.Anything, mock.Anything).Return(nil, errors.New("db error"))
			},
			wantErr: uc_errors.GetRefreshTokenByHashError,
		},
		{
			name:  "Error: Token Is Revoked",
			input: dto.RefreshTokenRequest{RefreshToken: "revoked_token"},
			mockSetup: func(u *mocks.UserRepository, tr *mocks.TokenRepository, tg *mocks.TokensGenerator) {
				tr.On("GetByHash", mock.Anything, mock.Anything).Return(revokedToken, nil)
			},
			wantErr: uc_errors.RevokedRefreshTokenError,
		},
		{
			name:  "Error: Token Is Expired (Revoke Success)",
			input: dto.RefreshTokenRequest{RefreshToken: "expired_token"},
			mockSetup: func(u *mocks.UserRepository, tr *mocks.TokenRepository, tg *mocks.TokensGenerator) {
				tr.On("GetByHash", mock.Anything, mock.Anything).Return(expiredToken, nil)
				// При истечении срока токен должен быть отозван
				tr.On("Revoke", mock.Anything, mock.Anything, "expired").Return(nil)
			},
			wantErr: uc_errors.ExpiredRefreshTokenError,
		},
		{
			name:  "Error: Token Is Expired (Revoke Fail)",
			input: dto.RefreshTokenRequest{RefreshToken: "expired_token"},
			mockSetup: func(u *mocks.UserRepository, tr *mocks.TokenRepository, tg *mocks.TokensGenerator) {
				tr.On("GetByHash", mock.Anything, mock.Anything).Return(expiredToken, nil)
				tr.On("Revoke", mock.Anything, mock.Anything, "expired").Return(errors.New("revoke fail"))
			},
			wantErr: uc_errors.RevokeRefreshTokenError,
		},
		{
			name:  "Error: User Not Found",
			input: dto.RefreshTokenRequest{RefreshToken: validTokenString},
			mockSetup: func(u *mocks.UserRepository, tr *mocks.TokenRepository, tg *mocks.TokensGenerator) {
				tr.On("GetByHash", mock.Anything, mock.Anything).Return(validOldToken, nil)
				u.On("GetByID", mock.Anything, userID).Return(nil, sql.ErrNoRows)
			},
			wantErr: uc_errors.UserNotFoundError,
		},
		{
			name:  "Error: Failed Get User",
			input: dto.RefreshTokenRequest{RefreshToken: validTokenString},
			mockSetup: func(u *mocks.UserRepository, tr *mocks.TokenRepository, tg *mocks.TokensGenerator) {
				tr.On("GetByHash", mock.Anything, mock.Anything).Return(validOldToken, nil)
				u.On("GetByID", mock.Anything, userID).Return(nil, errors.New("user not found"))
			},
			wantErr: uc_errors.GetUserError,
		},
		{
			name:  "Error: Generate Access Token Fail",
			input: dto.RefreshTokenRequest{RefreshToken: validTokenString},
			mockSetup: func(u *mocks.UserRepository, tr *mocks.TokenRepository, tg *mocks.TokensGenerator) {
				tr.On("GetByHash", mock.Anything, mock.Anything).Return(validOldToken, nil)
				u.On("GetByID", mock.Anything, userID).Return(validUser, nil)

				tg.On("GenerateAccessToken", mock.Anything, userID).Return("", errors.New("gen access fail"))
			},
			wantErr: uc_errors.GenerateAccessTokenError,
		},
		{
			name:  "Error: Rotate - Revoke Old Fail",
			input: dto.RefreshTokenRequest{RefreshToken: validTokenString},
			mockSetup: func(u *mocks.UserRepository, tr *mocks.TokenRepository, tg *mocks.TokensGenerator) {
				tr.On("GetByHash", mock.Anything, mock.Anything).Return(validOldToken, nil)
				u.On("GetByID", mock.Anything, userID).Return(validUser, nil)
				tg.On("GenerateAccessToken", mock.Anything, userID).Return("new_access_token", nil)

				// Ошибка отзыва старого при ротации
				tr.On("Revoke", mock.Anything, validOldToken.TokenHash, "rotating").Return(errors.New("revoke fail"))
			},
			wantErr: uc_errors.RevokeRefreshTokenError,
		},
		{
			name:  "Error: Rotate - Generate Refresh Fail",
			input: dto.RefreshTokenRequest{RefreshToken: validTokenString},
			mockSetup: func(u *mocks.UserRepository, tr *mocks.TokenRepository, tg *mocks.TokensGenerator) {
				tr.On("GetByHash", mock.Anything, mock.Anything).Return(validOldToken, nil)
				u.On("GetByID", mock.Anything, userID).Return(validUser, nil)
				tg.On("GenerateAccessToken", mock.Anything, userID).Return("new_access_token", nil)
				tr.On("Revoke", mock.Anything, validOldToken.TokenHash, "rotating").Return(nil)
				tg.On("GenerateRefreshToken", mock.Anything).Return("", errors.New("gen refresh fail"))
			},
			wantErr: uc_errors.GenerateRefreshTokenError,
		},
		{
			name:  "Error: Rotate - Create New Token Fail",
			input: dto.RefreshTokenRequest{RefreshToken: validTokenString},
			mockSetup: func(u *mocks.UserRepository, tr *mocks.TokenRepository, tg *mocks.TokensGenerator) {
				tr.On("GetByHash", mock.Anything, mock.Anything).Return(validOldToken, nil)
				u.On("GetByID", mock.Anything, userID).Return(validUser, nil)
				tg.On("GenerateAccessToken", mock.Anything, userID).Return("new_access_token", nil)
				tr.On("Revoke", mock.Anything, validOldToken.TokenHash, "rotating").Return(nil)
				tg.On("GenerateRefreshToken", mock.Anything).Return("new_refresh_token_string", nil)

				// Ошибка сохранения нового токена
				tr.On("Create", mock.Anything, mock.Anything).Return(errors.New("create fail"))
			},
			wantErr: uc_errors.CreateRefreshTokenError,
		},
		{
			name:  "Success: Tokens Rotated",
			input: dto.RefreshTokenRequest{RefreshToken: validTokenString},
			mockSetup: func(u *mocks.UserRepository, tr *mocks.TokenRepository, tg *mocks.TokensGenerator) {
				tr.On("GetByHash", mock.Anything, mock.Anything).Return(validOldToken, nil)
				u.On("GetByID", mock.Anything, userID).Return(validUser, nil)
				tg.On("GenerateAccessToken", mock.Anything, userID).Return("new_access_token", nil)
				tr.On("Revoke", mock.Anything, validOldToken.TokenHash, "rotating").Return(nil)
				tg.On("GenerateRefreshToken", mock.Anything).Return("new_refresh_token_string", nil)
				tr.On("Create", mock.Anything, mock.Anything).Return(nil)
			},
			wantErr: nil,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			uRepo := mocks.NewUserRepository(t)
			tRepo := mocks.NewTokenRepository(t)
			tGen := mocks.NewTokensGenerator(t)

			if tt.mockSetup != nil {
				tt.mockSetup(uRepo, tRepo, tGen)
			}

			// Инициализация usecase
			uc := usecase.NewRefreshTokenUC(
				uRepo,
				tRepo,
				tGen,
				time.Hour,
				24*time.Hour,
			)

			resp, err := uc.Execute(context.Background(), tt.input)

			if tt.wantErr != nil {
				assert.Error(t, err)
				assert.True(t, errors.Is(err, tt.wantErr), "expected error '%v' but got '%v'", tt.wantErr, err)
			} else {
				assert.NoError(t, err)
				assert.Equal(t, "new_access_token", resp.AccessToken)
				assert.Equal(t, "new_refresh_token_string", resp.RefreshToken)
				assert.Equal(t, validUser.Email, resp.User.Email)
			}
		})
	}
}
