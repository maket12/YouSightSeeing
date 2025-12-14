package http

import (
	"YouSightSeeing/backend/internal/domain/port"
	"net/http"
	"strings"

	"github.com/google/uuid"
	"github.com/labstack/echo/v4"
	"github.com/labstack/echo/v4/middleware"
)

type Router struct {
	JWTGen port.TokensGenerator
	Auth   *AuthHandler
	Route  *RouteHandler
}

func NewRouter(jwtGen port.TokensGenerator, auth *AuthHandler, route *RouteHandler) *Router {
	return &Router{
		JWTGen: jwtGen,
		Auth:   auth,
		Route:  route,
	}
}

func (r *Router) InitRoutes() *echo.Echo {
	router := echo.New()

	router.Use(middleware.Recover())
	router.Use(middleware.Logger())

	publicApi := router.Group("")
	{
		authGroup := publicApi.Group("/auth")
		{
			authGroup.POST("/google", r.Auth.GoogleAuth)
			authGroup.POST("/refresh", r.Auth.RefreshToken)
			authGroup.POST("/logout", r.Auth.Logout)
		}
	}

	privateApi := router.Group("/api")
	privateApi.Use(r.AuthMiddleware())
	{
		users := privateApi.Group("/users")
		{
			// TODO: GetMe
			users.GET("/me", nil)

			// TODO: UpdateProfile
			users.PATCH("/me", nil)

			// TODO: UpdatePicture
			users.PUT("/me/picture", nil)
		}
	}
	routesGroup := privateApi.Group("/routes")
	routesGroup.POST("/calculate", r.Route.CalculateRoute)
	return router
}

func (r *Router) AuthMiddleware() echo.MiddlewareFunc {
	return func(next echo.HandlerFunc) echo.HandlerFunc {
		return func(c echo.Context) error {
			if strings.HasPrefix(c.Path(), "/auth") {
				return next(c)
			}

			authHeader := c.Request().Header.Get("Authorization")
			if authHeader == "" {
				return c.JSON(
					http.StatusUnauthorized,
					map[string]string{
						"error": "Authorization header required",
					})
			}

			parts := strings.Split(authHeader, " ")
			if len(parts) != 2 || parts[0] != "Bearer" {
				return c.JSON(
					http.StatusUnauthorized,
					map[string]string{
						"error": "Invalid authorization format. Use: Bearer <token.",
					})
			}

			token := parts[1]

			userID, err := r.JWTGen.ValidateAccessToken(c.Request().Context(), token)
			if err != nil {
				return c.JSON(http.StatusUnauthorized, map[string]string{"error": err.Error()})
			}

			c.Set("user_id", userID)

			return next(c)
		}
	}
}

func GetUserIDFromContext(c echo.Context) (uuid.UUID, bool) {
	userIDValue := c.Get("user_id")
	if userIDValue == nil {
		return uuid.Nil, false
	}

	switch v := userIDValue.(type) {
	case uuid.UUID:
		return v, true
	case string:
		if parsed, err := uuid.Parse(v); err == nil {
			return parsed, true
		}
	}

	return uuid.Nil, false
}
