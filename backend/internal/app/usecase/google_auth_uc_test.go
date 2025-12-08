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

	mocks "YouSightSeeing/backend/internal/domain/port/mocks"
)

func TestGoogleAuthUC_Execute(t *testing.T) {
	// Подготовка общих тестовых данных
	validToken := "valid_google_token"
	validSub := "google_12345"
	validEmail := "test@example.com"
	userID := uuid.New()

	validClaims := &entity.GoogleClaims{
		Sub:           validSub,
		Email:         validEmail,
		EmailVerified: true,
	}

	existingUser := &entity.User{
		ID:    userID,
		Email: validEmail,
	}

	// Структура тестового кейса
	type testCase struct {
		name      string
		input     dto.GoogleAuthRequest
		mockSetup func(
			u *mocks.UserRepository,
			tr *mocks.TokenRepository,
			gv *mocks.GoogleVerifier,
			tg *mocks.TokensGenerator,
		)
		wantErr error
	}

	tests := []testCase{
		{
			name:  "Error: Empty Google Token",
			input: dto.GoogleAuthRequest{GoogleToken: ""},
			mockSetup: func(u *mocks.UserRepository, tr *mocks.TokenRepository, gv *mocks.GoogleVerifier, tg *mocks.TokensGenerator) {
				// Моки не вызываются, валидация происходит до вызовов
			},
			wantErr: uc_errors.EmptyGoogleTokenError,
		},
		{
			name:  "Error: Invalid Google Token",
			input: dto.GoogleAuthRequest{GoogleToken: "invalid_token"},
			mockSetup: func(u *mocks.UserRepository, tr *mocks.TokenRepository, gv *mocks.GoogleVerifier, tg *mocks.TokensGenerator) {
				gv.On("VerifyToken", mock.Anything, "invalid_token").Return(nil, errors.New("invalid"))
			},
			wantErr: uc_errors.GoogleTokenValidationError,
		},
		{
			name:  "Error: Email Not Verified",
			input: dto.GoogleAuthRequest{GoogleToken: validToken},
			mockSetup: func(u *mocks.UserRepository, tr *mocks.TokenRepository, gv *mocks.GoogleVerifier, tg *mocks.TokensGenerator) {
				gv.On("VerifyToken", mock.Anything, validToken).Return(&entity.GoogleClaims{
					Sub: validSub, Email: validEmail, EmailVerified: false,
				}, nil)
			},
			wantErr: uc_errors.EmailNotVerifiedError,
		},
		{
			name:  "Success: New User (Registration)",
			input: dto.GoogleAuthRequest{GoogleToken: validToken},
			mockSetup: func(u *mocks.UserRepository, tr *mocks.TokenRepository, gv *mocks.GoogleVerifier, tg *mocks.TokensGenerator) {
				// 1. Успешная верификация токена
				gv.On("VerifyToken", mock.Anything, validToken).Return(validClaims, nil)

				// 2. Пользователь не найден (sql.ErrNoRows) -> сценарий регистрации
				u.On("GetByGoogleSub", mock.Anything, validSub).Return(nil, sql.ErrNoRows)

				// 3. Создание нового пользователя
				// Используем MatchedBy, чтобы проверить поля создаваемого объекта
				u.On("Create", mock.Anything, mock.MatchedBy(func(user *entity.User) bool {
					return user.Email == validEmail
				})).Return(nil)

				// 4. Генерация refresh-токена
				tg.On("GenerateRefreshToken", mock.Anything, mock.Anything).Return("refresh_token_123", nil)

				// 5. Сохранение refresh-токена
				tr.On("Create", mock.Anything, mock.Anything).Return(nil)

				// 6. Генерация access-токена
				tg.On("GenerateAccessToken", mock.Anything, mock.Anything).Return("access_token_123", nil)
			},
			wantErr: nil,
		},
		{
			name:  "Success: Existing User (Login with expired token / No token)",
			input: dto.GoogleAuthRequest{GoogleToken: validToken},
			mockSetup: func(u *mocks.UserRepository, tr *mocks.TokenRepository, gv *mocks.GoogleVerifier, tg *mocks.TokensGenerator) {
				// 1. Верификация
				gv.On("VerifyToken", mock.Anything, validToken).Return(validClaims, nil)

				// 2. Пользователь найден
				u.On("GetByGoogleSub", mock.Anything, validSub).Return(existingUser, nil)

				// 3. Активный токен не найден
				tr.On("GetByUserID", mock.Anything, userID).Return(nil, sql.ErrNoRows)

				// 4. Генерация нового refresh-токена
				tg.On("GenerateRefreshToken", mock.Anything, userID).Return("refresh_token_new", nil)

				// 5. Сохранение нового токена (без отзыва старого, так как его нет)
				tr.On("Create", mock.Anything, mock.MatchedBy(func(t *entity.RefreshToken) bool {
					return t.UserID == userID
				})).Return(nil)

				// 6. Генерация access-токена
				tg.On("GenerateAccessToken", mock.Anything, userID).Return("access_token_new", nil)
			},
			wantErr: nil,
		},
		{
			name:  "Success: Existing User (Token Rotation)",
			input: dto.GoogleAuthRequest{GoogleToken: validToken},
			mockSetup: func(u *mocks.UserRepository, tr *mocks.TokenRepository, gv *mocks.GoogleVerifier, tg *mocks.TokensGenerator) {
				// 1. Верификация
				gv.On("VerifyToken", mock.Anything, validToken).Return(validClaims, nil)

				// 2. Пользователь найден
				u.On("GetByGoogleSub", mock.Anything, validSub).Return(existingUser, nil)

				// 3. Найден старый активный токен
				oldToken := &entity.RefreshToken{
					ID:        uuid.New(),
					UserID:    userID,
					TokenHash: "old_hash_value",
					ExpiresAt: time.Now().Add(time.Hour),
				}
				tr.On("GetByUserID", mock.Anything, userID).Return(oldToken, nil)

				// 4. Отзыв старого токена (Revoke)
				tr.On("Revoke", mock.Anything, "old_hash_value", "new log in").Return(nil)

				// 5. Генерация нового токена
				tg.On("GenerateRefreshToken", mock.Anything, userID).Return("refresh_token_rotated", nil)

				// 6. Сохранение нового токена
				tr.On("Create", mock.Anything, mock.MatchedBy(func(t *entity.RefreshToken) bool {
					return t.UserID == userID
				})).Return(nil)

				// 7. Генерация access-токена
				tg.On("GenerateAccessToken", mock.Anything, userID).Return("access_token_rotated", nil)
			},
			wantErr: nil,
		},
		{
			name:  "Error: Database Fail on Create User",
			input: dto.GoogleAuthRequest{GoogleToken: validToken},
			mockSetup: func(u *mocks.UserRepository, tr *mocks.TokenRepository, gv *mocks.GoogleVerifier, tg *mocks.TokensGenerator) {
				gv.On("VerifyToken", mock.Anything, validToken).Return(validClaims, nil)
				u.On("GetByGoogleSub", mock.Anything, validSub).Return(nil, sql.ErrNoRows)

				// Ошибка БД при создании пользователя
				u.On("Create", mock.Anything, mock.Anything).Return(errors.New("db connection error"))
			},
			wantErr: uc_errors.CreateUserError,
		},
		{
			name:  "Error: Validation - Empty Email",
			input: dto.GoogleAuthRequest{GoogleToken: validToken},
			mockSetup: func(u *mocks.UserRepository, tr *mocks.TokenRepository, gv *mocks.GoogleVerifier, tg *mocks.TokensGenerator) {
				// Возвращаем Claims без Email
				gv.On("VerifyToken", mock.Anything, validToken).Return(&entity.GoogleClaims{
					Sub: validSub, Email: "", EmailVerified: true,
				}, nil)
			},
			wantErr: uc_errors.EmptyEmailError,
		},
		{
			name:  "Error: Validation - Empty Sub",
			input: dto.GoogleAuthRequest{GoogleToken: validToken},
			mockSetup: func(u *mocks.UserRepository, tr *mocks.TokenRepository, gv *mocks.GoogleVerifier, tg *mocks.TokensGenerator) {
				// Возвращаем Claims без Sub
				gv.On("VerifyToken", mock.Anything, validToken).Return(&entity.GoogleClaims{
					Sub: "", Email: validEmail, EmailVerified: true,
				}, nil)
			},
			wantErr: uc_errors.EmptyGoogleSubError,
		},
		{
			name:  "Error: New User - Generate Refresh Token Fail",
			input: dto.GoogleAuthRequest{GoogleToken: validToken},
			mockSetup: func(u *mocks.UserRepository, tr *mocks.TokenRepository, gv *mocks.GoogleVerifier, tg *mocks.TokensGenerator) {
				gv.On("VerifyToken", mock.Anything, validToken).Return(validClaims, nil)
				u.On("GetByGoogleSub", mock.Anything, validSub).Return(nil, sql.ErrNoRows)
				u.On("Create", mock.Anything, mock.Anything).Return(nil)

				// Ошибка генерации
				tg.On("GenerateRefreshToken", mock.Anything, mock.Anything).Return("", errors.New("gen fail"))
			},
			wantErr: uc_errors.GenerateRefreshTokenError,
		},
		{
			name:  "Error: New User - Save Refresh Token Fail",
			input: dto.GoogleAuthRequest{GoogleToken: validToken},
			mockSetup: func(u *mocks.UserRepository, tr *mocks.TokenRepository, gv *mocks.GoogleVerifier, tg *mocks.TokensGenerator) {
				gv.On("VerifyToken", mock.Anything, validToken).Return(validClaims, nil)
				u.On("GetByGoogleSub", mock.Anything, validSub).Return(nil, sql.ErrNoRows)
				u.On("Create", mock.Anything, mock.Anything).Return(nil)
				tg.On("GenerateRefreshToken", mock.Anything, mock.Anything).Return("ref_token", nil)

				// Ошибка сохранения в БД
				tr.On("Create", mock.Anything, mock.Anything).Return(errors.New("db fail"))
			},
			wantErr: uc_errors.CreateRefreshTokenError,
		},
		{
			name:  "Error: New User - Generate Access Token Fail",
			input: dto.GoogleAuthRequest{GoogleToken: validToken},
			mockSetup: func(u *mocks.UserRepository, tr *mocks.TokenRepository, gv *mocks.GoogleVerifier, tg *mocks.TokensGenerator) {
				gv.On("VerifyToken", mock.Anything, validToken).Return(validClaims, nil)
				u.On("GetByGoogleSub", mock.Anything, validSub).Return(nil, sql.ErrNoRows)
				u.On("Create", mock.Anything, mock.Anything).Return(nil)
				tg.On("GenerateRefreshToken", mock.Anything, mock.Anything).Return("ref_token", nil)
				tr.On("Create", mock.Anything, mock.Anything).Return(nil)

				// Ошибка генерации Access
				tg.On("GenerateAccessToken", mock.Anything, mock.Anything).Return("", errors.New("gen access fail"))
			},
			wantErr: uc_errors.GenerateAccessTokenError,
		},
		{
			name:  "Error: Existing User (No Token) - Gen Refresh Fail",
			input: dto.GoogleAuthRequest{GoogleToken: validToken},
			mockSetup: func(u *mocks.UserRepository, tr *mocks.TokenRepository, gv *mocks.GoogleVerifier, tg *mocks.TokensGenerator) {
				gv.On("VerifyToken", mock.Anything, validToken).Return(validClaims, nil)
				u.On("GetByGoogleSub", mock.Anything, validSub).Return(existingUser, nil)
				tr.On("GetByUserID", mock.Anything, userID).Return(nil, sql.ErrNoRows)

				// Ошибка тут
				tg.On("GenerateRefreshToken", mock.Anything, userID).Return("", errors.New("gen fail"))
			},
			wantErr: uc_errors.GenerateRefreshTokenError,
		},
		{
			name:  "Error: Existing User - DB Error on Get Token",
			input: dto.GoogleAuthRequest{GoogleToken: validToken},
			mockSetup: func(u *mocks.UserRepository, tr *mocks.TokenRepository, gv *mocks.GoogleVerifier, tg *mocks.TokensGenerator) {
				gv.On("VerifyToken", mock.Anything, validToken).Return(validClaims, nil)
				u.On("GetByGoogleSub", mock.Anything, validSub).Return(existingUser, nil)

				// Ошибка БД (НЕ sql.ErrNoRows, а любая другая)
				tr.On("GetByUserID", mock.Anything, userID).Return(nil, errors.New("connection broken"))
			},
			wantErr: uc_errors.GetRefreshTokenByUserIDError,
		},
		{
			name:  "Error: Existing User - Revoke Fail",
			input: dto.GoogleAuthRequest{GoogleToken: validToken},
			mockSetup: func(u *mocks.UserRepository, tr *mocks.TokenRepository, gv *mocks.GoogleVerifier, tg *mocks.TokensGenerator) {
				gv.On("VerifyToken", mock.Anything, validToken).Return(validClaims, nil)
				u.On("GetByGoogleSub", mock.Anything, validSub).Return(existingUser, nil)

				oldToken := &entity.RefreshToken{ID: uuid.New(), UserID: userID, TokenHash: "hash"}
				tr.On("GetByUserID", mock.Anything, userID).Return(oldToken, nil)

				// Ошибка отзыва токена
				tr.On("Revoke", mock.Anything, "hash", "new log in").Return(errors.New("revoke fail"))
			},
			wantErr: uc_errors.RevokeRefreshTokenError,
		},
		{
			name:  "Error: Existing User (No Token) - Generate Access Token Fail",
			input: dto.GoogleAuthRequest{GoogleToken: validToken},
			mockSetup: func(u *mocks.UserRepository, tr *mocks.TokenRepository, gv *mocks.GoogleVerifier, tg *mocks.TokensGenerator) {
				gv.On("VerifyToken", mock.Anything, validToken).Return(validClaims, nil)
				u.On("GetByGoogleSub", mock.Anything, validSub).Return(existingUser, nil)

				// 1. Токена нет (Expired)
				tr.On("GetByUserID", mock.Anything, userID).Return(nil, sql.ErrNoRows)

				// 2. Refresh создался успешно
				tg.On("GenerateRefreshToken", mock.Anything, userID).Return("ref_token", nil)
				tr.On("Create", mock.Anything, mock.Anything).Return(nil)

				// 3. А вот Access упал <--- ВОТ ТУТ МЫ ПОКРЫВАЕМ СТРОКУ
				tg.On("GenerateAccessToken", mock.Anything, userID).Return("", errors.New("access gen fail"))
			},
			wantErr: uc_errors.GenerateAccessTokenError,
		},
		{
			name:  "Error: Existing User (No Token) - Create Refresh Token Fail",
			input: dto.GoogleAuthRequest{GoogleToken: validToken},
			mockSetup: func(u *mocks.UserRepository, tr *mocks.TokenRepository, gv *mocks.GoogleVerifier, tg *mocks.TokensGenerator) {
				gv.On("VerifyToken", mock.Anything, validToken).Return(validClaims, nil)
				u.On("GetByGoogleSub", mock.Anything, validSub).Return(existingUser, nil)

				// 1. Токена нет
				tr.On("GetByUserID", mock.Anything, userID).Return(nil, sql.ErrNoRows)

				// 2. Сгенерировали успешно
				tg.On("GenerateRefreshToken", mock.Anything, userID).Return("ref_token", nil)

				// 3. Но сохранить в БД не смогли <--- ВОТ ТУТ МЫ ПОКРЫВАЕМ СТРОКУ
				tr.On("Create", mock.Anything, mock.Anything).Return(errors.New("db create fail"))
			},
			wantErr: uc_errors.CreateRefreshTokenError,
		},
		{
			name:  "Error: Existing User (Rotation) - Generate Refresh Fail",
			input: dto.GoogleAuthRequest{GoogleToken: validToken},
			mockSetup: func(u *mocks.UserRepository, tr *mocks.TokenRepository, gv *mocks.GoogleVerifier, tg *mocks.TokensGenerator) {
				gv.On("VerifyToken", mock.Anything, validToken).Return(validClaims, nil)
				u.On("GetByGoogleSub", mock.Anything, validSub).Return(existingUser, nil)

				// 1. Старый токен есть
				oldToken := &entity.RefreshToken{ID: uuid.New(), UserID: userID, TokenHash: "hash"}
				tr.On("GetByUserID", mock.Anything, userID).Return(oldToken, nil)

				// 2. Отозвали успешно
				tr.On("Revoke", mock.Anything, "hash", "new log in").Return(nil)

				// 3. А новый сгенерировать не вышло <--- ВОТ ТУТ МЫ ПОКРЫВАЕМ СТРОКУ
				tg.On("GenerateRefreshToken", mock.Anything, userID).Return("", errors.New("gen fail"))
			},
			wantErr: uc_errors.GenerateRefreshTokenError,
		},
		{
			name:  "Error: Existing User (Rotation) - Create Refresh Fail",
			input: dto.GoogleAuthRequest{GoogleToken: validToken},
			mockSetup: func(u *mocks.UserRepository, tr *mocks.TokenRepository, gv *mocks.GoogleVerifier, tg *mocks.TokensGenerator) {
				gv.On("VerifyToken", mock.Anything, validToken).Return(validClaims, nil)
				u.On("GetByGoogleSub", mock.Anything, validSub).Return(existingUser, nil)

				// 1. Старый токен есть
				oldToken := &entity.RefreshToken{ID: uuid.New(), UserID: userID, TokenHash: "hash"}
				tr.On("GetByUserID", mock.Anything, userID).Return(oldToken, nil)

				// 2. Отозвали успешно
				tr.On("Revoke", mock.Anything, "hash", "new log in").Return(nil)

				// 3. Сгенерировали успешно
				tg.On("GenerateRefreshToken", mock.Anything, userID).Return("new_ref", nil)

				// 4. Но сохранить новый не вышло <--- ВОТ ТУТ МЫ ПОКРЫВАЕМ СТРОКУ
				tr.On("Create", mock.Anything, mock.Anything).Return(errors.New("db fail"))
			},
			wantErr: uc_errors.CreateRefreshTokenError,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			// Инициализация моков, сгенерированных mockery.
			// Мы передаем 't', чтобы моки автоматически фейлили тест при неожиданных вызовах.
			uRepo := mocks.NewUserRepository(t)
			tRepo := mocks.NewTokenRepository(t)
			gVerf := mocks.NewGoogleVerifier(t)
			tGen := mocks.NewTokensGenerator(t)

			// Настройка поведения моков для конкретного теста
			if tt.mockSetup != nil {
				tt.mockSetup(uRepo, tRepo, gVerf, tGen)
			}

			// Инициализация юзкейса с внедрением зависимостей
			uc := &usecase.GoogleAuthUC{
				Users:           uRepo,
				RefreshTokens:   tRepo,
				GoogleVerf:      gVerf,
				TokensGenerator: tGen,
			}

			// Выполнение тестируемого метода
			resp, err := uc.Execute(context.Background(), tt.input)

			// Проверки (Asserts)
			if tt.wantErr != nil {
				assert.Error(t, err)
				// Проверяем, что ошибка соответствует ожидаемой (через errors.Is для обернутых ошибок)
				assert.True(t, errors.Is(err, tt.wantErr), "expected error '%v' but got '%v'", tt.wantErr, err)
			} else {
				assert.NoError(t, err)
				assert.NotEmpty(t, resp.AccessToken)
				assert.NotEmpty(t, resp.RefreshToken)

				// Дополнительная проверка: вернулся ли правильный email
				if resp.User.Email != "" {
					assert.Equal(t, validEmail, resp.User.Email)
				}
			}
		})
	}
}
