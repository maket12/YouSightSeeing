package http

import (
	"YouSightSeeing/backend/internal/domain/port"
	"net/http"
	"strings"

	"github.com/google/uuid"
	"github.com/labstack/echo/v4"
	"github.com/labstack/echo/v4/middleware"
	"github.com/swaggest/swgui/v5emb"
)

type Router struct {
	jwtgen port.TokensGenerator
	auth   *AuthHandler
	user   *UserHandler
	route  *RouteHandler
	place  *PlacesHandler
}

func NewRouter(jwtGen port.TokensGenerator, auth *AuthHandler, users *UserHandler, route *RouteHandler, places *PlacesHandler) *Router {
	return &Router{
		jwtgen: jwtGen,
		auth:   auth,
		user:   users,
		route:  route,
		place:  places,
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
			authGroup.POST("/google", r.auth.GoogleAuth)
			authGroup.POST("/refresh", r.auth.RefreshToken)
			authGroup.POST("/logout", r.auth.Logout)
		}
	}

	privateApi := router.Group("/api")
	privateApi.Use(r.AuthMiddleware())
	{
		users := privateApi.Group("/users")
		{
			users.GET("/me", r.user.GetMe)
			users.PATCH("/me", r.user.UpdateMe)
			users.PUT("/me/picture", r.user.UpdateMePicture)
		}
		routesGroup := privateApi.Group("/routes")
		{
			routesGroup.GET("/calculate", r.route.CalculateRoute)
		}
		placesGroup := privateApi.Group("/places")
		{
			placesGroup.POST("/search", r.place.Search)
		}
	}

	router.GET("/openapi.yaml", func(c echo.Context) error {
		return c.File("docs/openapi.yaml")
	})

	router.GET("/swagger*", echo.WrapHandler(
		v5emb.New("YouSightSeeing API", "/openapi.yaml", "/swagger/"),
	))
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

			userID, err := r.jwtgen.ValidateAccessToken(c.Request().Context(), token)
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
