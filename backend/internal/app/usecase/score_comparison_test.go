package usecase

import (
    "fmt"
    "sort"
    "testing"

    "YouSightSeeing/backend/internal/app/dto"
)

func TestPlaceConversionEffectOnBaseScore(t *testing.T) {
    places := []dto.Place{
        {
            Name:        "Place One",
            Address:     "Addr 1",
            Categories:  []string{"tourism.sights"},
            Coordinates: []float64{8.0, 49.0},
            PlaceID:     "p1",
        },
        {
            Name:        "Place Two",
            Address:     "Addr 2",
            Categories:  []string{"tourism.sights"},
            Coordinates: []float64{8.001, 49.001},
            PlaceID:     "p2",
        },
        {
            Name:        "Place NoID",
            Address:     "Addr 3",
            Categories:  []string{"tourism.sights"},
            Coordinates: []float64{8.002, 49.002},
            PlaceID:     "",
        },
    }

    requested := []string{"tourism.sights"}
    pref := map[string]float64{"tourism.sights": 0.9}
    adjustments := map[string]float64{"p1": 0.05}
    placeConv := map[string]float64{"p1": 0.6, "p2": 0.1}

    oldCandidates := buildRecommendationCandidates_old(places, requested, pref, adjustments, 49.0, 8.0, 4000, false)
    newCandidates := buildRecommendationCandidates(places, requested, pref, adjustments, placeConv, placeConversionWeight, 49.0, 8.0, 4000, false)

    oldMap := make(map[string]float64)
    newMap := make(map[string]float64)

    for _, c := range oldCandidates {
        key := c.Place.PlaceID
        if key == "" {
            key = c.Place.Name
        }
        oldMap[key] = c.BaseScore
    }
    for _, c := range newCandidates {
        key := c.Place.PlaceID
        if key == "" {
            key = c.Place.Name
        }
        newMap[key] = c.BaseScore
    }

    eps := 0.000001

    // Print table header
    keys := make([]string, 0, len(places))
    for _, p := range places {
        key := p.PlaceID
        if key == "" {
            key = p.Name
        }
        keys = append(keys, key)
    }

    // Ensure deterministic order
    sort.Strings(keys)

    fmt.Println("\nPlace | OldScore | NewScore | Delta")
    fmt.Println("------------------------------------")
    for _, k := range keys {
        old := oldMap[k]
        new := newMap[k]
        delta := new - old
        fmt.Printf("%s | %.6f | %.6f | %.6f\n", k, old, new, delta)
    }

    p1Old, ok1 := oldMap["p1"]
    p1New, ok2 := newMap["p1"]
    if !ok1 || !ok2 {
        t.Fatalf("p1 candidate missing in results: old=%v new=%v", ok1, ok2)
    }

    expectedDelta := placeConversionWeight * placeConv["p1"]
    if !(abs(p1New-p1Old-expectedDelta) < eps) {
        t.Fatalf("p1: expected delta ~%v, got %v (old=%v new=%v)", expectedDelta, p1New-p1Old, p1Old, p1New)
    }

    p2Old, ok1 := oldMap["p2"]
    p2New, ok2 := newMap["p2"]
    if !ok1 || !ok2 {
        t.Fatalf("p2 candidate missing in results: old=%v new=%v", ok1, ok2)
    }
    expectedDelta2 := placeConversionWeight * placeConv["p2"]
    if !(abs(p2New-p2Old-expectedDelta2) < eps) {
        t.Fatalf("p2: expected delta ~%v, got %v (old=%v new=%v)", expectedDelta2, p2New-p2Old, p2Old, p2New)
    }

    nidOld, ok1 := oldMap["Place NoID"]
    nidNew, ok2 := newMap["Place NoID"]
    if !ok1 || !ok2 {
        t.Fatalf("Place NoID missing in results: old=%v new=%v", ok1, ok2)
    }
    if !(abs(nidNew-nidOld) < eps) {
        t.Fatalf("Place NoID should not be affected by conversion: delta %v", nidNew-nidOld)
    }
}

func abs(a float64) float64 {
    if a < 0 {
        return -a
    }
    return a
}
