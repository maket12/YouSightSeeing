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

type UpdateUserPictureUC struct {
	Users port.UserRepository
}

func NewUpdateUserPictureUC(users port.UserRepository) *UpdateUserPictureUC {
	return &UpdateUserPictureUC{
		Users: users,
	}
}

func (uc *UpdateUserPictureUC) Execute(ctx context.Context, in dto.UpdateUserPictureRequest) (dto.UpdateUserPictureResponse, error) {
	/* ####################
	   #	Validation    #
	   ####################
	*/
	if in.ID == uuid.Nil {
		return dto.UpdateUserPictureResponse{}, uc_errors.InvalidUserID
	}

	/* #####################
	   #	 Get user      #
	   #####################
	*/
	user, err := uc.Users.GetByID(ctx, in.ID)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return dto.UpdateUserPictureResponse{
				ID:      in.ID,
				Updated: false,
			}, err
		}
		return dto.UpdateUserPictureResponse{
			ID:      in.ID,
			Updated: false,
		}, uc_errors.Wrap(uc_errors.GetUserError, err)
	}

	/* #####################
	   #	 Upd user      #
	   #####################
	*/
	user.Picture = &in.Picture

	/* ####################
	   #	 Request      #
	   ####################
	*/
	if err := uc.Users.Update(ctx, user); err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return dto.UpdateUserPictureResponse{
				ID:      user.ID,
				Updated: false,
			}, err
		}
		return dto.UpdateUserPictureResponse{
			ID:      user.ID,
			Updated: false,
		}, uc_errors.Wrap(uc_errors.UpdateUserPictureError, err)
	}

	return dto.UpdateUserPictureResponse{
		ID:      user.ID,
		Updated: true,
		User:    mappers.MapUserIntoUserResponse(user),
	}, nil
}
