package dto

import "github.com/google/uuid"

type GetRouteListRequest struct {
	UserID uuid.UUID `json:"-"`
	Limit  int       `json:"limit"`
	Offset int       `json:"offset"`
}

type GetRouteListResponse struct {
	Routes []RouteResponse `json:"routes"`
}
