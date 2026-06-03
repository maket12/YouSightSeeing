package usecase

import (
    "sort"

    "YouSightSeeing/backend/internal/app/dto"
)

// buildRecommendationCandidates_old — сохранённая старая версия без placeConversion.
func buildRecommendationCandidates_old(
    places []dto.Place,
    requestedCategories []string,
    preferenceWeights map[string]float64,
    placeScoreAdjustments map[string]float64,
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

        if place.PlaceID != "" {
            baseScore += placeScoreAdjustments[place.PlaceID]
        }

        if baseScore < minCandidateScore {
            baseScore = minCandidateScore
        }

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
