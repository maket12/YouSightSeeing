package db

import (
	"YouSightSeeing/backend/internal/domain/entity"
	"context"
	"fmt"

	"github.com/google/uuid"
	"github.com/jmoiron/sqlx"
)

type UserEventRepository struct {
	db *sqlx.DB
}

func NewUserEventRepository(db *sqlx.DB) *UserEventRepository {
	return &UserEventRepository{
		db: db,
	}
}

func (r *UserEventRepository) Create(ctx context.Context, event *entity.UserEvent) error {
	query := `
		INSERT INTO user_events (
			id, user_id, event_type, route_id, place_id, category, created_at
		) VALUES (
			:id, :user_id, :event_type, :route_id, :place_id, :category, :created_at
		)
	`

	if _, err := r.db.NamedExecContext(ctx, query, event); err != nil {
		return fmt.Errorf("failed to create user event using db: %w", err)
	}

	return nil
}

func (r *UserEventRepository) GetByUserID(
	ctx context.Context,
	userID uuid.UUID,
	limit,
	offset int,
) ([]entity.UserEvent, error) {
	query := `
		SELECT id, user_id, event_type, route_id, place_id, category, created_at
		FROM user_events
		WHERE user_id = $1
		ORDER BY created_at DESC
		LIMIT $2 OFFSET $3
	`

	var events []entity.UserEvent
	if err := r.db.SelectContext(ctx, &events, query, userID, limit, offset); err != nil {
		return nil, fmt.Errorf("failed to get user events using db: %w", err)
	}

	return events, nil
}

func (r *UserEventRepository) GetRecentByUserID(
	ctx context.Context,
	userID uuid.UUID,
	limit int,
) ([]entity.UserEvent, error) {
	query := `
		SELECT id, user_id, event_type, route_id, place_id, category, created_at
		FROM user_events
		WHERE user_id = $1
		ORDER BY created_at DESC
		LIMIT $2
	`

	var events []entity.UserEvent
	if err := r.db.SelectContext(ctx, &events, query, userID, limit); err != nil {
		return nil, fmt.Errorf("failed to get recent user events using db: %w", err)
	}

	return events, nil
}
