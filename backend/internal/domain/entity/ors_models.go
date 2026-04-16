package entity

type ORSRequest struct {
	Coordinates  [][]float64 `json:"coordinates"`
	Preference   string      `json:"preference,omitempty"`
	Instructions bool        `json:"instructions"`
	Geometry     bool        `json:"geometry"`
}

type ORSRoute struct {
	Geometry [][]float64
	Distance float64
	Duration float64
}

type ORSMatrixRequest struct {
	Locations    [][]float64 `json:"locations"`
	Metrics      []string    `json:"metrics,omitempty"`
	Sources      []string    `json:"sources,omitempty"`
	Destinations []string    `json:"destinations,omitempty"`
}

type RouteMatrix struct {
	Durations [][]float64
	Distances [][]float64
}
