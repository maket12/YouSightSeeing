package usecase

import (
	"YouSightSeeing/backend/internal/app/dto"
	"YouSightSeeing/backend/internal/app/mappers"
	"YouSightSeeing/backend/internal/app/uc_errors"
	"YouSightSeeing/backend/internal/app/utils"
	"YouSightSeeing/backend/internal/domain/entity"
	"YouSightSeeing/backend/internal/domain/port"
	"context"
	"database/sql"
	"errors"
	"time"

	"github.com/google/uuid"
)

type GoogleAuthUC struct {
	Users           port.UserRepository
	RefreshTokens   port.TokenRepository
	GoogleVerf      port.GoogleVerifier
	TokensGenerator port.TokensGenerator
	AccessTokenTTL  time.Duration
	RefreshTokenTTL time.Duration
}

func NewGoogleAuthUC(
	users port.UserRepository,
	refreshTokens port.TokenRepository,
	googleVerf port.GoogleVerifier,
	tokensGenerator port.TokensGenerator,
	accessTokenTTL time.Duration,
	refreshTokenTTL time.Duration) *GoogleAuthUC {
	return &GoogleAuthUC{
		Users:           users,
		RefreshTokens:   refreshTokens,
		GoogleVerf:      googleVerf,
		TokensGenerator: tokensGenerator,
		AccessTokenTTL:  accessTokenTTL,
		RefreshTokenTTL: refreshTokenTTL,
	}
}

func (uc *GoogleAuthUC) Execute(ctx context.Context, in dto.GoogleAuthRequest) (dto.GoogleAuthResponse, error) {
	/* ####################
	   #	Validation    #
	   ####################
	*/
	googleClaims, err := uc.validateGoogleToken(ctx, in.GoogleToken)
	if err != nil {
		return dto.GoogleAuthResponse{}, err
	}

	/* ####################
	   #	 Request      #
	   ####################
	*/
	var accessToken, refreshToken string

	existingUser, err := uc.Users.GetByGoogleSub(ctx, googleClaims.Sub)
	if err != nil {
		if !errors.Is(err, sql.ErrNoRows) {
			return dto.GoogleAuthResponse{}, uc_errors.Wrap(uc_errors.GetUserError, err)
		}
	} else {
		// Get current refresh-token
		existingToken, getTokenErr := uc.RefreshTokens.GetByUserID(ctx, existingUser.ID)

		// If it is expired, create a new one
		if errors.Is(getTokenErr, sql.ErrNoRows) {
			// New token
			rawToken, genErr := uc.TokensGenerator.GenerateRefreshToken(ctx)

			if genErr != nil {
				return dto.GoogleAuthResponse{}, uc_errors.Wrap(uc_errors.GenerateRefreshTokenError, genErr)
			}

			// Hashing token
			hashedToken := utils.HashToken(rawToken)

			// TODO: Иметь возможность задать ExpiresAt через конфиг
			newRefreshToken := &entity.RefreshToken{
				ID:        uuid.New(),
				UserID:    existingUser.ID,
				TokenHash: hashedToken,
				IssuedAt:  time.Now().UTC(),
				ExpiresAt: time.Now().Add(uc.RefreshTokenTTL).UTC(),
			}

			if createRefreshTokenErr := uc.RefreshTokens.Create(ctx, newRefreshToken); createRefreshTokenErr != nil {
				return dto.GoogleAuthResponse{}, uc_errors.Wrap(uc_errors.CreateRefreshTokenError, createRefreshTokenErr)
			}

			refreshToken = rawToken
		} else if getTokenErr != nil {
			return dto.GoogleAuthResponse{}, uc_errors.Wrap(uc_errors.GetRefreshTokenByUserIDError, getTokenErr)
		} else {
			// Revoke an existing one
			if revokeErr := uc.RefreshTokens.Revoke(ctx, existingToken.TokenHash, "new log in"); revokeErr != nil {
				return dto.GoogleAuthResponse{}, uc_errors.Wrap(uc_errors.RevokeRefreshTokenError, revokeErr)
			}

			// New token
			rawToken, genErr := uc.TokensGenerator.GenerateRefreshToken(ctx)

			if genErr != nil {
				return dto.GoogleAuthResponse{}, uc_errors.Wrap(uc_errors.GenerateRefreshTokenError, genErr)
			}

			// Hashing token
			hashedToken := utils.HashToken(rawToken)

			existingToken.ID = uuid.New()
			existingToken.TokenHash = hashedToken
			existingToken.IssuedAt = time.Now()
			existingToken.ExpiresAt = time.Now().Add(uc.RefreshTokenTTL).UTC()

			if createRefreshTokenErr := uc.RefreshTokens.Create(ctx, existingToken); createRefreshTokenErr != nil {
				return dto.GoogleAuthResponse{}, uc_errors.Wrap(uc_errors.CreateRefreshTokenError, createRefreshTokenErr)
			}

			refreshToken = rawToken
		}

		accessToken, err = uc.TokensGenerator.GenerateAccessToken(ctx, existingUser.ID)
		if err != nil {
			return dto.GoogleAuthResponse{}, uc_errors.Wrap(uc_errors.GenerateAccessTokenError, err)
		}

		return dto.GoogleAuthResponse{
			AccessToken:  accessToken,
			RefreshToken: refreshToken,
			User:         mappers.MapUserIntoUserResponse(existingUser),
		}, nil
	}

	// Create user object using claims
	newUser := mappers.MapGoogleClaimsIntoUser(googleClaims)

	if createUserErr := uc.Users.Create(ctx, newUser); createUserErr != nil {
		return dto.GoogleAuthResponse{}, uc_errors.Wrap(uc_errors.CreateUserError, createUserErr)
	}

	// New token
	rawToken, genErr := uc.TokensGenerator.GenerateRefreshToken(ctx)

	if genErr != nil {
		return dto.GoogleAuthResponse{}, uc_errors.Wrap(uc_errors.GenerateRefreshTokenError, genErr)
	}

	// Hashing token
	hashedToken := utils.HashToken(rawToken)

	// TODO: Иметь возможность задать ExpiresAt через конфиг
	newRefreshToken := &entity.RefreshToken{
		ID:        uuid.New(),
		UserID:    newUser.ID,
		TokenHash: hashedToken,
		IssuedAt:  time.Now().UTC(),
		ExpiresAt: time.Now().Add(uc.RefreshTokenTTL).UTC(),
	}

	if createRefreshTokenErr := uc.RefreshTokens.Create(ctx, newRefreshToken); createRefreshTokenErr != nil {
		return dto.GoogleAuthResponse{}, uc_errors.Wrap(uc_errors.CreateRefreshTokenError, createRefreshTokenErr)
	}

	accessToken, err = uc.TokensGenerator.GenerateAccessToken(ctx, newUser.ID)
	if err != nil {
		return dto.GoogleAuthResponse{}, uc_errors.Wrap(uc_errors.GenerateAccessTokenError, err)
	}

	return dto.GoogleAuthResponse{
		AccessToken:  accessToken,
		RefreshToken: rawToken,
		User:         mappers.MapUserIntoUserResponse(newUser),
	}, nil
}

func (uc *GoogleAuthUC) validateGoogleToken(ctx context.Context, token string) (*entity.GoogleClaims, error) {
	if token == "" {
		return nil, uc_errors.EmptyGoogleTokenError
	}

	googleClaims, err := uc.GoogleVerf.VerifyToken(ctx, token)
	if err != nil {
		return nil, uc_errors.Wrap(uc_errors.GoogleTokenValidationError, err)
	}

	if googleClaims.Sub == "" {
		return nil, uc_errors.EmptyGoogleSubError
	}
	if googleClaims.Email == "" {
		return nil, uc_errors.EmptyEmailError
	}

	if !googleClaims.EmailVerified {
		return nil, uc_errors.EmailNotVerifiedError
	}

	return googleClaims, nil
}
