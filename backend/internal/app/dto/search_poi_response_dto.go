package dto

type PlaceInfo struct {
	//имя точки для отображения в инфе о точке
	Name string `json:"name"`

	//адрес одной строкой
	Address string `json:"address,omitempty"`

	//категории, к которым относится эта конкретная точка
	Categories []string `json:"categories"`

	//координаты точки [lon, lat]
	Coordinates []float64 `json:"coordinates"`

	//уникальный ID места, понадобится в будущем для более детальной инфа о точках
	PlaceID string `json:"place_id"`
}

type SearchPlacesResponse struct {
	Places []PlaceInfo `json:"places"`
}
