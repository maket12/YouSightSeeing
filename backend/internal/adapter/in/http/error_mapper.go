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
		case errors.Is(w.Public, uc_errors.CreateUserError):
			return http.StatusInternalServerError, w.Public.Error(), w.Reason
		default:
			return http.StatusInternalServerError, "internal error", w.Reason
		}
	}

	switch {
	case errors.Is(err, uc_errors.EmptyEmailError),
		errors.Is(err, uc_errors.EmailNotVerifiedError),
		errors.Is(err, uc_errors.EmptyGoogleTokenError),
		errors.Is(err, uc_errors.EmptyGoogleSubError),
		errors.Is(err, uc_errors.GoogleTokenValidationError):
		return http.StatusBadRequest, err.Error(), nil
	}

	return http.StatusInternalServerError, "internal error", nil
}
