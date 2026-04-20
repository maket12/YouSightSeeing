package usecase

import (
	"YouSightSeeing/backend/internal/app/dto"
	"YouSightSeeing/backend/internal/app/uc_errors"
	"YouSightSeeing/backend/internal/domain/entity"
	"YouSightSeeing/backend/internal/domain/port"
	"context"
	"math"
	"sort"
	"strings"

	"github.com/google/uuid"
)

const (
	visitDurationPerPlaceSeconds = 30 * 60

	// Для recommendation:
	// - точка не должна быть слишком далеко от стартовой позиции
	// - новые точки стараемся брать рядом с уже собранным кластером маршрута
	// - почти одинаковые POI не должны попадать в маршрут одновременно
	maxPointDistanceFromStartM   = 2500.0
	maxPointDistanceFromClusterM = 1200.0
	maxNearDuplicateDistanceM    = 120.0
)

type GenerateRouteUC struct {
	searchPlacesUC   SearchPlacesUseCase
	calculateUC      CalculateRouteUseCase
	matrixCalculator port.RouteMatrixCalculator
	preferencesRepo  port.UserCategoryPreferencesRepository
}

func NewGenerateRouteUC(
	searchPlacesUC SearchPlacesUseCase,
	calculateUC CalculateRouteUseCase,
	matrixCalculator port.RouteMatrixCalculator,
	preferencesRepo port.UserCategoryPreferencesRepository,
) *GenerateRouteUC {
	return &GenerateRouteUC{
		searchPlacesUC:   searchPlacesUC,
		calculateUC:      calculateUC,
		matrixCalculator: matrixCalculator,
		preferencesRepo:  preferencesRepo,
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
	if req.DurationMinutes <= 0 {
		req.DurationMinutes = 180
	}

	timeBudgetSeconds := float64(req.DurationMinutes * 60)

	preferenceWeights, requestedCategories := uc.resolvePreferenceWeights(
		ctx,
		req.UserID,
		req.Categories,
		req.IncludeFood,
	)

	categories := normalizeCategories(requestedCategories, req.IncludeFood)

	searchResp, err := uc.searchPlacesUC.Execute(ctx, dto.SearchPlacesRequest{
		Lat:        req.StartLat,
		Lon:        req.StartLon,
		Radius:     req.Radius,
		Categories: categories,
		Limit:      max(req.MaxPlaces*4, 20),
	})

	if err != nil {
		return dto.GenerateRouteResponse{}, err
	}

	candidates := buildRecommendationCandidates(
		searchResp.Places,
		requestedCategories,
		preferenceWeights,
		req.StartLat,
		req.StartLon,
		req.Radius,
		req.IncludeFood,
	)

	if len(candidates) == 0 {
		return dto.GenerateRouteResponse{}, uc_errors.ErrSearchPlacesFailed
	}

	matrixLocations := make([][]float64, 0, len(candidates)+1)
	matrixLocations = append(matrixLocations, []float64{req.StartLon, req.StartLat})

	for i := range candidates {
		candidates[i].MatrixIndex = len(matrixLocations)
		matrixLocations = append(matrixLocations, candidates[i].Place.Coordinates)
	}

	matrixResp, err := uc.matrixCalculator.CalculateMatrix(ctx, entity.ORSMatrixRequest{
		Locations: matrixLocations,
		Metrics:   []string{"duration"},
	})
	if err != nil {
		return dto.GenerateRouteResponse{}, uc_errors.Wrap(uc_errors.ErrRouteMatrixFailed, err)
	}

	selectedPlaces := greedySelectPlaces(
		candidates,
		matrixResp,
		req.MaxPlaces,
		timeBudgetSeconds,
		req.IncludeFood,
	)

	if len(selectedPlaces) == 0 {
		return dto.GenerateRouteResponse{}, uc_errors.ErrSearchPlacesFailed
	}

	selectedPlaces = localImproveSelection(
		selectedPlaces,
		candidates,
		matrixResp,
		timeBudgetSeconds,
		req.IncludeFood,
	)

	finalPlaces, routeResp, estimatedDuration, err := uc.buildRouteWithinBudget(
		ctx,
		req.StartLat,
		req.StartLon,
		selectedPlaces,
		timeBudgetSeconds,
	)
	if err != nil {
		return dto.GenerateRouteResponse{}, err
	}

	return dto.GenerateRouteResponse{
		Places: finalPlaces,
		Route: dto.RouteResult{
			Points:   routeResp.Points,
			Distance: routeResp.Distance,
			Duration: estimatedDuration,
		},
	}, nil
}

type recommendationCandidate struct {
	Place        dto.Place
	BaseScore    float64
	PrimaryClass string
	IsFood       bool
	MatrixIndex  int
}

func buildRecommendationCandidates(
	places []dto.Place,
	requestedCategories []string,
	preferenceWeights map[string]float64,
	startLat float64,
	startLon float64,
	radius int,
	includeFood bool,
) []recommendationCandidate {
	uniquePlaces := deduplicatePlaces(places)
	normalizedRequested := normalizeRequestedRegularCategories(requestedCategories)

	result := make([]recommendationCandidate, 0, len(uniquePlaces))

	for _, place := range uniquePlaces {
		if len(place.Coordinates) < 2 {
			continue
		}

		distFromStart := distanceMeters(
			startLat, startLon,
			place.Coordinates[1], place.Coordinates[0],
		)

		if distFromStart > float64(radius) {
			continue
		}

		isFood := hasFoodCategory(place.Categories)
		if isFood && !includeFood {
			continue
		}

		primaryClass := detectPrimaryClass(place, normalizedRequested, isFood)
		if primaryClass == "" && !isFood {
			continue
		}

		if isLowQualityPlace(place) {
			continue
		}

		baseScore := computeBaseScore(place, preferenceWeights, startLat, startLon, radius, isFood)

		result = append(result, recommendationCandidate{
			Place:        place,
			BaseScore:    baseScore,
			PrimaryClass: primaryClass,
			IsFood:       isFood,
		})
	}

	sort.Slice(result, func(i, j int) bool {
		return result[i].BaseScore > result[j].BaseScore
	})

	return result
}

func (uc *GenerateRouteUC) resolvePreferenceWeights(
	ctx context.Context,
	userID uuid.UUID,
	explicitCategories []string,
	includeFood bool,
) (map[string]float64, []string) {
	storedWeights := make(map[string]float64)

	if uc.preferencesRepo != nil && userID != uuid.Nil {
		if items, err := uc.preferencesRepo.GetByUserID(ctx, userID); err == nil {
			for _, item := range items {
				storedWeights[item.Category] = item.Weight
			}
		}
	}

	weights := buildPreferenceWeights(nil, includeFood)

	for category, weight := range storedWeights {
		weights[category] = weight
	}

	if len(explicitCategories) > 0 {
		requested := normalizeRequestedRegularCategories(explicitCategories)
		for _, category := range requested {
			weights[category] = 1.0
		}
		if includeFood {
			weights["catering.cafe"] = maxFloat(weights["catering.cafe"], 0.45)
		}
		return weights, requested
	}

	requested := deriveRequestedCategoriesFromWeights(weights)
	if len(requested) == 0 {
		requested = []string{"tourism.sights", "leisure.park"}
	}

	return weights, requested
}

func deriveRequestedCategoriesFromWeights(weights map[string]float64) []string {
	type pair struct {
		Category string
		Weight   float64
	}

	items := make([]pair, 0, len(weights))
	for category, weight := range weights {
		if hasCategoryPrefix(category, "catering.") {
			continue
		}
		items = append(items, pair{
			Category: category,
			Weight:   weight,
		})
	}

	sort.Slice(items, func(i, j int) bool {
		if items[i].Weight == items[j].Weight {
			return items[i].Category < items[j].Category
		}
		return items[i].Weight > items[j].Weight
	})

	result := make([]string, 0, 3)
	for _, item := range items {
		if item.Weight < 0.55 {
			continue
		}
		result = append(result, item.Category)
		if len(result) >= 3 {
			break
		}
	}

	return result
}

func maxFloat(a, b float64) float64 {
	if a > b {
		return a
	}
	return b
}

func buildPreferenceWeights(requestedCategories []string, includeFood bool) map[string]float64 {
	weights := map[string]float64{
		"tourism.sights":       0.9,
		"leisure.park":         0.8,
		"entertainment.museum": 0.75,
	}

	for _, category := range requestedCategories {
		weights[category] = 1.0
	}

	if includeFood {
		weights["catering.cafe"] = 0.45
	}

	return weights
}

func detectPrimaryClass(place dto.Place, requestedCategories []string, isFood bool) string {
	if isFood {
		return "food"
	}

	for _, req := range requestedCategories {
		for _, actual := range place.Categories {
			if actual == req || hasCategoryPrefix(actual, req+".") || hasCategoryPrefix(req, actual+".") {
				return req
			}
		}
	}

	for _, actual := range place.Categories {
		if hasCategoryPrefix(actual, "tourism.sights") {
			return "tourism.sights"
		}
		if hasCategoryPrefix(actual, "leisure.park") {
			return "leisure.park"
		}
		if hasCategoryPrefix(actual, "entertainment.museum") {
			return "entertainment.museum"
		}
	}

	return ""
}

func computeBaseScore(
	place dto.Place,
	preferenceWeights map[string]float64,
	startLat float64,
	startLon float64,
	radius int,
	isFood bool,
) float64 {
	preferenceScore := 0.3
	for category, weight := range preferenceWeights {
		for _, actual := range place.Categories {
			if actual == category || hasCategoryPrefix(actual, category+".") {
				if weight > preferenceScore {
					preferenceScore = weight
				}
			}
		}
	}

	dist := distanceMeters(startLat, startLon, place.Coordinates[1], place.Coordinates[0])
	proximityBonus := 0.0
	if radius > 0 {
		proximityBonus = 1.0 - (dist / float64(radius))
		if proximityBonus < 0 {
			proximityBonus = 0
		}
	}

	foodPenalty := 0.0
	if isFood {
		foodPenalty = 0.1
	}

	return preferenceScore + 0.4*proximityBonus - foodPenalty
}

func greedySelectPlaces(
	candidates []recommendationCandidate,
	matrix *entity.RouteMatrix,
	maxPlaces int,
	timeBudgetSeconds float64,
	includeFood bool,
) []dto.Place {
	selected := make([]recommendationCandidate, 0, maxPlaces)
	used := make(map[string]struct{})
	categoryCounts := make(map[string]int)
	foodUsed := false
	currentSpent := 0.0

	// Если пользователь попросил food-point, стараемся заранее включить одну подходящую
	if includeFood && maxPlaces > 0 {
		if foodCandidate, addedTime, ok := chooseRequiredFoodCandidate(candidates, matrix, timeBudgetSeconds); ok {
			selected = append(selected, foodCandidate)
			currentSpent += addedTime
			foodUsed = true
			categoryCounts[foodCandidate.PrimaryClass]++

			if foodCandidate.Place.PlaceID != "" {
				used[foodCandidate.Place.PlaceID] = struct{}{}
			}
		}
	}

	for len(selected) < maxPlaces {
		bestIdx := -1
		bestGain := 0.0
		bestAddedTime := 0.0

		for i, candidate := range candidates {
			if candidate.Place.PlaceID != "" {
				if _, exists := used[candidate.Place.PlaceID]; exists {
					continue
				}
			}

			if candidate.IsFood {
				if !includeFood || foodUsed {
					continue
				}
			} else {
				if categoryCounts[candidate.PrimaryClass] >= 2 {
					continue
				}

				// Пока есть доступный новый класс, не берём второй объект уже представленного класса
				if categoryCounts[candidate.PrimaryClass] >= 1 &&
					hasUnrepresentedAlternative(
						candidates,
						selected,
						used,
						categoryCounts,
						candidate.PrimaryClass,
						matrix,
						currentSpent,
						timeBudgetSeconds,
					) {
					continue
				}
			}

			if isTooSimilarToSelected(candidate, selected) {
				continue
			}

			addedTime := estimateAddedTime(candidate, selected, matrix)
			if addedTime <= 0 {
				continue
			}

			if currentSpent+addedTime > timeBudgetSeconds {
				continue
			}

			dynamicScore := candidate.BaseScore

			if categoryCounts[candidate.PrimaryClass] == 0 {
				dynamicScore += 0.30
			} else {
				dynamicScore -= 0.30 * float64(categoryCounts[candidate.PrimaryClass])
			}

			if len(selected) > 0 && selected[len(selected)-1].PrimaryClass == candidate.PrimaryClass {
				dynamicScore -= 0.20
			}

			gain := dynamicScore / addedTime
			if gain > bestGain {
				bestGain = gain
				bestIdx = i
				bestAddedTime = addedTime
			}
		}

		if bestIdx == -1 {
			break
		}

		chosen := candidates[bestIdx]
		selected = append(selected, chosen)
		currentSpent += bestAddedTime

		if chosen.Place.PlaceID != "" {
			used[chosen.Place.PlaceID] = struct{}{}
		}
		categoryCounts[chosen.PrimaryClass]++
		if chosen.IsFood {
			foodUsed = true
		}
	}

	result := make([]dto.Place, 0, len(selected))
	for _, item := range selected {
		result = append(result, item.Place)
	}

	return result
}

func localImproveSelection(
	currentPlaces []dto.Place,
	allCandidates []recommendationCandidate,
	matrix *entity.RouteMatrix,
	timeBudgetSeconds float64,
	includeFood bool,
) []dto.Place {
	currentCandidates, ok := mapPlacesToCandidates(currentPlaces, allCandidates)
	if !ok || len(currentCandidates) == 0 {
		return currentPlaces
	}

	bestSelection := cloneCandidates(currentCandidates)
	bestObjective := selectionObjective(bestSelection, includeFood)

	foodAvailable := false
	for _, candidate := range allCandidates {
		if candidate.IsFood {
			foodAvailable = true
			break
		}
	}

	for selectedIdx := range currentCandidates {
		currentKeySet := buildCandidateKeySet(currentCandidates)
		delete(currentKeySet, candidateKey(currentCandidates[selectedIdx]))

		for _, candidate := range allCandidates {
			key := candidateKey(candidate)
			if _, exists := currentKeySet[key]; exists {
				continue
			}

			tentative := cloneCandidates(currentCandidates)
			tentative[selectedIdx] = candidate

			if hasSelectionNearDuplicates(tentative) {
				continue
			}

			if !selectionSatisfiesConstraints(tentative) {
				continue
			}

			approxDuration := approximateSelectionDuration(tentative, matrix)
			if approxDuration > timeBudgetSeconds {
				continue
			}

			objective := selectionObjective(tentative, includeFood)
			if includeFood && foodAvailable && !selectionHasFood(bestSelection) && selectionHasFood(tentative) {
				objective += 0.20
			}

			if objective > bestObjective+0.05 {
				bestSelection = tentative
				bestObjective = objective
			}
		}
	}

	return candidatesToPlaces(bestSelection)
}

func mapPlacesToCandidates(
	places []dto.Place,
	candidates []recommendationCandidate,
) ([]recommendationCandidate, bool) {
	index := make(map[string]recommendationCandidate, len(candidates))
	for _, candidate := range candidates {
		index[candidateKey(candidate)] = candidate
	}

	result := make([]recommendationCandidate, 0, len(places))
	for _, place := range places {
		candidate, exists := index[candidateKeyFromPlace(place)]
		if !exists {
			return nil, false
		}
		result = append(result, candidate)
	}

	return result, true
}

func candidatesToPlaces(candidates []recommendationCandidate) []dto.Place {
	result := make([]dto.Place, 0, len(candidates))
	for _, candidate := range candidates {
		result = append(result, candidate.Place)
	}
	return result
}

func cloneCandidates(src []recommendationCandidate) []recommendationCandidate {
	dst := make([]recommendationCandidate, len(src))
	copy(dst, src)
	return dst
}

func buildCandidateKeySet(candidates []recommendationCandidate) map[string]struct{} {
	result := make(map[string]struct{}, len(candidates))
	for _, candidate := range candidates {
		result[candidateKey(candidate)] = struct{}{}
	}
	return result
}

func candidateKey(candidate recommendationCandidate) string {
	return candidateKeyFromPlace(candidate.Place)
}

func candidateKeyFromPlace(place dto.Place) string {
	if place.PlaceID != "" {
		return place.PlaceID
	}
	return strings.TrimSpace(place.Name) + "|" + strings.TrimSpace(place.Address)
}

func selectionSatisfiesConstraints(selection []recommendationCandidate) bool {
	categoryCounts := make(map[string]int)
	foodCount := 0

	for _, candidate := range selection {
		if candidate.IsFood {
			foodCount++
			if foodCount > 1 {
				return false
			}
			continue
		}

		categoryCounts[candidate.PrimaryClass]++
		if categoryCounts[candidate.PrimaryClass] > 2 {
			return false
		}
	}

	return true
}

func selectionHasFood(selection []recommendationCandidate) bool {
	for _, candidate := range selection {
		if candidate.IsFood {
			return true
		}
	}
	return false
}

func hasSelectionNearDuplicates(selection []recommendationCandidate) bool {
	for i := 0; i < len(selection); i++ {
		for j := i + 1; j < len(selection); j++ {
			if selection[i].PrimaryClass != selection[j].PrimaryClass {
				continue
			}
			if isTooSimilarToSelected(selection[i], []recommendationCandidate{selection[j]}) {
				return true
			}
		}
	}
	return false
}

func approximateSelectionDuration(
	selection []recommendationCandidate,
	matrix *entity.RouteMatrix,
) float64 {
	total := 0.0
	built := make([]recommendationCandidate, 0, len(selection))

	for _, candidate := range selection {
		added := estimateAddedTime(candidate, built, matrix)
		if added <= 0 {
			return math.MaxFloat64
		}
		total += added
		built = append(built, candidate)
	}

	return total
}

func selectionObjective(
	selection []recommendationCandidate,
	includeFood bool,
) float64 {
	total := 0.0
	categoryCounts := make(map[string]int)
	uniqueClasses := make(map[string]struct{})

	for _, candidate := range selection {
		total += candidate.BaseScore
		categoryCounts[candidate.PrimaryClass]++
		uniqueClasses[candidate.PrimaryClass] = struct{}{}
	}

	for className, count := range categoryCounts {
		if className == "food" {
			continue
		}
		if count > 1 {
			total -= 0.20 * float64(count-1)
		}
	}

	total += 0.10 * float64(len(uniqueClasses))

	if includeFood && selectionHasFood(selection) {
		total += 0.10
	}

	return total
}

func estimateAddedTime(
	candidate recommendationCandidate,
	selected []recommendationCandidate,
	matrix *entity.RouteMatrix,
) float64 {
	if matrix == nil || len(matrix.Durations) == 0 {
		return float64(visitDurationPerPlaceSeconds)
	}

	bestTravel := safeMatrixDuration(matrix, 0, candidate.MatrixIndex)

	for _, selectedCandidate := range selected {
		d := safeMatrixDuration(matrix, selectedCandidate.MatrixIndex, candidate.MatrixIndex)
		if d > 0 && (bestTravel == 0 || d < bestTravel) {
			bestTravel = d
		}
	}

	if bestTravel <= 0 {
		return 0
	}

	return bestTravel + float64(visitDurationPerPlaceSeconds)
}

func safeMatrixDuration(matrix *entity.RouteMatrix, from, to int) float64 {
	if matrix == nil || from < 0 || to < 0 {
		return 0
	}
	if from >= len(matrix.Durations) {
		return 0
	}
	if to >= len(matrix.Durations[from]) {
		return 0
	}
	return matrix.Durations[from][to]
}

func chooseRequiredFoodCandidate(
	candidates []recommendationCandidate,
	matrix *entity.RouteMatrix,
	timeBudgetSeconds float64,
) (recommendationCandidate, float64, bool) {
	bestIdx := -1
	bestGain := 0.0
	bestAddedTime := 0.0

	for i, candidate := range candidates {
		if !candidate.IsFood {
			continue
		}

		addedTime := estimateAddedTime(candidate, nil, matrix)
		if addedTime <= 0 || addedTime > timeBudgetSeconds {
			continue
		}

		dynamicScore := candidate.BaseScore + 0.20
		gain := dynamicScore / addedTime

		if gain > bestGain {
			bestGain = gain
			bestIdx = i
			bestAddedTime = addedTime
		}
	}

	if bestIdx == -1 {
		return recommendationCandidate{}, 0, false
	}

	return candidates[bestIdx], bestAddedTime, true
}

func hasUnrepresentedAlternative(
	candidates []recommendationCandidate,
	selected []recommendationCandidate,
	used map[string]struct{},
	categoryCounts map[string]int,
	currentPrimaryClass string,
	matrix *entity.RouteMatrix,
	currentSpent float64,
	timeBudgetSeconds float64,
) bool {
	for _, candidate := range candidates {
		if candidate.IsFood {
			continue
		}
		if candidate.PrimaryClass == "" || candidate.PrimaryClass == currentPrimaryClass {
			continue
		}
		if categoryCounts[candidate.PrimaryClass] > 0 {
			continue
		}
		if candidate.Place.PlaceID != "" {
			if _, exists := used[candidate.Place.PlaceID]; exists {
				continue
			}
		}
		if isTooSimilarToSelected(candidate, selected) {
			continue
		}

		addedTime := estimateAddedTime(candidate, selected, matrix)
		if addedTime <= 0 {
			continue
		}
		if currentSpent+addedTime > timeBudgetSeconds {
			continue
		}

		return true
	}

	return false
}

func isLowQualityPlace(place dto.Place) bool {
	name := strings.TrimSpace(place.Name)
	address := strings.TrimSpace(place.Address)

	if name == "" {
		return true
	}

	if address != "" && sameNormalizedText(name, address) {
		return true
	}

	return false
}

func sameNormalizedText(a, b string) bool {
	return strings.EqualFold(strings.TrimSpace(a), strings.TrimSpace(b))
}

func isTooSimilarToSelected(
	candidate recommendationCandidate,
	selected []recommendationCandidate,
) bool {
	for _, existing := range selected {
		if existing.PrimaryClass != candidate.PrimaryClass {
			continue
		}

		if arePlacesNear(candidate.Place, existing.Place, maxNearDuplicateDistanceM) {
			return true
		}

		if sameAddress(candidate.Place, existing.Place) {
			return true
		}
	}

	return false
}

func arePlacesNear(a dto.Place, b dto.Place, thresholdMeters float64) bool {
	if len(a.Coordinates) < 2 || len(b.Coordinates) < 2 {
		return false
	}

	dist := distanceMeters(
		a.Coordinates[1], a.Coordinates[0],
		b.Coordinates[1], b.Coordinates[0],
	)

	return dist <= thresholdMeters
}

func sameAddress(a dto.Place, b dto.Place) bool {
	if a.Address == "" || b.Address == "" {
		return false
	}

	return sameNormalizedText(a.Address, b.Address)
}

func (uc *GenerateRouteUC) buildRouteWithinBudget(
	ctx context.Context,
	startLat float64,
	startLon float64,
	selectedPlaces []dto.Place,
	timeBudgetSeconds float64,
) ([]dto.Place, dto.CalculateRouteResponse, float64, error) {
	working := append([]dto.Place(nil), selectedPlaces...)

	for len(working) > 0 {
		coordinates := make([][]float64, 0, len(working)+1)
		coordinates = append(coordinates, []float64{startLon, startLat})

		for _, place := range working {
			if len(place.Coordinates) < 2 {
				continue
			}
			coordinates = append(coordinates, place.Coordinates)
		}

		if len(coordinates) < 2 {
			return dto.GenerateRouteResponse{}.Places, dto.CalculateRouteResponse{}, 0, uc_errors.ErrInvalidRoutePoints
		}

		routeResp, err := uc.calculateUC.Execute(ctx, dto.CalculateRouteRequest{
			Coordinates:   coordinates,
			Profile:       "foot-walking",
			Preference:    "",
			OptimizeOrder: true,
		})
		if err != nil {
			return nil, dto.CalculateRouteResponse{}, 0, err
		}

		estimatedDuration := routeResp.Duration + float64(len(working)*visitDurationPerPlaceSeconds)
		if estimatedDuration <= timeBudgetSeconds {
			return working, routeResp, estimatedDuration, nil
		}

		working = working[:len(working)-1]
	}

	return nil, dto.CalculateRouteResponse{}, 0, uc_errors.ErrSearchPlacesFailed
}

func max(a, b int) int {
	if a > b {
		return a
	}
	return b
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
