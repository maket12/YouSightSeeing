package dto

type CalculateRouteResponse struct {
	Points   [][]float64 `json:"points"`
	Distance float64     `json:"distance"`
	Duration float64     `json:"duration"`
}
