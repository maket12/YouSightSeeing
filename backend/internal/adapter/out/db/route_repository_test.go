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
	"github.com/lib/pq"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"github.com/stretchr/testify/suite"
)

type RouteRepositoryTestSuite struct {
	suite.Suite
	db         *sqlx.DB
	repo       *db.RouteRepository
	ctx        context.Context
	testUser   *entity.User
	testRoutes []*entity.Route
}

func TestRouteRepositorySuite(t *testing.T) {
	if testing.Short() {
		t.Skip("Skipping integration tests in short mode")
	}
	suite.Run(t, new(RouteRepositoryTestSuite))
}

func (s *RouteRepositoryTestSuite) SetupSuite() {
	dsn := "postgres://test:test@localhost:5432/testdb?sslmode=disable"

	dbx, err := sqlx.Connect("postgres", dsn)
	s.Require().NoError(err)

	s.db = dbx
	s.repo = db.NewRouteRepository(dbx)
	s.ctx = context.Background()

	s.setupDatabase()
}

func (s *RouteRepositoryTestSuite) TearDownSuite() {
	if s.db != nil {
		_ = s.db.Close()
	}
}

func (s *RouteRepositoryTestSuite) setupDatabase() {
	_, err := s.db.Exec("DROP TABLE IF EXISTS routes")
	require.NoError(s.T(), err)
	_, err = s.db.Exec("DROP TABLE IF EXISTS users")
	require.NoError(s.T(), err)

	createUserTableSQL := `
    CREATE TABLE IF NOT EXISTS users (
        id UUID PRIMARY KEY,
        google_sub VARCHAR(255) UNIQUE NOT NULL,
        email VARCHAR(255) UNIQUE NOT NULL,
        created_at TIMESTAMP DEFAULT NOW()
    );`

	createRouteTableSQL := `
    CREATE TABLE routes (
        id UUID PRIMARY KEY,
        user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
        title VARCHAR(64) NOT NULL,
        start_latitude DOUBLE PRECISION NOT NULL,
        start_longitude DOUBLE PRECISION NOT NULL,
        distance INT NOT NULL DEFAULT 0,
        duration INT NOT NULL DEFAULT 0,
        categories TEXT[] NOT NULL,
        max_places INT NOT NULL,
        include_food BOOLEAN NOT NULL DEFAULT FALSE,
        is_public BOOLEAN NOT NULL DEFAULT FALSE,
        share_code VARCHAR(32) UNIQUE,
        created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
        updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
    );`

	_, err = s.db.Exec(createUserTableSQL)
	require.NoError(s.T(), err)
	_, err = s.db.Exec(createRouteTableSQL)
	require.NoError(s.T(), err)
}

func (s *RouteRepositoryTestSuite) SetupTest() {
	_, err := s.db.Exec("TRUNCATE users RESTART IDENTITY CASCADE")
	require.NoError(s.T(), err)

	s.testUser = &entity.User{
		ID:        uuid.New(),
		GoogleSub: "test-sub",
		Email:     "owner@example.com",
	}
	_, err = s.db.NamedExec("INSERT INTO users (id, google_sub, email) VALUES (:id, :google_sub, :email)", s.testUser)
	require.NoError(s.T(), err)

	now := time.Now().UTC()
	shareCode := "abc-123"

	s.testRoutes = []*entity.Route{
		{
			ID:             uuid.New(),
			UserID:         s.testUser.ID,
			Title:          "Morning Walk",
			StartLatitude:  55.7558,
			StartLongitude: 37.6173,
			Distance:       5000,
			Duration:       3600,
			Categories:     pq.StringArray{"park", "nature"},
			MaxPlaces:      5,
			IncludeFood:    false,
			IsPublic:       true,
			ShareCode:      &shareCode,
			CreatedAt:      now,
			UpdatedAt:      now,
		},
	}
}

func (s *RouteRepositoryTestSuite) TestCreate() {
	route := s.testRoutes[0]

	err := s.repo.Create(s.ctx, route)
	assert.NoError(s.T(), err)

	var count int
	err = s.db.Get(&count, "SELECT COUNT(*) FROM routes WHERE id = $1", route.ID)
	assert.NoError(s.T(), err)
	assert.Equal(s.T(), 1, count)
}

func (s *RouteRepositoryTestSuite) TestGetByID() {
	route := s.testRoutes[0]
	_ = s.repo.Create(s.ctx, route)

	fetched, err := s.repo.GetByID(s.ctx, route.ID)
	assert.NoError(s.T(), err)
	assert.Equal(s.T(), route.Title, fetched.Title)
	assert.Equal(s.T(), route.Distance, fetched.Distance)
	assert.Equal(s.T(), route.Categories, fetched.Categories)
}

func (s *RouteRepositoryTestSuite) TestUpdate() {
	route := s.testRoutes[0]
	_ = s.repo.Create(s.ctx, route)

	route.Title = "Updated Title"
	route.IsPublic = false

	err := s.repo.Update(s.ctx, route)
	assert.NoError(s.T(), err)

	updated, err := s.repo.GetByID(s.ctx, route.ID)
	assert.NoError(s.T(), err)
	assert.Equal(s.T(), "Updated Title", updated.Title)
	assert.False(s.T(), updated.IsPublic)
}

func (s *RouteRepositoryTestSuite) TestDelete() {
	route := s.testRoutes[0]
	_ = s.repo.Create(s.ctx, route)

	err := s.repo.Delete(s.ctx, route.ID)
	assert.NoError(s.T(), err)

	_, err = s.repo.GetByID(s.ctx, route.ID)
	assert.ErrorIs(s.T(), err, sql.ErrNoRows)
}

func (s *RouteRepositoryTestSuite) TestGetListByUserID() {
	route := s.testRoutes[0]
	_ = s.repo.Create(s.ctx, route)

	routes, err := s.repo.GetListByUserID(s.ctx, s.testUser.ID, 10, 0)
	assert.NoError(s.T(), err)
	assert.Len(s.T(), routes, 1)
	assert.Equal(s.T(), route.ID, routes[0].ID)
}

func (s *RouteRepositoryTestSuite) TestGetByShareCode() {
	route := s.testRoutes[0]
	_ = s.repo.Create(s.ctx, route)

	fetched, err := s.repo.GetByShareCode(s.ctx, *route.ShareCode)
	assert.NoError(s.T(), err)
	assert.Equal(s.T(), route.ID, fetched.ID)
}
