package dto

type CalculateRouteResponse struct {
	Route    [][]float64 `json:"route"`
	Distance float64     `json:"distance"`
	Duration float64     `json:"duration"`
}
