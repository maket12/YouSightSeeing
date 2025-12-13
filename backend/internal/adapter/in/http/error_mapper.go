package http

import (
	"YouSightSeeing/backend/internal/app/uc_errors"
	"errors"
	"net/http"
)

func HttpError(err error) (int, string, error) {
	var w *uc_errors.WrappedError
	if errors.As(err, &w) {
		switch {
		case errors.Is(err, uc_errors.GoogleTokenValidationError),
			errors.Is(w.Public, uc_errors.CreateUserError),
			errors.Is(w.Public, uc_errors.GetUserError),
			errors.Is(w.Public, uc_errors.UpdateUserError),
			errors.Is(w.Public, uc_errors.UpdateUserPictureError),
			errors.Is(w.Public, uc_errors.CreateRefreshTokenError),
			errors.Is(w.Public, uc_errors.GetRefreshTokenByUserIDError),
			errors.Is(w.Public, uc_errors.GetRefreshTokenByHashError),
			errors.Is(w.Public, uc_errors.RevokeRefreshTokenError),
			errors.Is(w.Public, uc_errors.GenerateAccessTokenError),
			errors.Is(w.Public, uc_errors.GenerateRefreshTokenError):
			return http.StatusInternalServerError, w.Public.Error(), w.Reason
		default:
			return http.StatusInternalServerError, "internal error", w.Reason
		}
	}

	switch {
	case errors.Is(err, uc_errors.UserNotFoundError),
		errors.Is(err, uc_errors.RefreshTokenNotFoundError):
		return http.StatusNotFound, err.Error(), nil
	}

	switch {
	case errors.Is(err, uc_errors.ExpiredRefreshTokenError),
		errors.Is(err, uc_errors.RevokedRefreshTokenError):
		return http.StatusUnauthorized, err.Error(), nil
	}

	switch {
	case errors.Is(err, uc_errors.EmptyEmailError),
		errors.Is(err, uc_errors.EmptyGoogleSubError),
		errors.Is(err, uc_errors.EmailNotVerifiedError),
		errors.Is(err, uc_errors.EmptyGoogleTokenError),
		errors.Is(err, uc_errors.EmptyRefreshTokenError),
		errors.Is(err, uc_errors.InvalidUserID):
		return http.StatusBadRequest, err.Error(), nil
	}

	return http.StatusInternalServerError, "internal error", nil
}
