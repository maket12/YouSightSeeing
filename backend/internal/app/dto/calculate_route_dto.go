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

type CalculateRouteResponse struct {
	Points   [][]float64 `json:"points"`
	Distance float64     `json:"distance"`
	Duration float64     `json:"duration"`
}
