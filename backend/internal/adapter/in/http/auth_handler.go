package http

import (
	"YouSightSeeing/backend/internal/app/dto"
	"YouSightSeeing/backend/internal/app/usecase"
	"log/slog"
	"net/http"

	"github.com/gin-gonic/gin"
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

func (h *AuthHandler) GoogleAuth(ctx *gin.Context) {
	var req dto.GoogleAuthRequest
	if err := ctx.ShouldBindJSON(&req); err != nil {
		ctx.JSON(http.StatusBadRequest, gin.H{"error": "invalid json"})
		return
	}

	resp, err := h.AuthUC.Execute(ctx, req)
	if err != nil {
		status, msg, internalErr := HttpError(err)
		h.log.ErrorContext(ctx, "failed to auth",
			slog.Int("status", status),
			slog.String("public_msg", msg),
			slog.Any("cause", internalErr))
		ctx.JSON(status, gin.H{"error": msg})
		return
	}

	h.log.InfoContext(ctx, "successful authentification",
		slog.String("user_id", resp.User.ID.String()))

	ctx.JSON(http.StatusCreated, resp)
}

func (h *AuthHandler) RefreshToken(ctx *gin.Context) {
	var req dto.RefreshTokenRequest
	if err := ctx.ShouldBindJSON(&req); err != nil {
		ctx.JSON(http.StatusBadRequest, gin.H{"error": "invalid json"})
		return
	}

	resp, err := h.RefreshUC.Execute(ctx, req)
	if err != nil {
		status, msg, internalErr := HttpError(err)
		h.log.ErrorContext(ctx, "failed to refresh tokens",
			slog.Int("status", status),
			slog.String("public_msg", msg),
			slog.Any("cause", internalErr))
		ctx.JSON(status, gin.H{"error": msg})
		return
	}

	h.log.InfoContext(ctx, "successful tokens refresh",
		slog.String("user_id", resp.User.ID.String()))

	ctx.JSON(http.StatusOK, resp)
}

func (h *AuthHandler) Logout(ctx *gin.Context) {
	var req dto.LogoutRequest
	if err := ctx.ShouldBindJSON(&req); err != nil {
		ctx.JSON(http.StatusBadRequest, gin.H{"error": "invalid json"})
		return
	}

	resp, err := h.LogoutUC.Execute(ctx, req)
	if err != nil {
		status, msg, internalErr := HttpError(err)
		h.log.ErrorContext(ctx, "failed to logout",
			slog.Int("status", status),
			slog.String("public_msg", msg),
			slog.Any("cause", internalErr))
		ctx.JSON(status, gin.H{"error": msg})
		return
	}

	h.log.InfoContext(ctx, "successful logout",
		slog.String("user_id", resp.UserID.String()))

	ctx.JSON(http.StatusOK, resp)
}
