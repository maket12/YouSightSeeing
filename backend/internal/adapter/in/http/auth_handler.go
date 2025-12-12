package http

import (
	"YouSightSeeing/backend/internal/app/dto"
	"YouSightSeeing/backend/internal/app/usecase"
	"log/slog"
	"net/http"

	"github.com/labstack/echo/v4"
)

type AuthHandler struct {
	log       *slog.Logger
	AuthUC    *usecase.GoogleAuthUC
	RefreshUC *usecase.RefreshTokenUC
	LogoutUC  *usecase.LogoutUC
}

func NewAuthHandler(
	log *slog.Logger,
	authUC *usecase.GoogleAuthUC,
	refreshUC *usecase.RefreshTokenUC,
	logoutUC *usecase.LogoutUC,

) *AuthHandler {
	return &AuthHandler{
		log:       log,
		AuthUC:    authUC,
		RefreshUC: refreshUC,
		LogoutUC:  logoutUC,
	}
}

func (h *AuthHandler) GoogleAuth(c echo.Context) error {
	var req dto.GoogleAuthRequest
	if err := c.Bind(&req); err != nil {
		return c.JSON(http.StatusBadRequest, map[string]string{"error": "invalid json"})
	}

	resp, err := h.AuthUC.Execute(c.Request().Context(), req)
	if err != nil {
		status, msg, internalErr := HttpError(err)
		h.log.ErrorContext(c.Request().Context(), "failed to auth",
			slog.Int("status", status),
			slog.String("public_msg", msg),
			slog.Any("cause", internalErr))
		return c.JSON(status, map[string]string{"error": msg})
	}

	h.log.InfoContext(c.Request().Context(), "successful authentification",
		slog.String("user_id", resp.User.ID.String()))

	return c.JSON(http.StatusCreated, resp)
}

func (h *AuthHandler) RefreshToken(c echo.Context) error {
	var req dto.RefreshTokenRequest
	if err := c.Bind(&req); err != nil {
		return c.JSON(http.StatusBadRequest, map[string]string{"error": "invalid json"})
	}

	resp, err := h.RefreshUC.Execute(c.Request().Context(), req)
	if err != nil {
		status, msg, internalErr := HttpError(err)
		h.log.ErrorContext(c.Request().Context(), "failed to refresh tokens",
			slog.Int("status", status),
			slog.String("public_msg", msg),
			slog.Any("cause", internalErr))
		return c.JSON(status, map[string]string{"error": msg})
	}

	h.log.InfoContext(c.Request().Context(), "successful tokens refresh",
		slog.String("user_id", resp.User.ID.String()))

	return c.JSON(http.StatusOK, resp)
}

func (h *AuthHandler) Logout(c echo.Context) error {
	var req dto.LogoutRequest
	if err := c.Bind(&req); err != nil {
		return c.JSON(http.StatusBadRequest, map[string]string{"error": "invalid json"})
	}

	resp, err := h.LogoutUC.Execute(c.Request().Context(), req)
	if err != nil {
		status, msg, internalErr := HttpError(err)
		h.log.ErrorContext(c.Request().Context(), "failed to logout",
			slog.Int("status", status),
			slog.String("public_msg", msg),
			slog.Any("cause", internalErr))
		return c.JSON(status, map[string]string{"error": msg})
	}

	h.log.InfoContext(c.Request().Context(), "successful logout",
		slog.String("user_id", resp.UserID.String()))

	return c.JSON(http.StatusOK, resp)
}
