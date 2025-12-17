package usecase

import (
	"context"
	"math"

	"YouSightSeeing/backend/internal/app/dto"
	"YouSightSeeing/backend/internal/app/uc_errors"
	"YouSightSeeing/backend/internal/domain/entity"
	"YouSightSeeing/backend/internal/domain/port"
)

type RouteUC struct {
	RouteService port.RouteService
}

func NewRouteUC(service port.RouteService) *RouteUC {
	return &RouteUC{
		RouteService: service,
	}
}

type orsResponseRaw struct {
	Features []struct {
		Geometry struct {
			Coordinates [][]float64 `json:"coordinates"`
		} `json:"geometry"`
		Properties struct {
			Summary struct {
				Distance float64 `json:"distance"`
				Duration float64 `json:"duration"`
			} `json:"summary"`
		} `json:"properties"`
	} `json:"features"`
}

func (uc *RouteUC) Execute(ctx context.Context, req dto.RouteRequest) (dto.RouteResponse, error) {
	//validation
	if len(req.Coordinates) < 2 {
		return dto.RouteResponse{}, uc_errors.ErrInvalidRoutePoints
	}

	sortedPoints := sortPointsNearestNeighbor(req.Coordinates)

	orsReq := entity.ORSRequest{
		Coordinates:  sortedPoints,
		Instructions: false,
		Geometry:     true,
	}

	routeEntity, err := uc.RouteService.CalculateRoute(ctx, orsReq)
	if err != nil {
		return dto.RouteResponse{}, uc_errors.Wrap(uc_errors.ErrRouteCalculationFailed, err)
	}

	return dto.RouteResponse{
		Route:    routeEntity.Geometry,
		Distance: routeEntity.Distance,
		Duration: routeEntity.Duration,
	}, nil
}

func sortPointsNearestNeighbor(points [][]float64) [][]float64 {
	if len(points) <= 2 {
		return points
	}

	unvisited := make([][]float64, len(points))
	copy(unvisited, points)

	path := make([][]float64, 0, len(points))
	current := unvisited[0]
	path = append(path, current)

	unvisited = unvisited[1:]

	for len(unvisited) > 0 {
		nearestIndex := -1
		minDist := math.MaxFloat64

		for i, p := range unvisited {
			dist := haversine(current[1], current[0], p[1], p[0])
			if dist < minDist {
				minDist = dist
				nearestIndex = i
			}
		}

		current = unvisited[nearestIndex]
		path = append(path, current)

		unvisited = append(unvisited[:nearestIndex], unvisited[nearestIndex+1:]...)
	}

	return path
}

func haversine(lat1, lon1, lat2, lon2 float64) float64 {
	const R = 6371
	dLat := (lat2 - lat1) * (math.Pi / 180.0)
	dLon := (lon2 - lon1) * (math.Pi / 180.0)

	lat1Rad := lat1 * (math.Pi / 180.0)
	lat2Rad := lat2 * (math.Pi / 180.0)

	a := math.Sin(dLat/2)*math.Sin(dLat/2) +
		math.Sin(dLon/2)*math.Sin(dLon/2)*math.Cos(lat1Rad)*math.Cos(lat2Rad)
	c := 2 * math.Atan2(math.Sqrt(a), math.Sqrt(1-a))

	return R * c
}
