package db

import (
	"YouSightSeeing/backend/internal/domain/entity"
	"context"
	"database/sql"
	"errors"
	"fmt"
	"lib/pq"

	"github.com/google/uuid"
	"github.com/jmoiron/sqlx"
)

type RouteRepository struct {
	db *sqlx.DB
}

func NewRouteRepository(db *sqlx.DB) *RouteRepository {
	return &RouteRepository{
		db: db,
	}
}

// Create — Создание нового маршрута
func (r *RouteRepository) Create(ctx context.Context, route *entity.Route) error {
	query := `INSERT INTO routes (
                 id, user_id, title, start_latitude, start_longitude, 
                 distance, duration, categories, max_places, 
                 include_food, is_public, share_code, created_at, updated_at
            ) VALUES (
                :id, :user_id, :title, :start_latitude, :start_longitude,
                :distance, :duration, :categories, :max_places,
                :include_food, :is_public, :share_code, :created_at, :updated_at
            );`

	if _, err := r.db.NamedExecContext(ctx, query, route); err != nil {
		return fmt.Errorf("failed to create route: %w", err)
	}

	return nil
}

// GetByID — Получение одного маршрута по ID
func (r *RouteRepository) GetByID(ctx context.Context, id uuid.UUID) (*entity.Route, error) {
	query := `SELECT * FROM routes WHERE id = $1`

	var route entity.Route
	if err := r.db.GetContext(ctx, &route, query, id); err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, err
		}
		return nil, fmt.Errorf("failed to get route: %w", err)
	}

	return &route, nil
}

// Update — Обновление данных маршрута
func (r *RouteRepository) Update(ctx context.Context, route *entity.Route) error {
	query := `UPDATE routes
            SET
                 title = :title,
                 is_public = :is_public,
                 share_code = :share_code,
                 updated_at = NOW() 
            WHERE id = :id`

	res, err := r.db.NamedExecContext(ctx, query, route)
	if err != nil {
		return fmt.Errorf("failed to update route: %w", err)
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

// Delete — Удаление маршрута
func (r *RouteRepository) Delete(ctx context.Context, id uuid.UUID) error {
	query := `DELETE FROM routes WHERE id = $1`

	res, err := r.db.ExecContext(ctx, query, id)
	if err != nil {
		return fmt.Errorf("failed to delete route: %w", err)
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

// GetListByUserID — Получение списка маршрутов конкретного пользователя (L в CRUDL)
func (r *RouteRepository) GetListByUserID(ctx context.Context, userID uuid.UUID, limit, offset int) ([]entity.Route, error) {
	query := `SELECT * FROM routes 
            WHERE user_id = $1 
            ORDER BY created_at DESC 
            LIMIT $2 OFFSET $3`

	var routes []entity.Route
	if err := r.db.SelectContext(ctx, &routes, query, userID, limit, offset); err != nil {
		return nil, fmt.Errorf("failed to get route list: %w", err)
	}

	return routes, nil
}

// GetByShareCode — Дополнительный метод для публичных ссылок
func (r *RouteRepository) GetByShareCode(ctx context.Context, code string) (*entity.Route, error) {
	query := `SELECT * FROM routes WHERE share_code = $1 AND is_public = TRUE`

	var route entity.Route
	if err := r.db.GetContext(ctx, &route, query, code); err != nil {
		return nil, err
	}

	return &route, nil
}
