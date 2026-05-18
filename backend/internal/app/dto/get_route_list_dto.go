package dto

type GetRouteListRequest struct {
	Limit  int `json:"limit"`
	Offset int `json:"offset"`
}

type GetRouteListResponse struct {
	Routes []RouteResponse `json:"routes"`
}
