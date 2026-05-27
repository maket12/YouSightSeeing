package db

import (
	"YouSightSeeing/backend/internal/domain/entity"
	"YouSightSeeing/backend/internal/domain/port"
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

func (r *UserEventRepository) GetGlobalStatsByPlaceIDs(
	ctx context.Context,
	placeIDs []string,
) (map[string]port.PlaceGlobalStats, error) {
	if len(placeIDs) == 0 {
		return map[string]port.PlaceGlobalStats{}, nil
	}

	query := `
		SELECT place_id,
			   SUM(CASE WHEN event_type = 'route_generated' THEN 1 ELSE 0 END) AS generated_count,
			   SUM(CASE WHEN event_type = 'route_saved' THEN 1 ELSE 0 END) AS saved_count
		FROM user_events
		WHERE place_id = ANY($1)
		GROUP BY place_id
	`

	rows, err := r.db.QueryxContext(ctx, query, placeIDs)
	if err != nil {
		return nil, fmt.Errorf("failed to query global place stats: %w", err)
	}
	defer rows.Close()

	result := make(map[string]port.PlaceGlobalStats)

	for rows.Next() {
		var placeID string
		var generated int
		var saved int
		if err := rows.Scan(&placeID, &generated, &saved); err != nil {
			return nil, fmt.Errorf("failed to scan global place stats row: %w", err)
		}
		result[placeID] = port.PlaceGlobalStats{
			GeneratedCount: generated,
			SavedCount:     saved,
		}
	}

	if err := rows.Err(); err != nil {
		return nil, fmt.Errorf("rows error while reading global place stats: %w", err)
	}

	return result, nil
}
