package usecase

import (
    "bytes"
    "html/template"
    "YouSightSeeing/backend/internal/app/dto"
)

// GenerateComparisonReport builds an HTML report comparing old and new base scores.
func GenerateComparisonReport() (string, error) {
    places := []dto.Place{
        {Name: "Place One", Address: "Addr 1", Categories: []string{"tourism.sights"}, Coordinates: []float64{8.0, 49.0}, PlaceID: "p1"},
        {Name: "Place Two", Address: "Addr 2", Categories: []string{"tourism.sights"}, Coordinates: []float64{8.001, 49.001}, PlaceID: "p2"},
        {Name: "Place NoID", Address: "Addr 3", Categories: []string{"tourism.sights"}, Coordinates: []float64{8.002, 49.002}, PlaceID: ""},
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

    // Prepare rows
    type row struct{
        Key string
        Old float64
        New float64
        Delta float64
    }

    rows := make([]row, 0, len(places))
    for _, p := range places {
        k := p.PlaceID
        if k == "" { k = p.Name }
        rows = append(rows, row{Key: k, Old: oldMap[k], New: newMap[k], Delta: newMap[k]-oldMap[k]})
    }

    tmpl := `<!doctype html>
<html>
<head>
  <meta charset="utf-8" />
  <title>Recommendation Score Comparison</title>
  <style>
    body { font-family: Arial, sans-serif; padding: 24px; background:#f7f9fb }
    table { border-collapse: collapse; width: 720px; background: white; box-shadow: 0 2px 6px rgba(0,0,0,0.08)}
    th, td { padding: 10px 14px; border-bottom: 1px solid #e6eef6 }
    th { background: #0b69ff; color: white; text-align: left }
    tr:nth-child(even) td { background: #fbfdff }
    .delta { font-weight: 600 }
    .positive { color: #10712a }
    .neutral { color: #333 }
  </style>
</head>
<body>
  <h2>Recommendation Score Comparison — Было / Стало</h2>
  <table>
    <thead>
      <tr><th>Place</th><th>Old Score</th><th>New Score</th><th>Delta</th></tr>
    </thead>
    <tbody>
    {{range .}}
      <tr>
        <td>{{.Key}}</td>
        <td>{{printf "%.6f" .Old}}</td>
        <td>{{printf "%.6f" .New}}</td>
        <td class="delta {{if gt .Delta 0.0}}positive{{else}}neutral{{end}}">{{printf "%.6f" .Delta}}</td>
      </tr>
    {{end}}
    </tbody>
  </table>
</body>
</html>`

    t, err := template.New("report").Parse(tmpl)
    if err != nil {
        return "", err
    }

    var buf bytes.Buffer
    if err := t.Execute(&buf, rows); err != nil {
        return "", err
    }

    return buf.String(), nil
}
