package mappers

import (
	"YouSightSeeing/backend/internal/app/dto"
	"YouSightSeeing/backend/internal/domain/entity"
	"time"

	"github.com/google/uuid"
)

func MapPlacesIntoResponse(places []entity.Place) []dto.Place {
	if places == nil {
		return []dto.Place{}
	}

	result := make([]dto.Place, 0, len(places))
	for _, p := range places {
		result = append(result, dto.Place{
			PlaceID:     p.ID,
			Name:        p.Name,
			Address:     p.Address,
			Categories:  p.Categories,
			Coordinates: p.Coordinates,
		})
	}
	return result
}

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
