package usecase

import (
	"math"
	"testing"

	"YouSightSeeing/backend/internal/app/dto"
	"YouSightSeeing/backend/internal/domain/entity"
)

func almostEqual(a, b float64) bool {
	return math.Abs(a-b) < 0.000001
}

func strPtr(value string) *string {
	return &value
}

func TestIncreasePreferenceWeight(t *testing.T) {
	tests := []struct {
		name    string
		current float64
		delta   float64
		want    float64
	}{
		{
			name:    "default weight with place viewed delta",
			current: 0.5,
			delta:   0.03,
			want:    0.515,
		},
		{
			name:    "saved route grows stronger",
			current: 0.5,
			delta:   0.12,
			want:    0.56,
		},
		{
			name:    "weight never goes above one",
			current: 0.98,
			delta:   0.5,
			want:    0.99,
		},
		{
			name:    "negative current weight is clamped",
			current: -1,
			delta:   0.1,
			want:    0.1,
		},
		{
			name:    "too large current weight is clamped",
			current: 2,
			delta:   0.1,
			want:    1,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got := increasePreferenceWeight(tt.current, tt.delta)

			if !almostEqual(got, tt.want) {
				t.Fatalf("expected %v, got %v", tt.want, got)
			}
		})
	}
}

func TestPreferenceDeltaByEventType(t *testing.T) {
	tests := []struct {
		name      string
		eventType string
		want      float64
	}{
		{
			name:      "place viewed gives small signal",
			eventType: entity.UserEventPlaceViewed,
			want:      0.03,
		},
		{
			name:      "route opened gives small signal",
			eventType: entity.UserEventRouteOpened,
			want:      0.04,
		},
		{
			name:      "route saved gives strongest signal",
			eventType: entity.UserEventRouteSaved,
			want:      0.12,
		},
		{
			name:      "route completed gives medium signal",
			eventType: entity.UserEventRouteCompleted,
			want:      0.08,
		},
		{
			name:      "route generated does not change category weight",
			eventType: entity.UserEventRouteGenerated,
			want:      0,
		},
		{
			name:      "unknown event does not change category weight",
			eventType: "unknown_event",
			want:      0,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got := preferenceDeltaByEventType(tt.eventType)

			if !almostEqual(got, tt.want) {
				t.Fatalf("expected %v, got %v", tt.want, got)
			}
		})
	}
}

func TestBuildPlaceScoreAdjustments(t *testing.T) {
	placeA := "place-a"
	placeB := "place-b"
	emptyPlace := ""

	events := []entity.UserEvent{
		{
			EventType: entity.UserEventRouteGenerated,
			PlaceID:   &placeA,
		},
		{
			EventType: entity.UserEventRouteGenerated,
			PlaceID:   &placeA,
		},
		{
			EventType: entity.UserEventPlaceViewed,
			PlaceID:   &placeA,
		},
		{
			EventType: entity.UserEventRouteSaved,
			PlaceID:   &placeB,
		},
		{
			EventType: entity.UserEventRouteCompleted,
			PlaceID:   &placeB,
		},
		{
			EventType: entity.UserEventRouteGenerated,
			PlaceID:   &emptyPlace,
		},
		{
			EventType: entity.UserEventRouteGenerated,
			PlaceID:   nil,
		},
	}

	adjustments := buildPlaceScoreAdjustments(events)

	wantA := 0.03 - 2*0.08
	wantB := 0.08 - 0.05

	if !almostEqual(adjustments[placeA], wantA) {
		t.Fatalf("place-a expected %v, got %v", wantA, adjustments[placeA])
	}

	if !almostEqual(adjustments[placeB], wantB) {
		t.Fatalf("place-b expected %v, got %v", wantB, adjustments[placeB])
	}

	if _, exists := adjustments[emptyPlace]; exists {
		t.Fatalf("empty place id should be ignored")
	}
}

func TestBuildPlaceScoreAdjustmentsCapsPenaltyAndBonus(t *testing.T) {
	placePenalty := "place-penalty"
	placeBonus := "place-bonus"

	events := make([]entity.UserEvent, 0)

	for i := 0; i < 10; i++ {
		events = append(events, entity.UserEvent{
			EventType: entity.UserEventRouteGenerated,
			PlaceID:   &placePenalty,
		})
	}

	for i := 0; i < 10; i++ {
		events = append(events, entity.UserEvent{
			EventType: entity.UserEventRouteSaved,
			PlaceID:   &placeBonus,
		})
	}

	adjustments := buildPlaceScoreAdjustments(events)

	if !almostEqual(adjustments[placePenalty], -maxPlacePenalty) {
		t.Fatalf("penalty expected %v, got %v", -maxPlacePenalty, adjustments[placePenalty])
	}

	if !almostEqual(adjustments[placeBonus], maxPlaceBonus) {
		t.Fatalf("bonus expected %v, got %v", maxPlaceBonus, adjustments[placeBonus])
	}
}

func TestDeriveRequestedCategoriesFromWeights(t *testing.T) {
	weights := map[string]float64{
		"tourism.sights":       0.90,
		"leisure.park":         0.80,
		"entertainment.museum": 0.70,
		"catering.cafe":        1.00,
		"low.category":         0.40,
	}

	got := deriveRequestedCategoriesFromWeights(weights)

	want := []string{
		"tourism.sights",
		"leisure.park",
		"entertainment.museum",
	}

	if len(got) != len(want) {
		t.Fatalf("expected %d categories, got %d: %v", len(want), len(got), got)
	}

	for i := range want {
		if got[i] != want[i] {
			t.Fatalf("expected category %q at index %d, got %q", want[i], i, got[i])
		}
	}
}

func TestDeriveRequestedCategoriesFromWeightsIgnoresLowAndFood(t *testing.T) {
	weights := map[string]float64{
		"catering.cafe": 1.00,
		"leisure.park":  0.54,
	}

	got := deriveRequestedCategoriesFromWeights(weights)

	if len(got) != 0 {
		t.Fatalf("expected empty result, got %v", got)
	}
}

func TestNormalizeCategories(t *testing.T) {
	tests := []struct {
		name        string
		categories  []string
		includeFood bool
		want        []string
	}{
		{
			name:        "default categories without food",
			categories:  nil,
			includeFood: false,
			want:        []string{"tourism.sights", "leisure.park"},
		},
		{
			name:        "default categories with food",
			categories:  nil,
			includeFood: true,
			want:        []string{"tourism.sights", "leisure.park", "catering.cafe"},
		},
		{
			name:        "removes duplicates and empty values",
			categories:  []string{"tourism.sights", "", "tourism.sights", "leisure.park"},
			includeFood: false,
			want:        []string{"tourism.sights", "leisure.park"},
		},
		{
			name:        "does not duplicate food category",
			categories:  []string{"tourism.sights", "catering.cafe"},
			includeFood: true,
			want:        []string{"tourism.sights", "catering.cafe"},
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got := normalizeCategories(tt.categories, tt.includeFood)

			if len(got) != len(tt.want) {
				t.Fatalf("expected %v, got %v", tt.want, got)
			}

			for i := range tt.want {
				if got[i] != tt.want[i] {
					t.Fatalf("expected %v, got %v", tt.want, got)
				}
			}
		})
	}
}

func TestBuildRecommendationCandidatesAppliesPlacePenaltyAndBonus(t *testing.T) {
	places := []dto.Place{
		{
			Name:        "Good Park",
			Address:     "Park Address",
			Categories:  []string{"leisure.park"},
			Coordinates: []float64{8.6815, 49.4146},
			PlaceID:     "park-good",
		},
		{
			Name:        "Repeated Park",
			Address:     "Repeated Address",
			Categories:  []string{"leisure.park"},
			Coordinates: []float64{8.6816, 49.4147},
			PlaceID:     "park-repeated",
		},
		{
			Name:        "Saved Park",
			Address:     "Saved Address",
			Categories:  []string{"leisure.park"},
			Coordinates: []float64{8.6817, 49.4148},
			PlaceID:     "park-saved",
		},
	}

	adjustments := map[string]float64{
		"park-repeated": -0.30,
		"park-saved":    0.20,
	}

	candidates := buildRecommendationCandidates(
		places,
		[]string{"leisure.park"},
		map[string]float64{"leisure.park": 0.8},
		adjustments,
		map[string]float64{},
		0.0,
		49.4146,
		8.6815,
		4000,
		false,
	)

	if len(candidates) != 3 {
		t.Fatalf("expected 3 candidates, got %d", len(candidates))
	}

	var repeatedScore float64
	var savedScore float64
	var goodScore float64

	for _, candidate := range candidates {
		switch candidate.Place.PlaceID {
		case "park-repeated":
			repeatedScore = candidate.BaseScore
		case "park-saved":
			savedScore = candidate.BaseScore
		case "park-good":
			goodScore = candidate.BaseScore
		}
	}

	if !(savedScore > goodScore) {
		t.Fatalf("saved place should have bonus: saved=%v good=%v", savedScore, goodScore)
	}

	if !(repeatedScore < goodScore) {
		t.Fatalf("repeated place should have penalty: repeated=%v good=%v", repeatedScore, goodScore)
	}
}

func TestBuildRecommendationCandidatesFoodFiltering(t *testing.T) {
	places := []dto.Place{
		{
			Name:        "Cafe",
			Address:     "Cafe Address",
			Categories:  []string{"catering.cafe"},
			Coordinates: []float64{8.6815, 49.4146},
			PlaceID:     "cafe-1",
		},
		{
			Name:        "Sight",
			Address:     "Sight Address",
			Categories:  []string{"tourism.sights"},
			Coordinates: []float64{8.6816, 49.4147},
			PlaceID:     "sight-1",
		},
	}

	candidatesWithoutFood := buildRecommendationCandidates(
		places,
		[]string{"tourism.sights"},
		map[string]float64{"tourism.sights": 0.9},
		map[string]float64{},
		map[string]float64{},
		0.0,
		49.4146,
		8.6815,
		4000,
		false,
	)

	for _, candidate := range candidatesWithoutFood {
		if candidate.IsFood {
			t.Fatalf("food candidate should not be included when includeFood=false")
		}
	}

	candidatesWithFood := buildRecommendationCandidates(
		places,
		[]string{"tourism.sights"},
		map[string]float64{
			"tourism.sights": 0.9,
			"catering.cafe":  0.45,
		},
		map[string]float64{},
		map[string]float64{},
		0.0,
		49.4146,
		8.6815,
		4000,
		true,
	)

	hasFood := false
	for _, candidate := range candidatesWithFood {
		if candidate.IsFood {
			hasFood = true
		}
	}

	if !hasFood {
		t.Fatalf("food candidate should be included when includeFood=true")
	}
}

func TestGreedySelectPlacesUsesOnlyOneFoodPoint(t *testing.T) {
	candidates := []recommendationCandidate{
		{
			Place: dto.Place{
				Name:        "Cafe 1",
				Address:     "Cafe 1 Address",
				Categories:  []string{"catering.cafe"},
				Coordinates: []float64{8.6815, 49.4146},
				PlaceID:     "cafe-1",
			},
			BaseScore:    1.0,
			PrimaryClass: "food",
			IsFood:       true,
			MatrixIndex:  1,
		},
		{
			Place: dto.Place{
				Name:        "Cafe 2",
				Address:     "Cafe 2 Address",
				Categories:  []string{"catering.cafe"},
				Coordinates: []float64{8.6816, 49.4147},
				PlaceID:     "cafe-2",
			},
			BaseScore:    1.0,
			PrimaryClass: "food",
			IsFood:       true,
			MatrixIndex:  2,
		},
		{
			Place: dto.Place{
				Name:        "Sight 1",
				Address:     "Sight 1 Address",
				Categories:  []string{"tourism.sights"},
				Coordinates: []float64{8.6817, 49.4148},
				PlaceID:     "sight-1",
			},
			BaseScore:    1.0,
			PrimaryClass: "tourism.sights",
			IsFood:       false,
			MatrixIndex:  3,
		},
	}

	matrix := &entity.RouteMatrix{
		Durations: [][]float64{
			{0, 100, 100, 100},
			{100, 0, 100, 100},
			{100, 100, 0, 100},
			{100, 100, 100, 0},
		},
	}

	selected := greedySelectPlaces(
		candidates,
		matrix,
		3,
		10000,
		true,
	)

	foodCount := 0
	for _, place := range selected {
		if hasFoodCategory(place.Categories) {
			foodCount++
		}
	}

	if foodCount > 1 {
		t.Fatalf("expected at most one food point, got %d", foodCount)
	}
}
