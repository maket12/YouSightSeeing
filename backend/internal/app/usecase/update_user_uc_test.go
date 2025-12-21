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

func TestUpdateUserUC(t *testing.T) {
	type UpdateUserTestCase struct {
		Name  string
		Input dto.UpdateUserRequest

		ExpectGet bool
		GetOut    *entity.User
		GetErr    error

		ExpectUpdate bool
		UpdateErr    error

		WantResp dto.UpdateUserResponse
		WantErr  error
	}

	var (
		testUpdateUID       = uuid.New()
		testUpdateEmail     = "new123@gmail.com"
		testUpdateFName     = "Vladimir Ziabkin"
		testUpdatePicture   = "newp123.jpg"
		testUpdateFirstName = "Vladimir"
		testUpdateLastName  = "Ziabkin"
	)

	var UpdateUserTestCases = []UpdateUserTestCase{
		{
			Name: "invalid user id",
			Input: dto.UpdateUserRequest{
				ID: uuid.Nil,
			},

			ExpectGet:    false,
			ExpectUpdate: false,

			WantErr: uc_errors.InvalidUserID,
		},

		{
			Name: "nothing to update",
			Input: dto.UpdateUserRequest{
				ID: testUpdateUID,
			},
			ExpectGet:    false,
			ExpectUpdate: false,
			WantResp: dto.UpdateUserResponse{
				ID:      testUpdateUID,
				Updated: false,
			},
			WantErr: nil,
		},

		{
			Name: "get: user not found",
			Input: dto.UpdateUserRequest{
				ID:    testUpdateUID,
				Email: &testUpdateEmail,
			},
			ExpectGet:    true,
			ExpectUpdate: false,
			GetErr:       sql.ErrNoRows,
			WantErr:      uc_errors.UserNotFoundError,
		},

		{
			Name: "get: repository error",
			Input: dto.UpdateUserRequest{
				ID:    testUpdateUID,
				Email: &testUpdateEmail,
			},
			ExpectGet:    true,
			ExpectUpdate: false,
			GetErr:       errors.New("db error"),
			WantErr:      uc_errors.GetUserError,
		},

		{
			Name: "update: user not found",
			Input: dto.UpdateUserRequest{
				ID:    testUpdateUID,
				Email: &testUpdateEmail,
			},
			ExpectGet:    true,
			ExpectUpdate: true,
			GetErr:       nil,
			GetOut: &entity.User{
				ID:    testUpdateUID,
				Email: testUpdateEmail,
			},
			UpdateErr: sql.ErrNoRows,
			WantErr:   uc_errors.UserNotFoundError,
		},

		{
			Name: "update: repository error",
			Input: dto.UpdateUserRequest{
				ID:    testUpdateUID,
				Email: &testUpdateEmail,
			},
			ExpectGet:    true,
			ExpectUpdate: true,
			GetErr:       nil,
			GetOut: &entity.User{
				ID:    testUpdateUID,
				Email: testUpdateEmail,
			},
			UpdateErr: errors.New("db error"),
			WantErr:   uc_errors.UpdateUserError,
		},

		{
			Name: "success",
			Input: dto.UpdateUserRequest{
				ID:        testUpdateUID,
				Email:     &testUpdateEmail,
				FullName:  &testUpdateFName,
				Picture:   &testUpdatePicture,
				FirstName: &testUpdateFirstName,
				LastName:  &testUpdateLastName,
			},
			ExpectGet:    true,
			ExpectUpdate: true,
			GetOut: &entity.User{
				ID: testUpdateUID,
			},
			WantResp: dto.UpdateUserResponse{
				ID:      testUpdateUID,
				Updated: true,
				User: dto.UserResponse{
					ID:        testUpdateUID,
					Email:     testUpdateEmail,
					FullName:  &testUpdateFName,
					Picture:   &testUpdatePicture,
					FirstName: &testUpdateFirstName,
					LastName:  &testUpdateLastName,
				},
			},
		},
	}

	for _, tt := range UpdateUserTestCases {
		t.Run(tt.Name, func(t *testing.T) {
			repo := new(mocks.UserRepository)
			uc := usecase.NewUpdateUserUC(repo)

			if tt.ExpectGet {
				repo.On("GetByID", mock.Anything, mock.Anything).Return(tt.GetOut, tt.GetErr)
			}
			if tt.ExpectUpdate {
				repo.On("Update", mock.Anything, mock.Anything).Return(tt.UpdateErr)
			}

			resp, err := uc.Execute(context.Background(), tt.Input)

			if tt.WantErr != nil {
				assert.Error(t, err)
				assert.True(t, errors.Is(err, tt.WantErr), "expected error '%v' but got '%v'", tt.WantErr, err)
			} else {
				assert.NoError(t, err)
				assert.Equal(t, tt.WantResp, resp)
			}

			repo.AssertExpectations(t)
		})
	}
}
