//go:build integration
// +build integration

package db_test

import (
	"YouSightSeeing/backend/internal/adapter/out/db"
	"YouSightSeeing/backend/internal/domain/entity"
	"context"
	"database/sql"
	"testing"
	"time"

	"github.com/google/uuid"
	"github.com/jmoiron/sqlx"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"github.com/stretchr/testify/suite"
)

type RTokenRepositoryTestSuite struct {
	suite.Suite
	db         *sqlx.DB
	repo       *db.RefreshTokenRepository
	ctx        context.Context
	testTokens []*entity.RefreshToken
}

func TestRTokenRepositorySuite(t *testing.T) {
	if testing.Short() {
		t.Skip("Skipping integration tests in short mode")
	}
	suite.Run(t, new(RTokenRepositoryTestSuite))
}

func (s *RTokenRepositoryTestSuite) SetupSuite() {
	dsn := "postgres://test:test@localhost:5432/testdb?sslmode=disable"

	dbx, err := db.NewPostgres(dsn)
	s.Require().NoError(err)

	s.db = dbx
	s.repo = db.NewRefreshTokensRepository(dbx)
	s.ctx = context.Background()

	s.setupDatabase()
}

func (s *RTokenRepositoryTestSuite) TearDownSuite() {
	if s.db != nil {
		err := s.db.Close()
		s.Assert().NoError(err)
	}
}

func (s *RTokenRepositoryTestSuite) setupDatabase() {
	_, err := s.db.Exec("DROP TABLE IF EXISTS refresh_tokens")
	require.NoError(s.T(), err)

	createTableSQL := `
    CREATE TABLE IF NOT EXISTS refresh_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    token_hash VARCHAR(255) UNIQUE NOT NULL,

    device_info TEXT,
    ip_address INET,
    user_agent TEXT,

    is_revoked BOOLEAN DEFAULT FALSE,
    revoked_at TIMESTAMPTZ,
    revoked_reason TEXT,

    issued_at TIMESTAMPTZ DEFAULT NOW(),
    expires_at TIMESTAMPTZ NOT NULL
	);
    `

	_, err = s.db.Exec(createTableSQL)
	require.NoError(s.T(), err)
}

func (s *RTokenRepositoryTestSuite) SetupTest() {
	_, err := s.db.Exec("TRUNCATE refresh_tokens RESTART IDENTITY CASCADE")
	require.NoError(s.T(), err)

	var (
		uID       = uuid.New()
		now       = time.Now()
		expiresAt = now.Add(time.Hour * 24)
	)

	s.testTokens = []*entity.RefreshToken{
		{
			ID:        uuid.New(),
			UserID:    uID,
			TokenHash: "hashed-token-1",
			IssuedAt:  now,
			ExpiresAt: expiresAt,
		},
		{
			ID:        uuid.New(),
			UserID:    uuid.New(),
			TokenHash: "hashed-token-2",
			IssuedAt:  now,
			ExpiresAt: expiresAt,
		},
		{
			ID:        uuid.New(),
			UserID:    uID,
			TokenHash: "hashed-token-3",
			IssuedAt:  now,
			ExpiresAt: now.Add(-1 * time.Hour),
		},
	}
}

func (s *RTokenRepositoryTestSuite) TestCreate() {
	token := s.testTokens[0]

	err := s.repo.Create(s.ctx, token)
	assert.NoError(s.T(), err)

	// Проверка на создание записи
	var count int
	err = s.db.Get(&count, "SELECT COUNT(*) FROM refresh_tokens WHERE id = $1", token.ID)
	assert.NoError(s.T(), err)
	assert.Equal(s.T(), 1, count)
}

func (s *RTokenRepositoryTestSuite) TestGetByHash() {
	token := s.testTokens[0]

	err := s.repo.Create(s.ctx, token)
	assert.NoError(s.T(), err)

	fetchedToken, err := s.repo.GetByHash(s.ctx, token.TokenHash)
	assert.NoError(s.T(), err)
	assert.Equal(s.T(), token.ID, fetchedToken.ID)
	assert.Equal(s.T(), token.UserID, fetchedToken.UserID)
}

func (s *RTokenRepositoryTestSuite) TestGetByHash_NotFound() {
	nonExistingHash := "non-existing-hash"

	user, err := s.repo.GetByHash(s.ctx, nonExistingHash)
	assert.Nil(s.T(), user)
	assert.Error(s.T(), err)
	assert.Equal(s.T(), sql.ErrNoRows, err)
}

func (s *RTokenRepositoryTestSuite) TestGetByID() {
	token := s.testTokens[0]

	err := s.repo.Create(s.ctx, token)
	assert.NoError(s.T(), err)

	fetchedToken, err := s.repo.GetByID(s.ctx, token.ID)
	assert.NoError(s.T(), err)
	assert.Equal(s.T(), token.TokenHash, fetchedToken.TokenHash)
	assert.Equal(s.T(), token.UserID, fetchedToken.UserID)
}

func (s *RTokenRepositoryTestSuite) TestGetByID_NotFound() {
	nonExistingID := uuid.New()

	user, err := s.repo.GetByID(s.ctx, nonExistingID)
	assert.Nil(s.T(), user)
	assert.Error(s.T(), err)
	assert.Equal(s.T(), sql.ErrNoRows, err)
}

func (s *RTokenRepositoryTestSuite) TestGetByUserID() {
	token := s.testTokens[0]

	err := s.repo.Create(s.ctx, token)
	assert.NoError(s.T(), err)

	fetchedToken, err := s.repo.GetByUserID(s.ctx, token.UserID)
	assert.NoError(s.T(), err)
	assert.Equal(s.T(), token.TokenHash, fetchedToken.TokenHash)
	assert.Equal(s.T(), token.ID, fetchedToken.ID)
}

func (s *RTokenRepositoryTestSuite) TestGetByUserID_NotFound() {
	nonExistingID := uuid.New()

	user, err := s.repo.GetByUserID(s.ctx, nonExistingID)
	assert.Nil(s.T(), user)
	assert.Error(s.T(), err)
	assert.Equal(s.T(), sql.ErrNoRows, err)
}

func (s *RTokenRepositoryTestSuite) TestRevoke() {
	token := s.testTokens[0]

	// Создаем токен
	err := s.repo.Create(s.ctx, token)
	assert.NoError(s.T(), err)

	// Отзываем токен
	reason := "user logged out"
	err = s.repo.Revoke(s.ctx, token.TokenHash, reason)
	assert.NoError(s.T(), err)

	// Проверяем что токен отозван
	fetchedToken, err := s.repo.GetByHash(s.ctx, token.TokenHash)
	assert.NoError(s.T(), err)
	assert.True(s.T(), fetchedToken.IsRevoked)
	assert.NotNil(s.T(), fetchedToken.RevokedAt)
	assert.Equal(s.T(), reason, *fetchedToken.RevokedReason)
}

func (s *RTokenRepositoryTestSuite) TestRevoke_NotFound() {
	nonExistingHash := "non-existing-hash"
	reason := "test reason"

	err := s.repo.Revoke(s.ctx, nonExistingHash, reason)
	assert.Error(s.T(), err)
	assert.Equal(s.T(), sql.ErrNoRows, err)
}

func (s *RTokenRepositoryTestSuite) TestRevokeByID() {
	token := s.testTokens[0]

	// Создаем токен
	err := s.repo.Create(s.ctx, token)
	assert.NoError(s.T(), err)

	// Отзываем токен по ID
	reason := "suspicious activity"
	err = s.repo.RevokeByID(s.ctx, token.ID, reason)
	assert.NoError(s.T(), err)

	// Проверяем что токен отозван
	fetchedToken, err := s.repo.GetByID(s.ctx, token.ID)
	assert.NoError(s.T(), err)
	assert.True(s.T(), fetchedToken.IsRevoked)
	assert.NotNil(s.T(), fetchedToken.RevokedAt)
	assert.Equal(s.T(), reason, *fetchedToken.RevokedReason)
}

func (s *RTokenRepositoryTestSuite) TestRevokeByID_NotFound() {
	nonExistingID := uuid.New()
	reason := "test reason"

	err := s.repo.RevokeByID(s.ctx, nonExistingID, reason)
	assert.Error(s.T(), err)
	assert.Equal(s.T(), sql.ErrNoRows, err)
}

func (s *RTokenRepositoryTestSuite) TestDeleteExpired() {
	// Создаем 2 токена: один активный, один истекший
	activeToken := s.testTokens[0]  // expires через 24 часа
	expiredToken := s.testTokens[2] // уже истекший (expiresAt = now - 1 hour)

	err := s.repo.Create(s.ctx, activeToken)
	assert.NoError(s.T(), err)
	err = s.repo.Create(s.ctx, expiredToken)
	assert.NoError(s.T(), err)

	// Проверяем что оба токена созданы
	var countBefore int
	err = s.db.Get(&countBefore, "SELECT COUNT(*) FROM refresh_tokens")
	assert.NoError(s.T(), err)
	assert.Equal(s.T(), 2, countBefore)

	// Удаляем истекшие токены
	err = s.repo.DeleteExpired(s.ctx)
	assert.NoError(s.T(), err)

	// Проверяем что истекший токен удален, а активный остался
	var countAfter int
	err = s.db.Get(&countAfter, "SELECT COUNT(*) FROM refresh_tokens")
	assert.NoError(s.T(), err)
	assert.Equal(s.T(), 1, countAfter)

	// Проверяем что остался именно активный токен
	var remainingTokenHash string
	err = s.db.Get(&remainingTokenHash, "SELECT token_hash FROM refresh_tokens")
	assert.NoError(s.T(), err)
	assert.Equal(s.T(), activeToken.TokenHash, remainingTokenHash)
}

func (s *RTokenRepositoryTestSuite) TestDeleteRevoked() {
	token1 := s.testTokens[0]
	token2 := s.testTokens[1]

	// Создаем оба токена
	err := s.repo.Create(s.ctx, token1)
	assert.NoError(s.T(), err)
	err = s.repo.Create(s.ctx, token2)
	assert.NoError(s.T(), err)

	// Отзываем первый токен
	err = s.repo.Revoke(s.ctx, token1.TokenHash, "test")
	assert.NoError(s.T(), err)

	// Ждем секунду чтобы revoked_at был в прошлом
	time.Sleep(1 * time.Second)

	// Удаляем отозванные токены старше 1 секунды
	olderThan := time.Now().Add(-500 * time.Millisecond) // 0.5 секунды назад
	err = s.repo.DeleteRevoked(s.ctx, &olderThan)
	assert.NoError(s.T(), err)

	// Проверяем что отозванный токен удален
	var count int
	err = s.db.Get(&count, "SELECT COUNT(*) FROM refresh_tokens")
	assert.NoError(s.T(), err)
	assert.Equal(s.T(), 1, count)

	// Проверяем что остался неотозванный токен
	var remainingTokenHash string
	err = s.db.Get(&remainingTokenHash, "SELECT token_hash FROM refresh_tokens")
	assert.NoError(s.T(), err)
	assert.Equal(s.T(), token2.TokenHash, remainingTokenHash)
}

func (s *RTokenRepositoryTestSuite) TestDeleteRevoked_WithoutTimeFilter() {
	token1 := s.testTokens[0]
	token2 := s.testTokens[1]

	// Создаем оба токена
	err := s.repo.Create(s.ctx, token1)
	assert.NoError(s.T(), err)
	err = s.repo.Create(s.ctx, token2)
	assert.NoError(s.T(), err)

	// Отзываем оба токена
	err = s.repo.Revoke(s.ctx, token1.TokenHash, "test1")
	assert.NoError(s.T(), err)
	err = s.repo.Revoke(s.ctx, token2.TokenHash, "test2")
	assert.NoError(s.T(), err)

	// Удаляем отозванные токены без фильтра времени (olderThan = nil)
	err = s.repo.DeleteRevoked(s.ctx, nil)
	assert.NoError(s.T(), err)

	// Проверяем что все отозванные токены удалены
	var count int
	err = s.db.Get(&count, "SELECT COUNT(*) FROM refresh_tokens")
	assert.NoError(s.T(), err)
	assert.Equal(s.T(), 0, count)
}

func (s *RTokenRepositoryTestSuite) TestGetList() {
	userID := uuid.New()

	tokens := []*entity.RefreshToken{
		{
			ID:        uuid.New(),
			UserID:    userID,
			TokenHash: "hash-1",
			IssuedAt:  time.Now(),
			ExpiresAt: time.Now().Add(24 * time.Hour),
		},
		{
			ID:        uuid.New(),
			UserID:    userID,
			TokenHash: "hash-2",
			IssuedAt:  time.Now(),
			ExpiresAt: time.Now().Add(24 * time.Hour),
		},
		{
			ID:        uuid.New(),
			UserID:    uuid.New(), // Другой пользователь
			TokenHash: "hash-3",
			IssuedAt:  time.Now(),
			ExpiresAt: time.Now().Add(24 * time.Hour),
		},
	}

	for _, token := range tokens {
		err := s.repo.Create(s.ctx, token)
		assert.NoError(s.T(), err)
	}

	// Получаем список токенов для первого пользователя
	userTokens, err := s.repo.GetList(s.ctx, userID)
	assert.NoError(s.T(), err)
	assert.Len(s.T(), userTokens, 2)

	// Проверяем что токены правильные
	tokenHashes := []string{userTokens[0].TokenHash, userTokens[1].TokenHash}
	assert.Contains(s.T(), tokenHashes, "hash-1")
	assert.Contains(s.T(), tokenHashes, "hash-2")
	assert.NotContains(s.T(), tokenHashes, "hash-3")
}

func (s *RTokenRepositoryTestSuite) TestGetList_Empty() {
	userID := uuid.New()

	tokens, err := s.repo.GetList(s.ctx, userID)
	assert.NoError(s.T(), err)
	assert.Empty(s.T(), tokens)
}

func (s *RTokenRepositoryTestSuite) TestGetListActive() {
	userID := uuid.New()

	now := time.Now()

	activeToken := &entity.RefreshToken{
		ID:        uuid.New(),
		UserID:    userID,
		TokenHash: "active-hash",
		IssuedAt:  now,
		ExpiresAt: now.Add(24 * time.Hour),
	}

	revokedToken := &entity.RefreshToken{
		ID:        uuid.New(),
		UserID:    userID,
		TokenHash: "revoked-hash",
		IssuedAt:  now,
		ExpiresAt: now.Add(24 * time.Hour),
	}

	expiredToken := &entity.RefreshToken{
		ID:        uuid.New(),
		UserID:    userID,
		TokenHash: "expired-hash",
		IssuedAt:  now,
		ExpiresAt: now.Add(-1 * time.Hour), // уже истек
	}

	err := s.repo.Create(s.ctx, activeToken)
	s.NoError(err)
	err = s.repo.Create(s.ctx, revokedToken)
	s.NoError(err)
	err = s.repo.Create(s.ctx, expiredToken)
	s.NoError(err)

	err = s.repo.Revoke(s.ctx, revokedToken.TokenHash, "test")
	s.NoError(err)

	activeTokens, err := s.repo.GetListActive(s.ctx, userID, true)
	s.NoError(err)

	s.Len(activeTokens, 1)
	s.Equal("active-hash", activeTokens[0].TokenHash)

	inactiveTokens, err := s.repo.GetListActive(s.ctx, userID, false)
	s.NoError(err)

	s.Len(inactiveTokens, 2)

	tokenHashes := []string{inactiveTokens[0].TokenHash, inactiveTokens[1].TokenHash}
	s.Contains(tokenHashes, "revoked-hash")
	s.Contains(tokenHashes, "expired-hash")
}

func (s *RTokenRepositoryTestSuite) TestIsValid() {
	validToken := &entity.RefreshToken{
		ID:        uuid.New(),
		UserID:    uuid.New(),
		TokenHash: "valid-hash",
		IssuedAt:  time.Now(),
		ExpiresAt: time.Now().Add(24 * time.Hour),
	}

	revokedToken := &entity.RefreshToken{
		ID:        uuid.New(),
		UserID:    uuid.New(),
		TokenHash: "revoked-hash",
		IssuedAt:  time.Now(),
		ExpiresAt: time.Now().Add(24 * time.Hour),
	}

	expiredToken := &entity.RefreshToken{
		ID:        uuid.New(),
		UserID:    uuid.New(),
		TokenHash: "expired-hash",
		IssuedAt:  time.Now(),
		ExpiresAt: time.Now().Add(-1 * time.Hour),
	}

	err := s.repo.Create(s.ctx, validToken)
	assert.NoError(s.T(), err)
	err = s.repo.Create(s.ctx, revokedToken)
	assert.NoError(s.T(), err)
	err = s.repo.Create(s.ctx, expiredToken)
	assert.NoError(s.T(), err)

	err = s.repo.Revoke(s.ctx, revokedToken.TokenHash, "test")
	assert.NoError(s.T(), err)

	isValid, err := s.repo.IsValid(s.ctx, validToken.TokenHash)
	assert.NoError(s.T(), err)
	assert.True(s.T(), isValid)

	isValid, err = s.repo.IsValid(s.ctx, revokedToken.TokenHash)
	assert.NoError(s.T(), err)
	assert.False(s.T(), isValid)

	isValid, err = s.repo.IsValid(s.ctx, expiredToken.TokenHash)
	assert.NoError(s.T(), err)
	assert.False(s.T(), isValid)

	// Проверяем несуществующий токен
	isValid, err = s.repo.IsValid(s.ctx, "non-existing-hash")
	assert.NoError(s.T(), err)
	assert.False(s.T(), isValid)
}

func (s *RTokenRepositoryTestSuite) TestGetListActive_Empty() {
	userID := uuid.New()

	tokens, err := s.repo.GetListActive(s.ctx, userID, true)
	assert.NoError(s.T(), err)
	assert.Empty(s.T(), tokens)
}

func (s *RTokenRepositoryTestSuite) TestComplexScenario() {
	// Комплексный тест: создание, отзыв, проверка валидности, удаление
	userID := uuid.New()

	// 1. Создаем токен
	token := &entity.RefreshToken{
		ID:        uuid.New(),
		UserID:    userID,
		TokenHash: "complex-test-hash",
		IssuedAt:  time.Now(),
		ExpiresAt: time.Now().Add(1 * time.Hour),
	}

	err := s.repo.Create(s.ctx, token)
	s.NoError(err)

	// 2. Проверяем что токен валиден
	isValid, err := s.repo.IsValid(s.ctx, token.TokenHash)
	s.NoError(err)
	s.True(isValid)

	// 3. Отзываем токен
	err = s.repo.Revoke(s.ctx, token.TokenHash, "complex test")
	s.NoError(err)

	// 4. Проверяем что токен не валиден после отзыва
	isValid, err = s.repo.IsValid(s.ctx, token.TokenHash)
	s.NoError(err)
	s.False(isValid)

	// 5. Проверяем что токен в списке неактивных
	inactiveTokens, err := s.repo.GetListActive(s.ctx, userID, false)
	s.NoError(err)
	s.Len(inactiveTokens, 1)
	s.Equal(token.TokenHash, inactiveTokens[0].TokenHash)

	// 6. Удаляем отозванные токены (старше 1 секунды)
	// Ждем 2 секунды чтобы revoked_at был достаточно в прошлом
	time.Sleep(2 * time.Second)
	olderThan := time.Now().Add(-1 * time.Second)
	err = s.repo.DeleteRevoked(s.ctx, &olderThan)
	s.NoError(err)

	// 7. Проверяем что токен удален
	// GetByHash должен вернуть sql.ErrNoRows
	_, err = s.repo.GetByHash(s.ctx, token.TokenHash)
	s.Error(err)
	s.Equal(sql.ErrNoRows, err)
}
