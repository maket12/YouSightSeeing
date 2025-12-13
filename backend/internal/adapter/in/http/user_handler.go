package http

import (
	"YouSightSeeing/backend/internal/app/dto"
	"YouSightSeeing/backend/internal/app/usecase"
	"log/slog"
	"net/http"

	"github.com/labstack/echo/v4"
)

type UserHandler struct {
	log                 *slog.Logger
	getUserUC           *usecase.GetUserUC
	updateUserUC        *usecase.UpdateUserUC
	updateUserPictureUC *usecase.UpdateUserPictureUC
}

func NewUserHandler(
	log *slog.Logger,
	getUserUc *usecase.GetUserUC,
	updateUserUc *usecase.UpdateUserUC,
	updateUserPictureUc *usecase.UpdateUserPictureUC) *UserHandler {
	return &UserHandler{
		log:                 log,
		getUserUC:           getUserUc,
		updateUserUC:        updateUserUc,
		updateUserPictureUC: updateUserPictureUc,
	}
}

func (h *UserHandler) GetMe(c echo.Context) error {
	userID, ok := GetUserIDFromContext(c)
	if !ok {
		return c.JSON(http.StatusUnauthorized, map[string]string{
			"error": "not authenticated",
		})
	}

	resp, err := h.getUserUC.Execute(
		c.Request().Context(), dto.GetUserRequest{
			ID: userID,
		})
	if err != nil {
		status, msg, internalErr := HttpError(err)
		h.log.ErrorContext(c.Request().Context(), "failed to get current user",
			slog.Int("status", status),
			slog.String("public_msg", msg),
			slog.Any("cause", internalErr))
		return c.JSON(status, map[string]string{"error": msg})
	}

	h.log.InfoContext(c.Request().Context(), "successfully got user",
		slog.String("user_id", resp.User.ID.String()))

	return c.JSON(http.StatusOK, resp)
}

func (h *UserHandler) UpdateMe(c echo.Context) error {
	userID, ok := GetUserIDFromContext(c)
	if !ok {
		return c.JSON(http.StatusUnauthorized, map[string]string{
			"error": "not authenticated",
		})
	}

	var req dto.UpdateUserRequest
	if err := c.Bind(&req); err != nil {
		return c.JSON(http.StatusBadRequest, map[string]string{"error": "invalid json"})
	}

	req.ID = userID

	resp, err := h.updateUserUC.Execute(c.Request().Context(), req)
	if err != nil {
		status, msg, internalErr := HttpError(err)
		h.log.ErrorContext(c.Request().Context(), "failed to update current user",
			slog.Int("status", status),
			slog.String("public_msg", msg),
			slog.Any("cause", internalErr))
		return c.JSON(status, map[string]string{"error": msg})
	}

	h.log.InfoContext(c.Request().Context(), "successfully updated user",
		slog.String("user_id", resp.ID.String()))

	return c.JSON(http.StatusOK, resp)
}

func (h *UserHandler) UpdateMePicture(c echo.Context) error {
	userID, ok := GetUserIDFromContext(c)
	if !ok {
		return c.JSON(http.StatusUnauthorized, map[string]string{
			"error": "not authenticated",
		})
	}

	var req dto.UpdateUserPictureRequest
	if err := c.Bind(&req); err != nil {
		return c.JSON(http.StatusBadRequest, map[string]string{"error": "invalid json"})
	}

	req.ID = userID

	resp, err := h.updateUserPictureUC.Execute(c.Request().Context(), req)
	if err != nil {
		status, msg, internalErr := HttpError(err)
		h.log.ErrorContext(c.Request().Context(), "failed to update picture of current user",
			slog.Int("status", status),
			slog.String("public_msg", msg),
			slog.Any("cause", internalErr))
		return c.JSON(status, map[string]string{"error": msg})
	}

	h.log.InfoContext(c.Request().Context(), "successfully updated user's picture",
		slog.String("user_id", resp.ID.String()))

	return c.JSON(http.StatusOK, resp)
}
