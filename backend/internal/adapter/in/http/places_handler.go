package http

import (
	"YouSightSeeing/backend/internal/app/dto"
	"YouSightSeeing/backend/internal/app/usecase"
	"log/slog"
	"net/http"

	"github.com/labstack/echo/v4"
)

type PlacesHandler struct {
	log      *slog.Logger
	SearchUC usecase.SearchPlacesUseCase
}

func NewPlacesHandler(log *slog.Logger, uc usecase.SearchPlacesUseCase) *PlacesHandler {
	return &PlacesHandler{
		log:      log,
		SearchUC: uc,
	}
}

func (h *PlacesHandler) SearchPlaces(c echo.Context) error {
	var req dto.SearchPoiRequest
	if err := c.Bind(&req); err != nil {
		return c.JSON(http.StatusBadRequest, map[string]string{
			"error": "invalid json",
		})
	}

	resp, err := h.SearchUC.Execute(c.Request().Context(), req)
	if err != nil {
		status, msg, internalErr := HttpError(err)
		h.log.ErrorContext(c.Request().Context(), "failed to search places",
			slog.Int("status", status),
			slog.String("public_msg", msg),
			slog.Any("cause", internalErr),
		)
		return c.JSON(status, map[string]string{"error": msg})
	}

	return c.JSON(http.StatusOK, resp)
}
