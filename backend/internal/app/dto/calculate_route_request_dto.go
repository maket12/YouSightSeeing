package dto

type Point struct {
	Lat float64 `json:"lat"`
	Lon float64 `json:"lon"`
}

type CalculateRouteRequest struct {
	Coordinates   [][]float64 `json:"coordinates"`
	Profile       string      `json:"profile,omitempty"`
	Preference    string      `json:"preference,omitempty"`
	OptimizeOrder bool        `json:"optimize_order,omitempty"`
}
