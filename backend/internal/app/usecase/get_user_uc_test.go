package usecase_test

import (
	"YouSightSeeing/backend/internal/app/dto"
	"YouSightSeeing/backend/internal/app/uc_errors"
	"YouSightSeeing/backend/internal/app/usecase"
	"YouSightSeeing/backend/internal/domain/entity"
	"YouSightSeeing/backend/internal/domain/port/mocks"
	"context"
	"database/sql"
	"errors"
	"testing"

	"github.com/google/uuid"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/mock"
)

type GetUserTestCase struct {
	Name  string
	Input dto.GetUserRequest

	ExceptGet bool
	GetOut    *entity.User
	GetErr    error

	WantResp dto.GetUserResponse
	WantErr  error
}

var testGetUID = uuid.New()

var GetUserTestCases = []GetUserTestCase{
	{
		Name: "invalid user id",
		Input: dto.GetUserRequest{
			ID: uuid.Nil,
		},
		ExceptGet: false,
		WantErr:   uc_errors.InvalidUserID,
	},

	{
		Name: "user not found",
		Input: dto.GetUserRequest{
			ID: testGetUID,
		},
		ExceptGet: true,
		GetErr:    sql.ErrNoRows,
		WantErr:   uc_errors.UserNotFoundError,
	},

	{
		Name: "repository error",
		Input: dto.GetUserRequest{
			ID: testGetUID,
		},
		ExceptGet: true,
		GetErr:    errors.New("db error"),
		WantErr:   uc_errors.GetUserError,
	},

	{
		Name: "success",
		Input: dto.GetUserRequest{
			ID: testGetUID,
		},
		ExceptGet: true,
		GetOut:    &entity.User{ID: testGetUID},
		WantResp:  dto.GetUserResponse{User: dto.UserResponse{ID: testGetUID}},
	},
}

func TestGetUserUC(t *testing.T) {
	for _, tt := range GetUserTestCases {
		t.Run(tt.Name, func(t *testing.T) {
			repo := new(mocks.UserRepository)
			uc := usecase.NewGetUserUC(repo)

			if tt.ExceptGet {
				repo.On("GetByID", mock.Anything, mock.Anything).
					Return(tt.GetOut, tt.GetErr)
			}

			resp, err := uc.Execute(context.Background(), tt.Input)

			if tt.WantErr != nil {
				assert.Error(t, err)
				assert.True(t, errors.Is(err, tt.WantErr),
					"expected error '%v' but got '%v'", tt.WantErr, err)
			} else {
				assert.NoError(t, err)
				assert.Equal(t, tt.WantResp, resp)
			}

			repo.AssertExpectations(t)
		})
	}
}
