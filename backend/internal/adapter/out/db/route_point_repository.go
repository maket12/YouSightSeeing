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

type RoutePointRepository struct {
	db *sqlx.DB
}

func NewRoutePointRepository(db *sqlx.DB) *RoutePointRepository {
	return &RoutePointRepository{db: db}
}

func (r *RoutePointRepository) Create(ctx context.Context, routePoint *entity.RoutePoint) error {
	query := `INSERT INTO route_points (
                 route_id, position, place_id, name, 
                 address, categories, latitude, longitude
             ) VALUES (
                 :route_id, :position, :place_id, :name, 
                 :address, :categories, :latitude, :longitude
           	);`

	if _, err := r.db.NamedExecContext(ctx, query, routePoint); err != nil {
		return fmt.Errorf("failed to create route: %w", err)
	}

	return nil
}

func (r *RoutePointRepository) Get(ctx context.Context, routeID uuid.UUID) ([]entity.RoutePoint, error) {
	query := `SELECT * FROM route_points WHERE route_id = $1`

	var routePoints []entity.RoutePoint
	if err := r.db.GetContext(ctx, &routePoints, query, routeID); err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, err
		}
		return nil, fmt.Errorf("failed to get route point: %w", err)
	}

	return routePoints, nil
}
