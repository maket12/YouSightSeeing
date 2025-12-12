package mappers

import (
	"YouSightSeeing/backend/internal/app/dto"
	"YouSightSeeing/backend/internal/domain/entity"
	"time"

	"github.com/google/uuid"
)

func MapUserIntoUserResponse(user *entity.User) dto.UserResponse {
	if user != nil {
		return dto.UserResponse{
			ID:            user.ID,
			GoogleSub:     user.GoogleSub,
			Email:         user.Email,
			FullName:      user.FullName,
			Picture:       user.Picture,
			FirstName:     user.FirstName,
			LastName:      user.LastName,
			EmailVerified: user.EmailVerified,
			GoogleDomain:  user.GoogleDomain,
			Locale:        user.Locale,
			CreatedAt:     user.CreatedAt,
			UpdatedAt:     user.UpdatedAt,
		}
	}
	return dto.UserResponse{}
}

func MapGoogleClaimsIntoUser(claims *entity.GoogleClaims) *entity.User {
	now := time.Now().UTC()
	return &entity.User{
		ID:            uuid.New(),
		GoogleSub:     claims.Sub,
		Email:         claims.Email,
		FullName:      claims.Name,
		Picture:       claims.Picture,
		FirstName:     claims.GivenName,
		LastName:      claims.FamilyName,
		EmailVerified: claims.EmailVerified,
		GoogleDomain:  claims.HD,
		Locale:        claims.Locale,
		CreatedAt:     now,
		UpdatedAt:     &now,
	}
}
