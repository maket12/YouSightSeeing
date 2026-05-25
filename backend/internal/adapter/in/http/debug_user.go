package http

import (
	"strings"

	"github.com/google/uuid"
	"github.com/labstack/echo/v4"
)

func GetUserIDFromContextOrTestHeader(c echo.Context) (uuid.UUID, bool, error) {
	if userID, ok := GetUserIDFromContext(c); ok {
		return userID, true, nil
	}

	rawUserID := strings.TrimSpace(c.Request().Header.Get("X-Test-User-ID"))
	if rawUserID == "" {
		return uuid.Nil, false, nil
	}

	userID, err := uuid.Parse(rawUserID)
	if err != nil {
		return uuid.Nil, false, err
	}

	return userID, true, nil
}
