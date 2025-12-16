package db_test

// TODO: go:build integration || +build integration

import (
	"YouSightSeeing/backend/internal/adapter/out/db"
	"context"
	"testing"

	"github.com/jmoiron/sqlx"
	"github.com/stretchr/testify/require"
	"github.com/stretchr/testify/suite"
)

type UserRepositoryTestSuite struct {
	suite.Suite
	db   *sqlx.DB
	repo *db.UserRepository
	ctx  context.Context
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

	_, err = dbx.Exec("TRUNCATE users RESTART IDENTITY CASCADE")
	require.NoError(t, err)
}

func (s *UserRepositoryTestSuite) setupDatabase() {
	_, err := s.db.Exec("DROP TABLE IF EXISTS users")
	s.Require()

	createTableSQL := `
    CREATE TABLE users (
        id UUID PRIMARY KEY,
        google_sub TEXT UNIQUE,
        email TEXT NOT NULL,
        full_name TEXT,
        picture TEXT,
        first_name TEXT,
        last_name TEXT,
        email_verified BOOLEAN DEFAULT false,
        google_domain TEXT,
        locale TEXT,
        created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
        updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
    );`

	_, err := s.db.Exec(createTableSQL)
	require.NoError(s.T(), err)
}
