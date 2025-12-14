package db

import (
	"context"

	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/jackc/pgx/v5/stdlib"
	"github.com/jmoiron/sqlx"
)

func NewPostgres(dsn string) (*sqlx.DB, error) {
	pool, err := pgxpool.New(context.Background(), dsn)
	if err != nil {
		return nil, err
	}

	pg := stdlib.OpenDBFromPool(pool)

	return sqlx.NewDb(pg, "pgx"), nil
}
