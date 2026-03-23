package usecase

import (
	"YouSightSeeing/backend/internal/app/dto"
	"YouSightSeeing/backend/internal/app/uc_errors"
	"context"
	"math"
	"sort"
)

const (
	visitDurationPerPlaceSeconds = 30 * 60

	// Для MVP:
	// - точка не должна быть слишком далеко от стартовой позиции
	// - новые точки стараемся брать рядом с уже собранным кластером маршрута
	maxPointDistanceFromStartM   = 2500.0
	maxPointDistanceFromClusterM = 1200.0
)

type GenerateRouteUC struct {
	searchPlacesUC SearchPlacesUseCase
	calculateUC    CalculateRouteUseCase
}

func NewGenerateRouteUC(
	searchPlacesUC SearchPlacesUseCase,
	calculateUC CalculateRouteUseCase,
) *GenerateRouteUC {
	return &GenerateRouteUC{
		searchPlacesUC: searchPlacesUC,
		calculateUC:    calculateUC,
	}
}

func (uc *GenerateRouteUC) Execute(
	ctx context.Context,
	req dto.GenerateRouteRequest,
) (dto.GenerateRouteResponse, error) {
	if req.StartLat < -90 || req.StartLat > 90 || req.StartLon < -180 || req.StartLon > 180 {
		return dto.GenerateRouteResponse{}, uc_errors.ErrInvalidCoordinates
	}

	if req.Radius <= 0 {
		req.Radius = 2000
	}
	if req.MaxPlaces <= 0 {
		req.MaxPlaces = 5
	}
	if req.MaxPlaces > 10 {
		req.MaxPlaces = 10
	}

	categories := normalizeCategories(req.Categories, req.IncludeFood)

	searchResp, err := uc.searchPlacesUC.Execute(ctx, dto.SearchPlacesRequest{
		Lat:        req.StartLat,
		Lon:        req.StartLon,
		Radius:     req.Radius,
		Categories: categories,
		Limit:      req.MaxPlaces * 3,
	})
	if err != nil {
		return dto.GenerateRouteResponse{}, err
	}

	selectedPlaces := selectPlacesForRoute(
		searchResp.Places,
		req.Categories,
		req.StartLat,
		req.StartLon,
		req.MaxPlaces,
		req.IncludeFood,
	)

	if len(selectedPlaces) == 0 {
		return dto.GenerateRouteResponse{}, uc_errors.ErrSearchPlacesFailed
	}

	coordinates := make([][]float64, 0, len(selectedPlaces)+1)
	coordinates = append(coordinates, []float64{req.StartLon, req.StartLat})

	for _, place := range selectedPlaces {
		if len(place.Coordinates) < 2 {
			continue
		}
		coordinates = append(coordinates, place.Coordinates)
	}

	if len(coordinates) < 2 {
		return dto.GenerateRouteResponse{}, uc_errors.ErrInvalidRoutePoints
	}

	routeResp, err := uc.calculateUC.Execute(ctx, dto.CalculateRouteRequest{
		Coordinates:   coordinates,
		Profile:       "foot-walking",
		Preference:    "",
		OptimizeOrder: true,
	})
	if err != nil {
		return dto.GenerateRouteResponse{}, err
	}

	// MVP-заглушка:
	// итоговое время = время ходьбы + 30 минут на каждую точку.
	estimatedDuration := routeResp.Duration + float64(len(selectedPlaces)*visitDurationPerPlaceSeconds)

	return dto.GenerateRouteResponse{
		Places: selectedPlaces,
		Route: dto.RouteResult{
			Points:   routeResp.Points,
			Distance: routeResp.Distance,
			Duration: estimatedDuration,
		},
	}, nil
}

func normalizeCategories(categories []string, includeFood bool) []string {
	if len(categories) == 0 {
		categories = []string{"tourism.sights", "leisure.park"}
	}

	result := make([]string, 0, len(categories)+1)
	used := make(map[string]struct{}, len(categories)+1)

	for _, c := range categories {
		if c == "" {
			continue
		}
		if _, exists := used[c]; exists {
			continue
		}
		used[c] = struct{}{}
		result = append(result, c)
	}

	if includeFood && !containsCategory(result, "catering.cafe") {
		result = append(result, "catering.cafe")
	}

	return result
}

func containsCategory(categories []string, target string) bool {
	for _, c := range categories {
		if c == target {
			return true
		}
	}
	return false
}

func selectPlacesForRoute(
	places []dto.Place,
	requestedCategories []string,
	startLat float64,
	startLon float64,
	maxPlaces int,
	includeFood bool,
) []dto.Place {
	if len(places) == 0 || maxPlaces <= 0 {
		return []dto.Place{}
	}

	uniquePlaces := deduplicatePlaces(places)

	var foodPlaces []dto.Place
	regularBuckets := make(map[string][]dto.Place)

	normalizedRequested := normalizeRequestedRegularCategories(requestedCategories)

	for _, place := range uniquePlaces {
		if len(place.Coordinates) < 2 {
			continue
		}

		distFromStart := distanceMeters(
			startLat, startLon,
			place.Coordinates[1], place.Coordinates[0],
		)

		if distFromStart > maxPointDistanceFromStartM {
			continue
		}

		if hasFoodCategory(place.Categories) {
			if includeFood {
				foodPlaces = append(foodPlaces, place)
			}
			continue
		}

		categoryKey := matchRequestedCategory(place.Categories, normalizedRequested)
		if categoryKey == "" {
			continue
		}

		regularBuckets[categoryKey] = append(regularBuckets[categoryKey], place)
	}

	for key := range regularBuckets {
		sort.Slice(regularBuckets[key], func(i, j int) bool {
			di := distanceMeters(
				startLat, startLon,
				regularBuckets[key][i].Coordinates[1], regularBuckets[key][i].Coordinates[0],
			)
			dj := distanceMeters(
				startLat, startLon,
				regularBuckets[key][j].Coordinates[1], regularBuckets[key][j].Coordinates[0],
			)
			return di < dj
		})
	}

	sort.Slice(foodPlaces, func(i, j int) bool {
		di := distanceMeters(
			startLat, startLon,
			foodPlaces[i].Coordinates[1], foodPlaces[i].Coordinates[0],
		)
		dj := distanceMeters(
			startLat, startLon,
			foodPlaces[j].Coordinates[1], foodPlaces[j].Coordinates[0],
		)
		return di < dj
	})

	selected := make([]dto.Place, 0, maxPlaces)

	foodSlots := 0
	if includeFood && len(foodPlaces) > 0 && maxPlaces > 1 {
		foodSlots = 1
	}

	regularLimit := maxPlaces - foodSlots
	if regularLimit < 0 {
		regularLimit = 0
	}

	categoryOrder := orderedCategoryKeys(normalizedRequested, regularBuckets)
	if len(categoryOrder) == 0 {
		categoryOrder = mapKeys(regularBuckets)
	}

	selectedRegular := pickBalancedRegularPlaces(
		regularBuckets,
		categoryOrder,
		startLat,
		startLon,
		regularLimit,
	)

	selected = append(selected, selectedRegular...)

	if foodSlots == 1 && len(foodPlaces) > 0 && len(selected) < maxPlaces {
		bestFood := pickNearestFoodToCluster(foodPlaces, startLat, startLon, selected)
		if bestFood != nil {
			selected = append(selected, *bestFood)
		}
	}

	return selected
}

func pickBalancedRegularPlaces(
	buckets map[string][]dto.Place,
	categoryOrder []string,
	startLat float64,
	startLon float64,
	limit int,
) []dto.Place {
	if limit <= 0 {
		return []dto.Place{}
	}

	selected := make([]dto.Place, 0, limit)
	usedIDs := make(map[string]struct{})
	indexes := make(map[string]int, len(categoryOrder))

	for len(selected) < limit {
		progress := false

		for _, category := range categoryOrder {
			if len(selected) >= limit {
				break
			}

			places := buckets[category]
			for indexes[category] < len(places) {
				candidate := places[indexes[category]]
				indexes[category]++

				if candidate.PlaceID != "" {
					if _, exists := usedIDs[candidate.PlaceID]; exists {
						continue
					}
				}

				if !isNearCurrentCluster(candidate, startLat, startLon, selected) {
					continue
				}

				selected = append(selected, candidate)
				if candidate.PlaceID != "" {
					usedIDs[candidate.PlaceID] = struct{}{}
				}
				progress = true
				break
			}
		}

		if !progress {
			break
		}
	}

	return selected
}

func pickNearestFoodToCluster(
	foodPlaces []dto.Place,
	startLat float64,
	startLon float64,
	selected []dto.Place,
) *dto.Place {
	for i := range foodPlaces {
		if isNearCurrentCluster(foodPlaces[i], startLat, startLon, selected) {
			return &foodPlaces[i]
		}
	}
	return nil
}

func isNearCurrentCluster(
	place dto.Place,
	startLat float64,
	startLon float64,
	selected []dto.Place,
) bool {
	if len(place.Coordinates) < 2 {
		return false
	}

	placeLat := place.Coordinates[1]
	placeLon := place.Coordinates[0]

	if len(selected) == 0 {
		return distanceMeters(startLat, startLon, placeLat, placeLon) <= maxPointDistanceFromStartM
	}

	clusterLat, clusterLon := clusterCenter(startLat, startLon, selected)

	return distanceMeters(clusterLat, clusterLon, placeLat, placeLon) <= maxPointDistanceFromClusterM
}

func clusterCenter(startLat float64, startLon float64, selected []dto.Place) (float64, float64) {
	sumLat := startLat
	sumLon := startLon
	count := 1.0

	for _, p := range selected {
		if len(p.Coordinates) < 2 {
			continue
		}
		sumLat += p.Coordinates[1]
		sumLon += p.Coordinates[0]
		count++
	}

	return sumLat / count, sumLon / count
}

func deduplicatePlaces(places []dto.Place) []dto.Place {
	result := make([]dto.Place, 0, len(places))
	usedIDs := make(map[string]struct{})

	for _, place := range places {
		if len(place.Coordinates) < 2 {
			continue
		}

		if place.PlaceID != "" {
			if _, exists := usedIDs[place.PlaceID]; exists {
				continue
			}
			usedIDs[place.PlaceID] = struct{}{}
		}

		result = append(result, place)
	}

	return result
}

func normalizeRequestedRegularCategories(categories []string) []string {
	if len(categories) == 0 {
		return []string{"tourism.sights", "leisure.park"}
	}

	result := make([]string, 0, len(categories))
	used := make(map[string]struct{})

	for _, c := range categories {
		if c == "" || hasCategoryPrefix(c, "catering.") {
			continue
		}
		if _, exists := used[c]; exists {
			continue
		}
		used[c] = struct{}{}
		result = append(result, c)
	}

	if len(result) == 0 {
		return []string{"tourism.sights", "leisure.park"}
	}

	return result
}

func matchRequestedCategory(placeCategories []string, requested []string) string {
	for _, req := range requested {
		for _, actual := range placeCategories {
			if actual == req || hasCategoryPrefix(actual, req+".") || hasCategoryPrefix(req, actual+".") {
				return req
			}
		}
	}
	return ""
}

func orderedCategoryKeys(requested []string, buckets map[string][]dto.Place) []string {
	result := make([]string, 0, len(requested))
	for _, c := range requested {
		if len(buckets[c]) > 0 {
			result = append(result, c)
		}
	}
	return result
}

func mapKeys(m map[string][]dto.Place) []string {
	keys := make([]string, 0, len(m))
	for k := range m {
		keys = append(keys, k)
	}
	sort.Strings(keys)
	return keys
}

func hasFoodCategory(categories []string) bool {
	for _, c := range categories {
		if hasCategoryPrefix(c, "catering.") {
			return true
		}
	}
	return false
}

func hasCategoryPrefix(value string, prefix string) bool {
	return len(value) >= len(prefix) && value[:len(prefix)] == prefix
}

func distanceMeters(lat1, lon1, lat2, lon2 float64) float64 {
	const earthRadius = 6371000.0

	lat1Rad := lat1 * math.Pi / 180
	lon1Rad := lon1 * math.Pi / 180
	lat2Rad := lat2 * math.Pi / 180
	lon2Rad := lon2 * math.Pi / 180

	dLat := lat2Rad - lat1Rad
	dLon := lon2Rad - lon1Rad

	a := math.Sin(dLat/2)*math.Sin(dLat/2) +
		math.Cos(lat1Rad)*math.Cos(lat2Rad)*
			math.Sin(dLon/2)*math.Sin(dLon/2)

	c := 2 * math.Atan2(math.Sqrt(a), math.Sqrt(1-a))
	return earthRadius * c
}
