package http

import (
	"net/http"

	"github.com/labstack/echo/v4"

	"YouSightSeeing/backend/internal/app/dto"
	"YouSightSeeing/backend/internal/app/uc_errors"
	"YouSightSeeing/backend/internal/app/usecase"
)

type RouteHandler struct {
	RouteUC *usecase.RouteUC
}

func NewRouteHandler(uc *usecase.RouteUC) *RouteHandler {
	return &RouteHandler{
		RouteUC: uc,
	}
}

func (h *RouteHandler) CalculateRoute(c echo.Context) error {
	var req dto.RouteRequest

	if err := c.Bind(&req); err != nil {
		return c.JSON(http.StatusBadRequest, map[string]string{
			"error": "Invalid request format",
		})
	}

	resp, err := h.RouteUC.Execute(c.Request().Context(), req)
	if err != nil {
		status := http.StatusInternalServerError
		if err == uc_errors.ErrInvalidRoutePoints {
			status = http.StatusBadRequest
		}

		return c.JSON(status, map[string]string{
			"error": err.Error(),
		})
	}

	return c.JSON(http.StatusOK, resp)
}
