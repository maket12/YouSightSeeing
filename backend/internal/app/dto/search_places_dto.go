package dto

type SearchPlacesRequest struct {
	Lat        float64  `json:"lat"`
	Lon        float64  `json:"lon"`
	Radius     int      `json:"radius"`
	Categories []string `json:"categories"`
	Limit      int      `json:"limit,omitempty"`
}

type Place struct {
	Name        string    `json:"name"`
	Address     string    `json:"address,omitempty"`
	Categories  []string  `json:"categories"`
	Coordinates []float64 `json:"coordinates"`
	PlaceID     string    `json:"place_id"`
}

type SearchPlacesResponse struct {
	Places []Place `json:"places"`
}
