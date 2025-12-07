package jwt

import (
	"YouSightSeeing/backend/internal/domain/entity"
	"context"
	"fmt"
	"time"

	"github.com/golang-jwt/jwt/v5"
	"github.com/google/uuid"
)

type TokensGenerator struct {
	accessSecret  []byte
	refreshSecret []byte
}

func NewTokensGenerator(accessSecret, refreshSecret []byte) *TokensGenerator {
	return &TokensGenerator{
		accessSecret:  accessSecret,
		refreshSecret: refreshSecret,
	}
}

func (gen *TokensGenerator) GenerateAccessToken(ctx context.Context, userID uuid.UUID) (string, error) {
	claims := entity.AccessClaims{
		Type:   "access",
		UserID: userID,
	}

	// TODO: иметь возможность устанавливать срок жизни через конфиг
	jwtClaims := toJwtAccess(claims, time.Minute*15)

	token := jwt.NewWithClaims(jwt.SigningMethodHS256, jwtClaims)
	return token.SignedString(gen.accessSecret)
}

func (gen *TokensGenerator) GenerateRefreshToken(ctx context.Context) (string, error) {
	claims := entity.RefreshClaims{
		Type: "refresh",
	}

	// TODO: иметь возможность устанавливать срок жизни через конфиг
	jwtClaims := toJwtRefresh(claims, time.Hour*24*7)

	token := jwt.NewWithClaims(jwt.SigningMethodHS256, jwtClaims)
	return token.SignedString(gen.refreshSecret)
}

func (gen *TokensGenerator) ParseAccessToken(ctx context.Context, token string) (*entity.AccessClaims, error) {
	jwtClaims := &jwtAccessClaims{}

	parsedToken, err := jwt.ParseWithClaims(token, jwtClaims, func(token *jwt.Token) (interface{}, error) {
		return gen.accessSecret, nil
	}, jwt.WithValidMethods([]string{jwt.SigningMethodHS256.Name}), jwt.WithLeeway(30*time.Second))

	if err != nil || !parsedToken.Valid {
		return nil, fmt.Errorf("failed to parse token: %w", err)
	}

	return toDomainAccess(jwtClaims), nil
}

func (gen *TokensGenerator) ParseRefreshToken(ctx context.Context, token string) (*entity.RefreshClaims, error) {
	jwtClaims := &jwtRefreshClaims{}

	parsedToken, err := jwt.ParseWithClaims(token, jwtClaims, func(token *jwt.Token) (interface{}, error) {
		return gen.refreshSecret, nil
	}, jwt.WithValidMethods([]string{jwt.SigningMethodHS256.Name}), jwt.WithLeeway(30*time.Second))

	if err != nil || !parsedToken.Valid {
		return nil, fmt.Errorf("failed to parse token: %w", err)
	}

	return toDomainRefresh(jwtClaims), nil
}

type jwtAccessClaims struct {
	jwt.RegisteredClaims
	Type   string    `json:"type"`
	UserID uuid.UUID `json:"user_id"`
}

type jwtRefreshClaims struct {
	jwt.RegisteredClaims
	Type string `json:"type"`
}
