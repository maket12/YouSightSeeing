package http

import "github.com/gin-gonic/gin"

type Router struct {
	Auth *AuthHandler
}

func NewRouter(auth *AuthHandler) *Router {
	return &Router{
		Auth: auth,
	}
}

func (r *Router) InitRoutes() *gin.Engine {
	router := gin.New()

	router.Use(gin.Recovery())
	router.Use(gin.Logger())

	api := router.Group("/auth")
	{
		api.POST("/google", r.Auth.GoogleAuth)
	}

	return router
}
