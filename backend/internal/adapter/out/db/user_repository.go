package db

import (
	"YouSightSeeing/backend/internal/domain/entity"
	"context"
	"database/sql"
	"errors"
	"fmt"

	"github.com/google/uuid"
	"github.com/jmoiron/sqlx"
)

type UserRepository struct {
	db *sqlx.DB
}

func NewUserRepository(db *sqlx.DB) *UserRepository {
	return &UserRepository{
		db: db,
	}
}

func (r *UserRepository) Create(ctx context.Context, user *entity.User) error {
	query := `INSERT INTO users (
			       id, google_sub, email, full_name, picture,
			       first_name, last_name, email_verified,
			       google_domain, locale, created_at, updated_at
			  ) VALUES (
			      :id, :google_sub, :email, :full_name, :picture,
			      :first_name, :last_name, :email_verified, :google_domain,
			      :locale, :created_at, :updated_at
			  );`

	if _, err := r.db.NamedExecContext(ctx, query, user); err != nil {
		return fmt.Errorf("failed to create user using db: %w", err)
	}

	return nil
}

func (r *UserRepository) GetByID(ctx context.Context, id uuid.UUID) (*entity.User, error) {
	query := `SELECT id, google_sub, email, full_name, picture,
			       first_name, last_name, email_verified,
			       google_domain, locale, created_at, updated_at 
			  FROM users 
			  WHERE id = $1`

	var user entity.User

	if err := r.db.GetContext(ctx, &user, query, id); err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, err
		}
		return nil, fmt.Errorf("failed to get user using db: %w", err)
	}

	return &user, nil
}

func (r *UserRepository) GetByGoogleSub(ctx context.Context, googleSub string) (*entity.User, error) {
	query := `SELECT id, google_sub, email, full_name, picture,
			       first_name, last_name, email_verified,
			       google_domain, locale, created_at, updated_at 
			  FROM users 
			  WHERE google_sub = $1`

	var user entity.User

	if err := r.db.GetContext(ctx, &user, query, googleSub); err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, err
		}
		return nil, fmt.Errorf("failed to get user using db: %w", err)
	}

	return &user, nil
}

func (r *UserRepository) Update(ctx context.Context, user *entity.User) error {
	query := `UPDATE users
			  SET
				    email = :email,
				    full_name = :full_name,
				    picture = :picture,
				    first_name = :first_name,
				    last_name = :last_name,
				    updated_at = NOW() 
			  WHERE id = :id`

	res, err := r.db.NamedExecContext(ctx, query, user)
	if err != nil {
		return fmt.Errorf("failed to update user using db: %w", err)
	}

	rows, err := res.RowsAffected()
	if err != nil {
		return fmt.Errorf("failed to count rows: %w", err)
	}
	if rows == 0 {
		return sql.ErrNoRows
	}

	return nil
}

func (r *UserRepository) Delete(ctx context.Context, id uuid.UUID) error {
	query := `DELETE FROM users
			  WHERE id = $1`

	res, err := r.db.ExecContext(ctx, query, id)

	if err != nil {
		return fmt.Errorf("failed to delete user using db: %w", err)
	}

	rows, err := res.RowsAffected()

	if err != nil {
		return fmt.Errorf("failed to count rows: %w", err)
	}
	if rows == 0 {
		return sql.ErrNoRows
	}

	return nil
}

func (r *UserRepository) GetList(ctx context.Context, limit, offset int) ([]entity.User, error) {
	query := `SELECT id, google_sub, email, full_name, picture,
			       first_name, last_name, email_verified,
			       google_domain, locale, created_at, updated_at 
			  FROM users
			  LIMIT $1 OFFSET $2`

	var users []entity.User
	if err := r.db.SelectContext(ctx, &users, query, limit, offset); err != nil {
		return nil, fmt.Errorf("failed to get list of users using db: %w", err)
	}

	return users, nil
}
