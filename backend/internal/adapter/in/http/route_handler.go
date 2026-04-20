package http

import (
	"log/slog"
	"net/http"

	"github.com/labstack/echo/v4"

	"YouSightSeeing/backend/internal/app/dto"
	"YouSightSeeing/backend/internal/app/usecase"
)

type RouteHandler struct {
	log              *slog.Logger
	calculateRouteUC usecase.CalculateRouteUseCase
	generateRouteUC  usecase.GenerateRouteUseCase
	saveRouteUC      usecase.SaveRouteUseCase
}

func NewRouteHandler(
	log *slog.Logger,
	routeUC usecase.CalculateRouteUseCase,
	generateRouteUC usecase.GenerateRouteUseCase,
	saveRouteUC usecase.SaveRouteUseCase,
) *RouteHandler {
	return &RouteHandler{
		log:              log,
		calculateRouteUC: routeUC,
		generateRouteUC:  generateRouteUC,
		saveRouteUC:      saveRouteUC,
	}
}

func (h *RouteHandler) CalculateRoute(c echo.Context) error {
	var req dto.CalculateRouteRequest

	if err := c.Bind(&req); err != nil {
		return c.JSON(http.StatusBadRequest, map[string]string{
			"error": "invalid json",
		})
	}

	resp, err := h.calculateRouteUC.Execute(c.Request().Context(), req)
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

func (h *RouteHandler) GenerateRoute(c echo.Context) error {
	var req dto.GenerateRouteRequest

	if err := c.Bind(&req); err != nil {
		return c.JSON(http.StatusBadRequest, map[string]string{
			"error": "invalid json",
		})
	}

	resp, err := h.generateRouteUC.Execute(c.Request().Context(), req)
	if err != nil {
		status, msg, internalErr := HttpError(err)
		h.log.ErrorContext(c.Request().Context(), "failed to generate route",
			slog.Int("status", status),
			slog.String("public_msg", msg),
			slog.Any("cause", internalErr),
		)
		return c.JSON(status, map[string]string{"error": msg})
	}

	return c.JSON(http.StatusOK, resp)
}

func (h *RouteHandler) SaveRoute(c echo.Context) error {
	var req dto.SaveRouteRequest

	if err := c.Bind(&req); err != nil {
		return c.JSON(http.StatusBadRequest, map[string]string{
			"error": "invalid json",
		})
	}

	err := h.saveRouteUC.Execute(c.Request().Context(), req)
	if err != nil {
		status, msg, internalErr := HttpError(err)
		h.log.ErrorContext(c.Request().Context(), "failed to save route",
			slog.Int("status", status),
			slog.String("public_msg", msg),
			slog.Any("cause", internalErr),
		)
		return c.JSON(status, map[string]string{"error": msg})
	}

	return c.JSON(http.StatusCreated, nil)
}
