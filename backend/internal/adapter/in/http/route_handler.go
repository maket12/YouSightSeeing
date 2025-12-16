package http

import (
	"log/slog"
	"net/http"

	"github.com/labstack/echo/v4"

	"YouSightSeeing/backend/internal/app/dto"
	"YouSightSeeing/backend/internal/app/usecase"
)

type RouteHandler struct {
	log     *slog.Logger
	RouteUC *usecase.CalculateRouteUC
}

func NewRouteHandler(log *slog.Logger, uc *usecase.CalculateRouteUC) *RouteHandler {
	return &RouteHandler{
		log:     log,
		RouteUC: uc,
	}
}

func (h *RouteHandler) CalculateRoute(c echo.Context) error {
	var req dto.CalculateRouteRequest

	if err := c.Bind(&req); err != nil {
		return c.JSON(http.StatusBadRequest, map[string]string{
			"error": "invalid json",
		})
	}

	resp, err := h.RouteUC.Execute(c.Request().Context(), req)
	if err != nil {
		status, msg, internalErr := HttpError(err)
		h.log.ErrorContext(c.Request().Context(), "failed to calculate route",
			slog.Int("status", status),
			slog.String("public_msg", msg),
			slog.Any("cause", internalErr),
		)
		return c.JSON(status, map[string]string{"error": msg})
	}

	return c.JSON(http.StatusOK, resp)
}
