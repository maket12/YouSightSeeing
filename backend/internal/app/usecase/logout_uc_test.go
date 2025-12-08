package usecase_test

import (
	"context"
	"errors"
	"testing"

	"github.com/google/uuid"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/mock"

	"YouSightSeeing/backend/internal/app/dto"
	"YouSightSeeing/backend/internal/app/uc_errors"
	"YouSightSeeing/backend/internal/app/usecase"
	"YouSightSeeing/backend/internal/domain/entity"

	mocks "YouSightSeeing/backend/internal/domain/port/mocks"
)

func TestLogoutUC_Execute(t *testing.T) {
	// Подготовка тестовых данных
	validRefreshToken := "raw_refresh_token_123"
	userID := uuid.New()

	tests := []struct {
		name      string
		input     dto.LogoutRequest
		mockSetup func(tr *mocks.TokenRepository)
		wantErr   error
		wantResp  *dto.LogoutResponse
	}{
		{
			name:  "Error: Empty Refresh Token",
			input: dto.LogoutRequest{RefreshToken: ""},
			mockSetup: func(tr *mocks.TokenRepository) {
				// Моки не вызываются, валидация происходит в самом начале
			},
			wantErr:  uc_errors.EmptyRefreshTokenError,
			wantResp: nil,
		},
		{
			name:  "Success: User Logged Out",
			input: dto.LogoutRequest{RefreshToken: validRefreshToken},
			mockSetup: func(tr *mocks.TokenRepository) {
				// 1. Получить токен из БД
				tr.On("GetByHash", mock.Anything, mock.Anything).Return(&entity.RefreshToken{
					UserID: userID,
				}, nil)

				// 2. Отозвать токен
				tr.On("Revoke", mock.Anything, mock.Anything, "logout").Return(nil)
			},
			wantErr: nil,
			wantResp: &dto.LogoutResponse{
				UserID: userID,
				Logout: true,
			},
		},
		{
			name:  "Error: Token Not Found",
			input: dto.LogoutRequest{RefreshToken: validRefreshToken},
			mockSetup: func(tr *mocks.TokenRepository) {
				// GetByHash возвращает ошибку (токен не найден или БД ошибка)
				tr.On("GetByHash", mock.Anything, mock.Anything).Return(nil, errors.New("token not found"))
			},
			wantErr:  uc_errors.GetRefreshTokenByHashError,
			wantResp: nil,
		},
		{
			name:  "Error: Revoke Failed",
			input: dto.LogoutRequest{RefreshToken: validRefreshToken},
			mockSetup: func(tr *mocks.TokenRepository) {
				// 1. Токен найден успешно
				tr.On("GetByHash", mock.Anything, mock.Anything).Return(&entity.RefreshToken{
					UserID: userID,
				}, nil)

				// 2. Но отзыв упал
				tr.On("Revoke", mock.Anything, mock.Anything, "logout").Return(errors.New("db revoke error"))
			},
			wantErr:  uc_errors.RevokeRefreshTokenError,
			wantResp: nil,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			// Инициализация мока
			tokenRepo := mocks.NewTokenRepository(t)

			// Настройка поведения мока
			if tt.mockSetup != nil {
				tt.mockSetup(tokenRepo)
			}

			// Инициализация юзкейса
			uc := usecase.NewLogoutUC(tokenRepo)

			// Выполнение
			resp, err := uc.Execute(context.Background(), tt.input)

			// Проверки
			if tt.wantErr != nil {
				assert.Error(t, err)
				assert.True(t, errors.Is(err, tt.wantErr), "expected error '%v' but got '%v'", tt.wantErr, err)
			} else {
				assert.NoError(t, err)
				assert.Equal(t, tt.wantResp.Logout, resp.Logout)
				assert.Equal(t, tt.wantResp.UserID, resp.UserID)
			}

			// Проверить, что все моки были вызваны как ожидалось
			tokenRepo.AssertExpectations(t)
		})
	}
}
