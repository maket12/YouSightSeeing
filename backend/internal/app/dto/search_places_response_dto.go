package dto

type Place struct {
	// Имя точки для отображения в информации о точке
	Name string `json:"name"`

	// Адрес одной строкой
	Address string `json:"address,omitempty"`

	// Категории, к которым относится эта конкретная точка
	Categories []string `json:"categories"`

	// Координаты точки [lon, lat]
	Coordinates []float64 `json:"coordinates"`

	// Уникальный ID места, понадобится в будущем для более детальной инфа о точках
	PlaceID string `json:"place_id"`
}

type SearchPlacesResponse struct {
	Places []Place `json:"places"`
}
