package dto

type Point struct {
	Lat float64 `json:"lat"`
	Lon float64 `json:"lon"`
}

type RouteRequest struct {
	Points        []Point `json:"points"`
	Profile       string  `json:"profile,omitempty"`
	Preference    string  `json:"preference,omitempty"`
	OptimizeOrder bool    `json:"optimize_order,omitempty"`
}
