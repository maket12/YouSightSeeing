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

type GetUserUC struct {
	user port.UserRepository
}

func NewGetUserUC(users port.UserRepository) *GetUserUC {
	return &GetUserUC{
		user: users,
	}
}

func (uc *GetUserUC) Execute(ctx context.Context, in dto.GetUserRequest) (dto.GetUserResponse, error) {
	/* ####################
	   #	Validation    #
	   ####################
	*/
	if in.ID == uuid.Nil {
		return dto.GetUserResponse{}, uc_errors.InvalidUserID
	}

	/* ####################
	   #	 Request      #
	   ####################
	*/
	user, err := uc.user.GetByID(ctx, in.ID)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return dto.GetUserResponse{}, uc_errors.UserNotFoundError
		}
		return dto.GetUserResponse{}, uc_errors.Wrap(uc_errors.GetUserError, err)
	}

	return dto.GetUserResponse{
		User: mappers.MapUserIntoUserResponse(user),
	}, nil
}
