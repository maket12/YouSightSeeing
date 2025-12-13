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
	Users port.UserRepository
}

func NewGetUserUC(users port.UserRepository) *GetUserUC {
	return &GetUserUC{
		Users: users,
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
	user, err := uc.Users.GetByID(ctx, in.ID)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return dto.GetUserResponse{}, err
		}
		return dto.GetUserResponse{}, uc_errors.Wrap(uc_errors.GetUserError, err)
	}

	return dto.GetUserResponse{
		User: mappers.MapUserIntoUserResponse(user),
	}, nil
}
