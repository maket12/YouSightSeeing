package db

import (
	"YouSightSeeing/backend/internal/domain/entity"
	"context"
	"database/sql"
	"errors"
	"fmt"

	"github.com/google/uuid"
	"github.com/jmoiron/sqlx"
	"github.com/lib/pq"
)

type RoutePointRepository struct {
	db *sqlx.DB
}

func NewRoutePointRepository(db *sqlx.DB) *RoutePointRepository {
	return &RoutePointRepository{db: db}
}

func (r *RoutePointRepository) Create(ctx context.Context, routePoint *entity.RoutePoint) error {
	query := `
		INSERT INTO route_points (
			id,
			route_id,
			position,
			place_id,
			name,
			address,
			categories,
			latitude,
			longitude
		)
		VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9)
	`

	_, err := executor(ctx, r.db).ExecContext(
		ctx,
		query,
		routePoint.ID,
		routePoint.RouteID,
		routePoint.Position,
		routePoint.PlaceID,
		routePoint.Name,
		routePoint.Address,
		pq.Array(routePoint.Categories),
		routePoint.Latitude,
		routePoint.Longitude,
	)
	if err != nil {
		return fmt.Errorf("failed to create route point: %w", err)
	}

	return nil
}

func (r *RoutePointRepository) Get(ctx context.Context, routeID uuid.UUID) ([]entity.RoutePoint, error) {
	query := `
		SELECT
			id,
			route_id,
			position,
			place_id,
			name,
			address,
			categories,
			latitude,
			longitude
		FROM route_points
		WHERE route_id = $1
		ORDER BY position
	`

	var routePoints []entity.RoutePoint
	if err := r.db.SelectContext(ctx, &routePoints, query, routeID); err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, err
		}
		return nil, fmt.Errorf("failed to get route points: %w", err)
	}

	return routePoints, nil
}
