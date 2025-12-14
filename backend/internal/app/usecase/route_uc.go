package usecase

import (
	"context"
	"fmt"

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

func (uc *RouteUC) Execute(ctx context.Context, req dto.RouteRequest) (*dto.RouteResponse, error) {
	//validation
	if len(req.Points) < 2 {
		return nil, uc_errors.ErrInvalidRoutePoints
	}
	//меняем местами широту и долготу, потому что ors требует формат [lon, lat]
	coordinates := make([][]float64, 0, len(req.Points))
	for _, p := range req.Points {
		coordinates = append(coordinates, []float64{p.Lon, p.Lat})
	}

	//запрос к сущности ors
	orsReq := entity.ORSRequest{
		Coordinates:  coordinates,
		Instructions: false,
		Geometry:     true,
	}

	//вызываем адаптер
	rawResponse, err := uc.RouteService.CalculateRoute(ctx, orsReq)
	if err != nil {
		//Логируем реальную ошибку, но пользователю отдаем общую
		fmt.Printf("ORS Error: %v\n", err)
		return nil, uc_errors.ErrRouteCalculationFailed
	}

	respDTO, err := parseORSResponse(rawResponse)
	if err != nil {
		fmt.Printf("Parse error: %v\n", err)
		return nil, uc_errors.ErrRouteCalculationFailed
	}
	return respDTO, nil
}

// парсим json ответ от ors
func parseORSResponse(data map[string]interface{}) (*dto.RouteResponse, error) {
	features, ok := data["features"].([]interface{})
	if !ok || len(features) == 0 {
		return nil, fmt.Errorf("no features in response")
	}

	feature := features[0].(map[string]interface{})

	//достаём линию маршрута
	geometry, ok := feature["geometry"].(map[string]interface{})
	if !ok {
		return nil, fmt.Errorf("no geometry")
	}

	rawCoords, ok := geometry["coordinates"].([]interface{})
	if !ok {
		return nil, fmt.Errorf("no coordinates")
	}

	//преобразуем [][]interface{} -> []dto.Point
	routePath := make([]dto.Point, 0, len(rawCoords))
	for _, rc := range rawCoords {
		pointPair, ok := rc.([]interface{})
		if ok && len(pointPair) >= 2 {
			//ORS возвращает [lon, lat], нам нужно в DTO [lat, lon]
			lon := pointPair[0].(float64)
			lat := pointPair[1].(float64)
			routePath = append(routePath, dto.Point{Lat: lat, Lon: lon})
		}
	}

	//достаём статистику (время, дистанцию)
	var distance, duration float64
	if props, ok := feature["properties"].(map[string]interface{}); ok {
		if summary, ok := props["summary"].(map[string]interface{}); ok {
			if d, ok := summary["distance"].(float64); ok {
				distance = d
			}
			if t, ok := summary["duration"].(float64); ok {
				duration = t
			}
		}
	}
	return &dto.RouteResponse{
		Route:    routePath,
		Distance: distance,
		Duration: duration,
	}, nil
}
