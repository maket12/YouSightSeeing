package usecase

import (
	"YouSightSeeing/backend/internal/app/dto"
	"YouSightSeeing/backend/internal/app/mappers"
	"YouSightSeeing/backend/internal/app/uc_errors"
	"YouSightSeeing/backend/internal/domain/port"
	"context"
	"database/sql"
	"errors"

	"github.com/google/uuid"
)

type UpdateUserUC struct {
	Users port.UserRepository
}

func NewUpdateUserUC(users port.UserRepository) *UpdateUserUC {
	return &UpdateUserUC{
		Users: users,
	}
}

func (uc *UpdateUserUC) Execute(ctx context.Context, in dto.UpdateUserRequest) (dto.UpdateUserResponse, error) {
	/* ####################
	   #	Validation    #
	   ####################
	*/
	if in.ID == uuid.Nil {
		return dto.UpdateUserResponse{}, uc_errors.InvalidUserID
	}
	if in.Email == nil &&
		in.FullName == nil &&
		in.Picture == nil &&
		in.FirstName == nil &&
		in.LastName == nil {
		return dto.UpdateUserResponse{
			ID:      in.ID,
			Updated: false,
		}, nil
	}

	/* #####################
	   #	 Get user      #
	   #####################
	*/
	user, err := uc.Users.GetByID(ctx, in.ID)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return dto.UpdateUserResponse{
				ID:      in.ID,
				Updated: false,
			}, err
		}
		return dto.UpdateUserResponse{
			ID:      in.ID,
			Updated: false,
		}, uc_errors.Wrap(uc_errors.GetUserError, err)
	}

	/* #####################
	   #	 Upd user      #
	   #####################
	*/
	if in.Email != nil {
		user.Email = *in.Email
	}
	if in.FullName != nil {
		user.FullName = in.FullName
	}
	if in.Picture != nil {
		user.Picture = in.Picture
	}
	if in.FirstName != nil {
		user.FirstName = in.FirstName
	}
	if in.LastName != nil {
		user.LastName = in.LastName
	}

	/* ####################
	   #	 Request      #
	   ####################
	*/
	if err := uc.Users.Update(ctx, user); err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return dto.UpdateUserResponse{
				ID:      user.ID,
				Updated: false,
			}, err
		}
		return dto.UpdateUserResponse{
			ID:      user.ID,
			Updated: false,
		}, uc_errors.Wrap(uc_errors.UpdateUserError, err)
	}

	return dto.UpdateUserResponse{
		ID:      user.ID,
		Updated: true,
		User:    mappers.MapUserIntoUserResponse(user),
	}, nil
}
