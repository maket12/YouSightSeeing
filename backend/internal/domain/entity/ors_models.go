package entity

type ORSRequest struct {
	Coordinates  [][]float64 `json:"coordinates"`
	Preference   string      `json:"preference,omitempty"`
	Instructions bool        `json:"instructions"`
	Geometry     bool        `json:"geometry"`
}
