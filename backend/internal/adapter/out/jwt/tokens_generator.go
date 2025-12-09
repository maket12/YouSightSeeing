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
	accessSecret    []byte
	refreshSecret   []byte
	accessTokenTTL  time.Duration
	refreshTokenTTL time.Duration
}

func NewTokensGenerator(
	accessSecret, refreshSecret []byte,
	accessTTL, refreshTTL time.Duration) *TokensGenerator {
	return &TokensGenerator{
		accessSecret:    accessSecret,
		refreshSecret:   refreshSecret,
		accessTokenTTL:  accessTTL,
		refreshTokenTTL: refreshTTL,
	}
}

func (gen *TokensGenerator) GenerateAccessToken(ctx context.Context, userID uuid.UUID) (string, error) {
	claims := entity.AccessClaims{
		Type:   "access",
		UserID: userID,
	}

	// TODO: иметь возможность устанавливать срок жизни через конфиг
	jwtClaims := toJwtAccess(claims, gen.accessTokenTTL)

	token := jwt.NewWithClaims(jwt.SigningMethodHS256, jwtClaims)
	return token.SignedString(gen.accessSecret)
}

func (gen *TokensGenerator) GenerateRefreshToken(ctx context.Context) (string, error) {
	claims := entity.RefreshClaims{
		Type: "refresh",
	}

	// TODO: иметь возможность устанавливать срок жизни через конфиг
	jwtClaims := toJwtRefresh(claims, gen.refreshTokenTTL)

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

func (gen *TokensGenerator) ValidateAccessToken(ctx context.Context, token string) (userID uuid.UUID, err error) {
	claims, err := gen.ParseAccessToken(ctx, token)
	if err != nil {
		return uuid.Nil, err
	}

	if claims.Type != "access" {
		return uuid.Nil, fmt.Errorf("invalid token type")
	}

	return claims.UserID, nil
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
