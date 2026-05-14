package db

import (
	"YouSightSeeing/backend/internal/domain/entity"
	"context"
	"database/sql"
	"errors"
	"fmt"
	"time"

	"github.com/google/uuid"
	"github.com/jmoiron/sqlx"
)

type UserCategoryPreferencesRepository struct {
	db *sqlx.DB
}

func NewUserCategoryPreferencesRepository(db *sqlx.DB) *UserCategoryPreferencesRepository {
	return &UserCategoryPreferencesRepository{
		db: db,
	}
}

func (r *UserCategoryPreferencesRepository) Upsert(
	ctx context.Context,
	preference *entity.UserCategoryPreference,
) error {
	if preference.UpdatedAt.IsZero() {
		preference.UpdatedAt = time.Now().UTC()
	}

	query := `
		INSERT INTO user_category_preferences (
			id, user_id, category, weight, updated_at
		) VALUES (
			:id, :user_id, :category, :weight, :updated_at
		)
		ON CONFLICT (user_id, category)
		DO UPDATE SET
			weight = EXCLUDED.weight,
			updated_at = EXCLUDED.updated_at
	`

	if _, err := r.db.NamedExecContext(ctx, query, preference); err != nil {
		return fmt.Errorf("failed to upsert user category preference using db: %w", err)
	}

	return nil
}

func (r *UserCategoryPreferencesRepository) GetByUserID(
	ctx context.Context,
	userID uuid.UUID,
) ([]entity.UserCategoryPreference, error) {
	query := `
		SELECT id, user_id, category, weight, updated_at
		FROM user_category_preferences
		WHERE user_id = $1
		ORDER BY category
	`

	var preferences []entity.UserCategoryPreference
	if err := r.db.SelectContext(ctx, &preferences, query, userID); err != nil {
		return nil, fmt.Errorf("failed to get user category preferences using db: %w", err)
	}

	return preferences, nil
}

func (r *UserCategoryPreferencesRepository) GetByUserIDAndCategory(
	ctx context.Context,
	userID uuid.UUID,
	category string,
) (*entity.UserCategoryPreference, error) {
	query := `
		SELECT id, user_id, category, weight, updated_at
		FROM user_category_preferences
		WHERE user_id = $1 AND category = $2
	`

	var preference entity.UserCategoryPreference
	if err := r.db.GetContext(ctx, &preference, query, userID, category); err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, err
		}
		return nil, fmt.Errorf("failed to get user category preference using db: %w", err)
	}

	return &preference, nil
}

func (r *UserCategoryPreferencesRepository) DeleteByUserID(
	ctx context.Context,
	userID uuid.UUID,
) error {
	query := `DELETE FROM user_category_preferences WHERE user_id = $1`

	if _, err := r.db.ExecContext(ctx, query, userID); err != nil {
		return fmt.Errorf("failed to delete user category preferences using db: %w", err)
	}

	return nil
}
