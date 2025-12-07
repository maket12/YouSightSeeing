package http

import (
	uc_errors2 "YouSightSeeing/backend/internal/app/uc_errors"
	"errors"
	"net/http"
)

func HttpError(err error) (int, string, error) {
	var w *uc_errors2.WrappedError
	if errors.As(err, &w) {
		switch {
		case errors.Is(w.Public, uc_errors2.CreateUserError):
			return http.StatusInternalServerError, w.Public.Error(), w.Reason
		default:
			return http.StatusInternalServerError, "internal error", w.Reason
		}
	}

	switch {
	case errors.Is(err, uc_errors2.EmptyEmailError),
		errors.Is(err, uc_errors2.EmailNotVerifiedError),
		errors.Is(err, uc_errors2.EmptyGoogleTokenError),
		errors.Is(err, uc_errors2.EmptyGoogleSubError),
		errors.Is(err, uc_errors2.GoogleTokenValidationError):
		return http.StatusBadRequest, err.Error(), nil
	}

	return http.StatusInternalServerError, "internal error", nil
}
