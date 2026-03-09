package dto

type GenerateRouteResponse struct {
	Places []Place     `json:"places"`
	Route  RouteResult `json:"route"`
}

type RouteResult struct {
	Points   [][]float64 `json:"points"`
	Distance float64     `json:"distance"`
	Duration float64     `json:"duration"`
}
