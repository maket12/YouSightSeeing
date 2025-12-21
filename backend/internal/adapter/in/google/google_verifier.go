package google

import (
	"YouSightSeeing/backend/internal/domain/entity"
	"context"
	"fmt"
	"time"

	"golang.org/x/oauth2"
	oauth2api "google.golang.org/api/oauth2/v2"
	"google.golang.org/api/option"
)

type OAuthVerifier struct {
	clientID string
}

func NewOAuthVerifier(clientID string) *OAuthVerifier {
	return &OAuthVerifier{clientID: clientID}
}

func (v *OAuthVerifier) VerifyToken(ctx context.Context, token string) (*entity.GoogleClaims, error) {
	oauth2Service, err := oauth2api.NewService(ctx, option.WithHTTPClient(
		oauth2.NewClient(ctx, nil)))
	if err != nil {
		return nil, fmt.Errorf("failed to create oauth2 service: %w", err)
	}

	tokenInfo, err := oauth2Service.Tokeninfo().IdToken(token).Context(ctx).Do()
	if err != nil {
		return nil, fmt.Errorf("google token verification failed: %w", err)
	}

	if tokenInfo.Audience != v.clientID {
		return nil, fmt.Errorf("token audience mismatch: got %s, expected %s",
			tokenInfo.Audience, v.clientID)
	}

	userInfo, err := oauth2Service.Userinfo.Get().Context(ctx).Do()
	if err != nil {
		return v.CreateClaims(tokenInfo, nil), nil
	}

	return v.CreateClaims(tokenInfo, userInfo), nil
}

func (v *OAuthVerifier) CreateClaims(tokenInfo *oauth2api.Tokeninfo, userInfo *oauth2api.Userinfo) *entity.GoogleClaims {
	claims := &entity.GoogleClaims{
		Sub:           tokenInfo.UserId,
		Email:         tokenInfo.Email,
		EmailVerified: tokenInfo.VerifiedEmail,
	}

	if tokenInfo.ExpiresIn > 0 {
		claims.ExpiresAt = time.Now().Add(time.Duration(tokenInfo.ExpiresIn) * time.Second)
	}

	if userInfo != nil {
		claims.Name = stringPtr(userInfo.Name)
		claims.FamilyName = stringPtr(userInfo.FamilyName)
		claims.GivenName = stringPtr(userInfo.GivenName)
		claims.HD = stringPtr(userInfo.Hd)
		claims.Locale = stringPtr(userInfo.Locale)
		claims.Picture = stringPtr(userInfo.Picture)
	}

	return claims
}

func stringPtr(s string) *string {
	if s == "" {
		return nil
	}
	return &s
}
