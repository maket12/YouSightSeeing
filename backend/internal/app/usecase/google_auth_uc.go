package usecase

import (
	dto2 "YouSightSeeing/backend/internal/app/dto"
	"YouSightSeeing/backend/internal/app/mappers"
	uc_errors2 "YouSightSeeing/backend/internal/app/uc_errors"
	entity2 "YouSightSeeing/backend/internal/domain/entity"
	port2 "YouSightSeeing/backend/internal/domain/port"
	"context"
	"crypto/sha256"
	"database/sql"
	"encoding/hex"
	"errors"
	"time"

	"github.com/google/uuid"
)

type GoogleAuthUC struct {
	Users           port2.UserRepository
	RefreshTokens   port2.TokenRepository
	GoogleVerf      port2.GoogleVerifier
	TokensGenerator port2.TokensGenerator
}

func (uc *GoogleAuthUC) Execute(ctx context.Context, in dto2.GoogleAuthRequest) (dto2.GoogleAuthResponse, error) {
	/* ####################
	   #	Validation    #
	   ####################
	*/
	googleClaims, err := uc.validateGoogleToken(ctx, in.GoogleToken)
	if err != nil {
		return dto2.GoogleAuthResponse{}, err
	}

	/* ####################
	   #	 Request      #
	   ####################
	*/
	var accessToken, refreshToken string

	existingUser, err := uc.Users.GetByGoogleSub(ctx, googleClaims.Sub)
	if err == nil {
		// Get current refresh-token
		existingToken, getTokenErr := uc.RefreshTokens.GetByUserID(ctx, existingUser.ID)

		// If it is expired, create a new one
		if errors.Is(getTokenErr, sql.ErrNoRows) {
			// New token
			rawToken, genErr := uc.TokensGenerator.GenerateRefreshToken(ctx, existingUser.ID)

			if genErr != nil {
				return dto2.GoogleAuthResponse{}, uc_errors2.Wrap(uc_errors2.GenerateRefreshTokenError, genErr)
			}

			// Hashing token
			hashedToken := hashToken(rawToken)

			// TODO: Иметь возможность задать ExpiresAt через конфиг
			newRefreshToken := &entity2.RefreshToken{
				ID:        uuid.New(),
				UserID:    existingUser.ID,
				TokenHash: hashedToken,
				IssuedAt:  time.Now().UTC(),
				ExpiresAt: time.Now().Add(time.Hour * 24 * 7).UTC(),
			}

			if createRefreshTokenErr := uc.RefreshTokens.Create(ctx, newRefreshToken); createRefreshTokenErr != nil {
				return dto2.GoogleAuthResponse{}, uc_errors2.Wrap(uc_errors2.CreateRefreshTokenError, createRefreshTokenErr)
			}

			refreshToken = rawToken
		} else if getTokenErr != nil {
			return dto2.GoogleAuthResponse{}, uc_errors2.Wrap(uc_errors2.GetRefreshTokenByUserIDError, getTokenErr)
		} else {
			// Revoke an existing one
			if revokeErr := uc.RefreshTokens.Revoke(ctx, existingToken.TokenHash, "new log in"); revokeErr != nil {
				return dto2.GoogleAuthResponse{}, uc_errors2.Wrap(uc_errors2.RevokeRefreshTokenError, revokeErr)
			}

			// New token
			rawToken, genErr := uc.TokensGenerator.GenerateRefreshToken(ctx, existingUser.ID)

			if genErr != nil {
				return dto2.GoogleAuthResponse{}, uc_errors2.Wrap(uc_errors2.GenerateRefreshTokenError, genErr)
			}

			// Hashing token
			hashedToken := hashToken(rawToken)

			existingToken.ID = uuid.New()
			existingToken.TokenHash = hashedToken
			existingToken.IssuedAt = time.Now()
			existingToken.ExpiresAt = time.Now().Add(time.Hour * 24 * 7).UTC()

			if createRefreshTokenErr := uc.RefreshTokens.Create(ctx, existingToken); createRefreshTokenErr != nil {
				return dto2.GoogleAuthResponse{}, uc_errors2.Wrap(uc_errors2.CreateRefreshTokenError, createRefreshTokenErr)
			}

			refreshToken = rawToken
		}

		accessToken, err = uc.TokensGenerator.GenerateAccessToken(ctx, existingUser.ID)
		if err != nil {
			return dto2.GoogleAuthResponse{}, uc_errors2.Wrap(uc_errors2.GenerateAccessTokenError, err)
		}

		return dto2.GoogleAuthResponse{
			AccessToken:  accessToken,
			RefreshToken: refreshToken,
			User:         mappers.MapUserIntoUserResponse(existingUser),
		}, nil
	}

	// Create user object using claims
	newUser := mappers.MapGoogleClaimsIntoUser(googleClaims)

	if createUserErr := uc.Users.Create(ctx, newUser); createUserErr != nil {
		return dto2.GoogleAuthResponse{}, uc_errors2.Wrap(uc_errors2.CreateUserError, createUserErr)
	}

	// New token
	rawToken, genErr := uc.TokensGenerator.GenerateRefreshToken(ctx, existingUser.ID)

	if genErr != nil {
		return dto2.GoogleAuthResponse{}, uc_errors2.Wrap(uc_errors2.GenerateRefreshTokenError, genErr)
	}

	// Hashing token
	hashedToken := hashToken(rawToken)

	// TODO: Иметь возможность задать ExpiresAt через конфиг
	newRefreshToken := &entity2.RefreshToken{
		ID:        uuid.New(),
		UserID:    newUser.ID,
		TokenHash: hashedToken,
		IssuedAt:  time.Now().UTC(),
		ExpiresAt: time.Now().Add(time.Hour * 24 * 7).UTC(),
	}

	if createRefreshTokenErr := uc.RefreshTokens.Create(ctx, newRefreshToken); createRefreshTokenErr != nil {
		return dto2.GoogleAuthResponse{}, uc_errors2.Wrap(uc_errors2.CreateRefreshTokenError, createRefreshTokenErr)
	}

	accessToken, err = uc.TokensGenerator.GenerateAccessToken(ctx, newUser.ID)
	if err != nil {
		return dto2.GoogleAuthResponse{}, uc_errors2.Wrap(uc_errors2.GenerateAccessTokenError, err)
	}

	return dto2.GoogleAuthResponse{
		AccessToken:  accessToken,
		RefreshToken: rawToken,
		User:         mappers.MapUserIntoUserResponse(newUser),
	}, nil
}

func (uc *GoogleAuthUC) validateGoogleToken(ctx context.Context, token string) (*entity2.GoogleClaims, error) {
	if token == "" {
		return nil, uc_errors2.EmptyGoogleTokenError
	}

	googleClaims, err := uc.GoogleVerf.VerifyToken(ctx, token)
	if err != nil {
		return nil, uc_errors2.Wrap(uc_errors2.GoogleTokenValidationError, err)
	}

	if googleClaims.Sub == "" {
		return nil, uc_errors2.Wrap(uc_errors2.EmptyGoogleSubError, err)
	}
	if googleClaims.Email == "" {
		return nil, uc_errors2.Wrap(uc_errors2.EmptyEmailError, err)
	}

	if !googleClaims.EmailVerified {
		return nil, uc_errors2.EmailNotVerifiedError
	}

	return googleClaims, nil
}

func hashToken(token string) string {
	hash := sha256.Sum256([]byte(token))
	return hex.EncodeToString(hash[:])
}
