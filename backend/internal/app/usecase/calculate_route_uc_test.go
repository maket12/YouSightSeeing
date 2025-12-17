package usecase_test

import (
	"YouSightSeeing/backend/internal/app/dto"
	"YouSightSeeing/backend/internal/app/uc_errors"
	"YouSightSeeing/backend/internal/app/usecase"
	"YouSightSeeing/backend/internal/domain/entity"
	"YouSightSeeing/backend/internal/domain/port/mocks"
	"context"
	"errors"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/mock"
)

func TestCalculateRouteUC(t *testing.T) {
	type CalculateRouteTestCase struct {
		Name     string
		Input    dto.CalculateRouteRequest
		CallRepo bool
		RepoErr  error
		WantErr  error
	}

	var (
		testCoords              = make([][]float64, 2)
		calculateRouteTestCases = []CalculateRouteTestCase{
			{
				Name: "not enough coords",
				Input: dto.CalculateRouteRequest{
					Coordinates: testCoords[:1], // берём только 1 точку
				},
				CallRepo: false,
				WantErr:  uc_errors.ErrInvalidRoutePoints,
			},
			{
				Name: "repository error",
				Input: dto.CalculateRouteRequest{
					Coordinates: testCoords,
				},
				CallRepo: true,
				RepoErr:  errors.New("failed to calculate"),
				WantErr:  uc_errors.ErrRouteCalculationFailed,
			},
			{
				Name: "repository error",
				Input: dto.CalculateRouteRequest{
					Coordinates: testCoords,
				},
				CallRepo: true,
				RepoErr:  nil,
				WantErr:  nil,
			},
		}
	)
	for _, tt := range calculateRouteTestCases {
		t.Run(tt.Name, func(t *testing.T) {
			repo := new(mocks.RouteCalculator)
			uc := usecase.NewCalculateRouteUC(repo)

			if tt.CallRepo {
				repo.On("CalculateRoute", mock.Anything, mock.Anything).
					Return(&entity.Route{}, tt.RepoErr)
			}

			_, err := uc.Execute(context.Background(), tt.Input)

			if tt.WantErr != nil {
				assert.Error(t, err)
				assert.True(t, errors.Is(err, tt.WantErr),
					"expected error '%v' but got '%v'", tt.WantErr, err)
			} else {
				assert.NoError(t, err)
			}

			repo.AssertExpectations(t)
		})
	}
}
