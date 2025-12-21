package entity

import (
	"time"

	"github.com/google/uuid"
)

type RefreshToken struct {
	ID        uuid.UUID `db:"id"`
	UserID    uuid.UUID `db:"user_id"`
	TokenHash string    `db:"token_hash"`

	DeviceInfo *string `db:"device_info"`
	IPAddress  *string `db:"ip_address"`
	UserAgent  *string `db:"user_agent"`

	IsRevoked     bool       `db:"is_revoked"`
	RevokedAt     *time.Time `db:"revoked_at"`
	RevokedReason *string    `db:"revoked_reason"`

	IssuedAt  time.Time `db:"issued_at"`
	ExpiresAt time.Time `db:"expires_at"`
}
