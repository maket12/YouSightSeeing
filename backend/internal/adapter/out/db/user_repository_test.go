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

type UserRepositoryTestSuite struct {
	suite.Suite
	db        *sqlx.DB
	repo      *db.UserRepository
	ctx       context.Context
	testUsers []*entity.User
}

func TestUserRepositorySuite(t *testing.T) {
	if testing.Short() {
		t.Skip("Skipping integration tests in short mode")
	}
	suite.Run(t, new(UserRepositoryTestSuite))
}

func (s *UserRepositoryTestSuite) SetupSuite() {
	dsn := "postgres://test:test@localhost:5432/testdb?sslmode=disable"

	dbx, err := db.NewPostgres(dsn)
	s.Require().NoError(err)

	s.db = dbx
	s.repo = db.NewUserRepository(dbx)
	s.ctx = context.Background()

	s.setupDatabase()
}

func (s *UserRepositoryTestSuite) TearDownSuite() {
	if s.db != nil {
		err := s.db.Close()
		s.Assert().NoError(err)
	}
}

func (s *UserRepositoryTestSuite) setupDatabase() {
	_, err := s.db.Exec("DROP TABLE IF EXISTS users")
	require.NoError(s.T(), err)

	createTableSQL := `
    CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY,
    google_sub VARCHAR(255) UNIQUE NOT NULL,
    
    email VARCHAR(255) UNIQUE NOT NULL,
    full_name VARCHAR(255),
    picture TEXT,

    first_name VARCHAR(100),
    last_name VARCHAR(100),
    email_verified BOOLEAN DEFAULT FALSE,
    google_domain VARCHAR(100),
    locale VARCHAR(10),

    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP
	);
    `

	_, err = s.db.Exec(createTableSQL)
	require.NoError(s.T(), err)
}

func (s *UserRepositoryTestSuite) SetupTest() {
	_, err := s.db.Exec("TRUNCATE users RESTART IDENTITY CASCADE")
	require.NoError(s.T(), err)

	var (
		firstFullName  = "Vladimir Ziabkin"
		secondFullName = "ShiShi"
		now            = time.Now()
	)

	s.testUsers = []*entity.User{
		{
			ID:        uuid.New(),
			GoogleSub: "google-sub-1",
			Email:     "test1@example.com",
			FullName:  &firstFullName,
			CreatedAt: now,
			UpdatedAt: &now,
		},
		{
			ID:        uuid.New(),
			GoogleSub: "google-sub-2",
			Email:     "test2@example.com",
			FullName:  &secondFullName,
			CreatedAt: now,
			UpdatedAt: &now,
		},
	}
}

func (s *UserRepositoryTestSuite) TestCreate() {
	user := s.testUsers[0]

	err := s.repo.Create(s.ctx, user)
	assert.NoError(s.T(), err)

	// Проверка на создание записи
	var count int
	err = s.db.Get(&count, "SELECT COUNT(*) FROM users WHERE id = $1", user.ID)
	assert.NoError(s.T(), err)
	assert.Equal(s.T(), 1, count)
}

func (s *UserRepositoryTestSuite) TestGetByID() {
	user := s.testUsers[0]

	err := s.repo.Create(s.ctx, user)
	assert.NoError(s.T(), err)

	fetchedUser, err := s.repo.GetByID(s.ctx, user.ID)
	assert.NoError(s.T(), err)
	assert.Equal(s.T(), user.GoogleSub, fetchedUser.GoogleSub)
	assert.Equal(s.T(), user.Email, fetchedUser.Email)
}

func (s *UserRepositoryTestSuite) TestGetByID_NotFound() {
	nonExistingID := uuid.New()

	user, err := s.repo.GetByID(s.ctx, nonExistingID)
	assert.Nil(s.T(), user)
	assert.Error(s.T(), err)
	assert.Equal(s.T(), sql.ErrNoRows, err)
}

func (s *UserRepositoryTestSuite) TestGetBySub() {
	user := s.testUsers[0]

	err := s.repo.Create(s.ctx, user)
	assert.NoError(s.T(), err)

	fetchedUser, err := s.repo.GetByGoogleSub(s.ctx, user.GoogleSub)
	assert.NoError(s.T(), err)
	assert.Equal(s.T(), user.ID, fetchedUser.ID)
	assert.Equal(s.T(), user.Email, fetchedUser.Email)
}

func (s *UserRepositoryTestSuite) TestGetBySub_NotFound() {
	nonExistingSub := "non-existing-sub"

	user, err := s.repo.GetByGoogleSub(s.ctx, nonExistingSub)
	assert.Nil(s.T(), user)
	assert.Error(s.T(), err)
	assert.Equal(s.T(), sql.ErrNoRows, err)
}

func (s *UserRepositoryTestSuite) TestUpdate() {
	user := s.testUsers[0]

	err := s.repo.Create(s.ctx, user)
	assert.NoError(s.T(), err)

	user.Email = "new-email@example.com"

	err = s.repo.Update(s.ctx, user)
	assert.NoError(s.T(), err)

	// Проверяем, обновились ли данные
	updatedUser, err := s.repo.GetByID(s.ctx, user.ID)
	assert.NoError(s.T(), err)
	assert.Equal(s.T(), "new-email@example.com", updatedUser.Email)
}

func (s *UserRepositoryTestSuite) TestUpdate_NotFound() {
	user := s.testUsers[0]

	err := s.repo.Update(s.ctx, user)
	assert.Error(s.T(), err)
	assert.Equal(s.T(), sql.ErrNoRows, err)
}

func (s *UserRepositoryTestSuite) TestDelete() {
	user := s.testUsers[0]

	err := s.repo.Create(s.ctx, user)
	assert.NoError(s.T(), err)

	err = s.repo.Delete(s.ctx, user.ID)
	assert.NoError(s.T(), err)

	// Проверяем наличие в БД (должен вернуть ошибку)
	existingUser, err := s.repo.GetByID(s.ctx, user.ID)
	assert.Nil(s.T(), existingUser)
	assert.Error(s.T(), err)
	assert.Equal(s.T(), sql.ErrNoRows, err)
}

func (s *UserRepositoryTestSuite) TestDelete_NotFound() {
	user := s.testUsers[0]

	err := s.repo.Delete(s.ctx, user.ID)
	assert.Error(s.T(), err)
	assert.Equal(s.T(), sql.ErrNoRows, err)
}

func (s *UserRepositoryTestSuite) TestGetList() {
	for _, user := range s.testUsers {
		err := s.repo.Create(s.ctx, user)
		assert.NoError(s.T(), err)
	}

	// Получаем список только что созданных пользователей
	users, err := s.repo.GetList(s.ctx, 10, 0)
	assert.NoError(s.T(), err)
	assert.Len(s.T(), users, 2)

	// Проверяем пагинацию (limit)
	users, err = s.repo.GetList(s.ctx, 1, 0)
	assert.NoError(s.T(), err)
	assert.Len(s.T(), users, 1)
}
