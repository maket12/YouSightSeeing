package dto

type GenerateRouteRequest struct {
	StartLat    float64  `json:"start_lat"`
	StartLon    float64  `json:"start_lon"`
	Categories  []string `json:"categories,omitempty"`
	Radius      int      `json:"radius,omitempty"`
	MaxPlaces   int      `json:"max_places,omitempty"`
	IncludeFood bool     `json:"include_food,omitempty"`
}
