package jwt

import (
	"YouSightSeeing/backend/internal/domain/entity"
	"time"

	"github.com/golang-jwt/jwt/v5"
)

func toJwtAccess(c entity.AccessClaims, ttl time.Duration) jwtAccessClaims {
	return jwtAccessClaims{
		RegisteredClaims: jwt.RegisteredClaims{
			ExpiresAt: jwt.NewNumericDate(time.Now().Add(ttl)),
			IssuedAt:  jwt.NewNumericDate(time.Now()),
		},
		Type:   c.Type,
		UserID: c.UserID,
	}
}

func toJwtRefresh(c entity.RefreshClaims, ttl time.Duration) jwtRefreshClaims {
	return jwtRefreshClaims{
		RegisteredClaims: jwt.RegisteredClaims{
			ExpiresAt: jwt.NewNumericDate(time.Now().Add(ttl)),
			IssuedAt:  jwt.NewNumericDate(time.Now()),
		},
		Type: c.Type,
	}
}

func toDomainAccess(j *jwtAccessClaims) *entity.AccessClaims {
	return &entity.AccessClaims{
		Type:   j.Type,
		UserID: j.UserID,
	}
}

func toDomainRefresh(j *jwtRefreshClaims) *entity.RefreshClaims {
	return &entity.RefreshClaims{
		Type: j.Type,
	}
}
