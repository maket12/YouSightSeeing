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

func TestUpdatePictureUserUC(t *testing.T) {
	type UpdateUserPictureTestCase struct {
		Name  string
		Input dto.UpdateUserPictureRequest

		ExpectGet bool
		GetOut    *entity.User
		GetErr    error

		ExpectUpdate bool
		UpdateErr    error

		WantResp dto.UpdateUserPictureResponse
		WantErr  error
	}

	var (
		testUpdatePictureUID     = uuid.New()
		testUpdatePicturePicture = "my-photo.jpg"
	)

	var UpdateUserPictureTestCases = []UpdateUserPictureTestCase{
		{
			Name: "invalid user id",
			Input: dto.UpdateUserPictureRequest{
				ID: uuid.Nil,
			},

			ExpectGet:    false,
			ExpectUpdate: false,

			WantErr: uc_errors.InvalidUserID,
		},

		{
			Name: "get: user not found",
			Input: dto.UpdateUserPictureRequest{
				ID:      testUpdatePictureUID,
				Picture: testUpdatePicturePicture,
			},
			ExpectGet:    true,
			ExpectUpdate: false,
			GetErr:       sql.ErrNoRows,
			WantErr:      uc_errors.UserNotFoundError,
		},

		{
			Name: "get: repository error",
			Input: dto.UpdateUserPictureRequest{
				ID:      testUpdatePictureUID,
				Picture: testUpdatePicturePicture,
			},
			ExpectGet:    true,
			ExpectUpdate: false,
			GetErr:       errors.New("db error"),
			WantErr:      uc_errors.GetUserError,
		},

		{
			Name: "update: user not found",
			Input: dto.UpdateUserPictureRequest{
				ID:      testUpdatePictureUID,
				Picture: testUpdatePicturePicture,
			},
			ExpectGet:    true,
			ExpectUpdate: true,
			GetErr:       nil,
			GetOut: &entity.User{
				ID:      testUpdatePictureUID,
				Picture: &testUpdatePicturePicture,
			},
			UpdateErr: sql.ErrNoRows,
			WantErr:   sql.ErrNoRows,
		},

		{
			Name: "update: repository error",
			Input: dto.UpdateUserPictureRequest{
				ID:      testUpdatePictureUID,
				Picture: testUpdatePicturePicture,
			},
			ExpectGet:    true,
			ExpectUpdate: true,
			GetErr:       nil,
			GetOut: &entity.User{
				ID:      testUpdatePictureUID,
				Picture: &testUpdatePicturePicture,
			},
			UpdateErr: errors.New("db error"),
			WantErr:   uc_errors.UpdateUserPictureError,
		},

		{
			Name: "success",
			Input: dto.UpdateUserPictureRequest{
				ID:      testUpdatePictureUID,
				Picture: testUpdatePicturePicture,
			},
			ExpectGet:    true,
			ExpectUpdate: true,
			GetOut: &entity.User{
				ID:      testUpdatePictureUID,
				Picture: &testUpdatePicturePicture,
			},
			WantResp: dto.UpdateUserPictureResponse{
				ID:      testUpdatePictureUID,
				Updated: true,
				User: dto.UserResponse{
					ID:      testUpdatePictureUID,
					Picture: &testUpdatePicturePicture,
				},
			},
		},
	}

	for _, tt := range UpdateUserPictureTestCases {
		t.Run(tt.Name, func(t *testing.T) {
			repo := new(mocks.UserRepository)
			uc := usecase.NewUpdateUserPictureUC(repo)

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
