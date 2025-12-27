package dto

type SearchPoiRequest struct {
	//координаты центра поиска (пользователя или стартовая точка)
	Lat float64 `json:"lat"`
	Lon float64 `json:"lon"`

	//радиус поиска
	Radius int `json:"radius"`

	//категории точек интереса
	Categories []string `json:"categories"`

	//лимит количества точек
	Limit int `json:"limit,omitempty"`
}
