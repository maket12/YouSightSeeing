package dto

type RouteResponse struct {
	Route    []Point `json:"route"`
	Distance float64 `json:"distance"`
	Duration float64 `json:"duration"`
}
