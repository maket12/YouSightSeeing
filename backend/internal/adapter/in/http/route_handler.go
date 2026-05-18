package http

import (
	"log/slog"
	"net/http"

	"github.com/google/uuid"
	"github.com/labstack/echo/v4"

	"YouSightSeeing/backend/internal/app/dto"
	"YouSightSeeing/backend/internal/app/usecase"
)

type RouteHandler struct {
	log              *slog.Logger
	calculateRouteUC usecase.CalculateRouteUseCase
	generateRouteUC  usecase.GenerateRouteUseCase
	createRouteUC    usecase.CreateRouteUseCase
	getRouteUC       usecase.GetRouteUseCase
	getRouteListUC   usecase.GetRouteListUseCase
}

func NewRouteHandler(
	log *slog.Logger,
	routeUC usecase.CalculateRouteUseCase,
	generateRouteUC usecase.GenerateRouteUseCase,
	saveRouteUC usecase.CreateRouteUseCase,
	getRouteUC usecase.GetRouteUseCase,
	getRouteListUC usecase.GetRouteListUseCase,
) *RouteHandler {
	return &RouteHandler{
		log:              log,
		calculateRouteUC: routeUC,
		generateRouteUC:  generateRouteUC,
		createRouteUC:    saveRouteUC,
		getRouteUC:       getRouteUC,
		getRouteListUC:   getRouteListUC,
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

	userID, ok, err := GetUserIDFromContextOrTestHeader(c)
	if err != nil {
		return c.JSON(http.StatusBadRequest, map[string]string{
			"error": "invalid test user id",
		})
	}
	if ok {
		req.UserID = userID
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

func (h *RouteHandler) CreateRoute(c echo.Context) error {
	var req dto.CreateRouteRequest

	if err := c.Bind(&req); err != nil {
		return c.JSON(http.StatusBadRequest, map[string]string{
			"error": "invalid json",
		})
	}

	userID, ok, err := GetUserIDFromContextOrTestHeader(c)
	if err != nil {
		return c.JSON(http.StatusBadRequest, map[string]string{
			"error": "invalid test user id",
		})
	}
	if !ok {
		return c.JSON(http.StatusUnauthorized, map[string]string{
			"error": "Authorization header required",
		})
	}

	req.UserID = userID

	resp, err := h.createRouteUC.Execute(c.Request().Context(), req)
	if err != nil {
		status, msg, internalErr := HttpError(err)
		h.log.ErrorContext(c.Request().Context(), "failed to save route",
			slog.Int("status", status),
			slog.String("public_msg", msg),
			slog.Any("cause", internalErr),
		)
		return c.JSON(status, map[string]string{"error": msg})
	}

	return c.JSON(http.StatusCreated, resp)
}

func (h *RouteHandler) GetRoute(c echo.Context) error {
	userID, ok, err := GetUserIDFromContextOrTestHeader(c)
	if err != nil {
		return c.JSON(http.StatusBadRequest, map[string]string{
			"error": "invalid test user id",
		})
	}
	if !ok {
		return c.JSON(http.StatusUnauthorized, map[string]string{
			"error": "Authorization header required",
		})
	}

	routeID, err := uuid.Parse(c.Param("id"))
	if err != nil {
		return c.JSON(http.StatusBadRequest, map[string]string{
			"error": "invalid route id",
		})
	}

	req := dto.GetRouteRequest{
		RouteID: routeID,
		UserID:  userID,
	}

	resp, err := h.getRouteUC.Execute(c.Request().Context(), req)
	if err != nil {
		status, msg, internalErr := HttpError(err)
		h.log.ErrorContext(c.Request().Context(), "failed to get route",
			slog.Int("status", status),
			slog.String("public_msg", msg),
			slog.Any("cause", internalErr),
		)
		return c.JSON(status, map[string]string{"error": msg})
	}

	return c.JSON(http.StatusOK, resp)
}

func (h *RouteHandler) GetRouteList(c echo.Context) error {
	userID, ok, err := GetUserIDFromContextOrTestHeader(c)
	if err != nil {
		return c.JSON(http.StatusBadRequest, map[string]string{
			"error": "invalid test user id",
		})
	}
	if !ok {
		return c.JSON(http.StatusUnauthorized, map[string]string{
			"error": "Authorization header required",
		})
	}

	var req dto.GetRouteListRequest

	if err := c.Bind(&req); err != nil {
		return c.JSON(http.StatusBadRequest, map[string]string{
			"error": "invalid json",
		})
	}

	req.UserID = userID

	resp, err := h.getRouteListUC.Execute(c.Request().Context(), req)
	if err != nil {
		status, msg, internalErr := HttpError(err)
		h.log.ErrorContext(c.Request().Context(), "failed to get routes list",
			slog.Int("status", status),
			slog.String("public_msg", msg),
			slog.Any("cause", internalErr),
		)
		return c.JSON(status, map[string]string{"error": msg})
	}

	return c.JSON(http.StatusOK, resp)
}
