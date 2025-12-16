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

type RefreshTokenRepository struct {
	db *sqlx.DB
}

func NewRefreshTokensRepository(db *sqlx.DB) *RefreshTokenRepository {
	return &RefreshTokenRepository{
		db: db,
	}
}

func (r *RefreshTokenRepository) Create(ctx context.Context, token *entity.RefreshToken) error {
	query := `INSERT INTO refresh_tokens (
                    id, user_id, token_hash, device_info, 
                    ip_address, user_agent, is_revoked, 
                    revoked_at, revoked_reason, issued_at, 
                    expires_at
              ) VALUES (
                    :id, :user_id, :token_hash, :device_info, 
                    :ip_address, :user_agent, :is_revoked, 
                    :revoked_at, :revoked_reason, :issued_at, 
                    :expires_at
              );`

	if _, err := r.db.NamedExecContext(ctx, query, token); err != nil {
		return fmt.Errorf("failed to create refresh token using db: %w", err)
	}

	return nil
}

func (r *RefreshTokenRepository) GetByHash(ctx context.Context, tokenHash string) (*entity.RefreshToken, error) {
	query := `SELECT id, user_id, token_hash, device_info, 
       				ip_address, user_agent, is_revoked, 
       				revoked_at, revoked_reason, issued_at, 
       				expires_at 
			  FROM refresh_tokens
			  WHERE token_hash = $1`

	var refreshToken entity.RefreshToken
	if err := r.db.GetContext(ctx, &refreshToken, query, tokenHash); err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, err
		}
		return nil, fmt.Errorf("failed to get refresh token using db: %w", err)
	}

	return &refreshToken, nil
}

func (r *RefreshTokenRepository) GetByID(ctx context.Context, id uuid.UUID) (*entity.RefreshToken, error) {
	query := `SELECT id, user_id, token_hash, device_info, 
       				ip_address, user_agent, is_revoked, 
       				revoked_at, revoked_reason, issued_at, 
       				expires_at 
			  FROM refresh_tokens
			  WHERE id = $1`

	var refreshToken entity.RefreshToken
	if err := r.db.GetContext(ctx, &refreshToken, query, id); err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, err
		}
		return nil, fmt.Errorf("failed to get refresh token using db: %w", err)
	}

	return &refreshToken, nil
}

func (r *RefreshTokenRepository) GetByUserID(ctx context.Context, userID uuid.UUID) (*entity.RefreshToken, error) {
	query := `SELECT id, user_id, token_hash, device_info, 
       				ip_address, user_agent, is_revoked, 
       				revoked_at, revoked_reason, issued_at, 
       				expires_at 
			  FROM refresh_tokens
			  WHERE user_id = $1 AND is_revoked = false`

	var refreshToken entity.RefreshToken
	if err := r.db.GetContext(ctx, &refreshToken, query, userID); err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, err
		}
		return nil, fmt.Errorf("failed to get refresh token using db: %w", err)
	}

	return &refreshToken, nil
}

func (r *RefreshTokenRepository) Revoke(ctx context.Context, tokenHash string, reason string) error {
	query := `UPDATE refresh_tokens 
			  SET 
			      is_revoked = true,
			      revoked_at = now(),
			      revoked_reason = $2 
			  WHERE token_hash = $1`

	res, err := r.db.ExecContext(ctx, query, tokenHash, reason)
	if err != nil {
		return fmt.Errorf("failed to revoke token using db: %w", err)
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

func (r *RefreshTokenRepository) RevokeByID(ctx context.Context, id uuid.UUID, reason string) error {
	query := `UPDATE refresh_tokens 
			  SET 
			      is_revoked = true,
			      revoked_at = now(),
			      revoked_reason = $2 
			  WHERE id = $1`

	res, err := r.db.ExecContext(ctx, query, id, reason)
	if err != nil {
		return fmt.Errorf("failed to revoke refresh token by id using db: %w", err)
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

func (r *RefreshTokenRepository) DeleteExpired(ctx context.Context) error {
	query := `DELETE FROM refresh_tokens
			  WHERE expires_at <= now()`

	if _, err := r.db.ExecContext(ctx, query); err != nil {
		return fmt.Errorf("failed to delete expired refresh tokens using db: %w", err)
	}

	return nil
}

func (r *RefreshTokenRepository) DeleteRevoked(ctx context.Context, olderThan *time.Time) error {
	query := `DELETE FROM refresh_tokens
			  WHERE revoked_at <= $1`

	var timeFilter time.Time

	if olderThan == nil {
		timeFilter = time.Now().UTC()
	} else {
		timeFilter = *olderThan
	}

	if _, err := r.db.ExecContext(ctx, query, timeFilter); err != nil {
		return fmt.Errorf("failed to delete revoked refresh tokens using db: %w", err)
	}

	return nil
}

func (r *RefreshTokenRepository) GetList(ctx context.Context, userID uuid.UUID) ([]entity.RefreshToken, error) {
	query := `SELECT id, user_id, token_hash, device_info, 
       				ip_address, user_agent, is_revoked, 
       				revoked_at, revoked_reason, issued_at, 
       				expires_at 
			  FROM refresh_tokens
			  WHERE user_id = $1`

	var tokens []entity.RefreshToken
	if err := r.db.SelectContext(ctx, &tokens, query, userID); err != nil {
		return nil, fmt.Errorf("failed to get list of refresh tokens using db: %w", err)
	}

	return tokens, nil
}

func (r *RefreshTokenRepository) GetListActive(ctx context.Context, userID uuid.UUID, isActive bool) ([]entity.RefreshToken, error) {
	query := `SELECT id, user_id, token_hash, device_info, 
       				ip_address, user_agent, is_revoked, 
       				revoked_at, revoked_reason, issued_at, 
       				expires_at 
			  FROM refresh_tokens
			  WHERE user_id = $1 AND is_revoked = $2`

	var tokens []entity.RefreshToken
	if err := r.db.SelectContext(ctx, &tokens, query, userID, !isActive); err != nil {
		return nil, fmt.Errorf("failed to get list of active refresh tokens using db: %w", err)
	}

	return tokens, nil
}

func (r *RefreshTokenRepository) IsValid(ctx context.Context, tokenHash string) (bool, error) {
	query := `SELECT EXISTS(
				SELECT 1 FROM refresh_tokens 
				WHERE token_hash = $1 
				AND is_revoked = false
				AND expires_at > now()
			  )`

	var isValid bool
	if err := r.db.GetContext(ctx, &isValid, query, tokenHash); err != nil {
		return false, fmt.Errorf("failed to check validation of refresh token using db: %w", err)
	}

	return isValid, nil
}
