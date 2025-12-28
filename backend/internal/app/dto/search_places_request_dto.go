package dto

type SearchPlacesRequest struct {
	// Координаты центра поиска (пользователя или стартовая точка)
	Lat float64 `json:"lat"`
	Lon float64 `json:"lon"`

	// Радиус поиска
	Radius int `json:"radius"`

	// Категории точек интереса
	Categories []string `json:"categories"`

	// Лимит количества точек
	Limit int `json:"limit,omitempty"`
}
