package http

import (
	"YouSightSeeing/backend/internal/app/dto"
	"YouSightSeeing/backend/internal/app/usecase"
	"log/slog"
	"net/http"

	"github.com/labstack/echo/v4"
)

type EventHandler struct {
	log     *slog.Logger
	trackUC usecase.TrackUserEventUseCase
}

func NewEventHandler(
	log *slog.Logger,
	trackUC usecase.TrackUserEventUseCase,
) *EventHandler {
	return &EventHandler{
		log:     log,
		trackUC: trackUC,
	}
}

func (h *EventHandler) TrackEvent(c echo.Context) error {
	var req dto.TrackUserEventRequest

	if err := c.Bind(&req); err != nil {
		return c.JSON(http.StatusBadRequest, map[string]string{
			"error": "invalid json",
		})
	}

	userID, ok := GetUserIDFromContext(c)
	if !ok {
		return c.JSON(http.StatusUnauthorized, map[string]string{
			"error": "Authorization header required",
		})
	}

	req.UserID = userID

	resp, err := h.trackUC.Execute(c.Request().Context(), req)
	if err != nil {
		status, msg, internalErr := HttpError(err)
		h.log.ErrorContext(c.Request().Context(), "failed to track user event",
			slog.Int("status", status),
			slog.String("public_msg", msg),
			slog.Any("cause", internalErr),
		)
		return c.JSON(status, map[string]string{"error": msg})
	}

	return c.JSON(http.StatusOK, resp)
}
