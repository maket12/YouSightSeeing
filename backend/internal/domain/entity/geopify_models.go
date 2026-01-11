package entity

type PlacesSearchFilter struct {
	Lat        float64
	Lon        float64
	Radius     int
	Categories []string
	Limit      int
}

type Place struct {
	ID          string
	Name        string
	Address     string
	Categories  []string
	Coordinates []float64
}
