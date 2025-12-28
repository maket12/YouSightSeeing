package main

import (
	"YouSightSeeing/backend/cmd/app/config"
	adaptergv "YouSightSeeing/backend/internal/adapter/in/google"
	adapterhttp "YouSightSeeing/backend/internal/adapter/in/http"
	adapterdb "YouSightSeeing/backend/internal/adapter/out/db"
	adaptergeo "YouSightSeeing/backend/internal/adapter/out/gpf"
	adaptertg "YouSightSeeing/backend/internal/adapter/out/jwt"
	adapterors "YouSightSeeing/backend/internal/adapter/out/ors"
	"YouSightSeeing/backend/internal/app/usecase"
	"context"
	"errors"
	"log"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"
)

func parseLogLevel(level string) slog.Level {
	switch level {
	case "DEBUG":
		return slog.LevelDebug
	case "INFO":
		return slog.LevelInfo
	case "WARN":
		return slog.LevelWarn
	case "ERROR":
		return slog.LevelError
	default:
		return slog.LevelInfo
	}
}

func main() {
	// ======================
	// 1. Load config
	// ======================
	cfg, err := config.Load()
	if err != nil {
		log.Fatalf("failed to load config: %v", err)
	}

	// ======================
	// 2. Setup logger
	// ======================

	logger := slog.New(slog.NewTextHandler(os.Stdout, &slog.HandlerOptions{
		Level: parseLogLevel(cfg.LogLevel),
	}))

	// ======================
	// 3. Connect to Postgres
	// ======================
	db, err := adapterdb.NewPostgres(cfg.DatabaseDSN)
	if err != nil {
		logger.Error("failed to connect db", slog.Any("err", err))
		os.Exit(1)
	}

	// ======================
	// 4. Repositories
	// ======================
	usersRepo := adapterdb.NewUserRepository(db)
	rTokensRepo := adapterdb.NewRefreshTokensRepository(db)
	googleVerfRepo := adaptergv.NewOAuthVerifier(cfg.GoogleClientID)
	tokensGeneratorRepo := adaptertg.NewTokensGenerator(
		cfg.AccessSecret,
		cfg.RefreshSecret,
		cfg.AccessDuration,
		cfg.RefreshDuration,
	)
	routeCalculator := adapterors.NewRouteCalculator(cfg.ORSApiKey)
	placesService := adaptergeo.NewPlacesService(cfg.GeopifyAPIKey)

	// ======================
	// 5. Usecases
	// ======================
	googleAuthUC := usecase.NewGoogleAuthUC(
		usersRepo, rTokensRepo,
		googleVerfRepo, tokensGeneratorRepo,
		cfg.AccessDuration, cfg.RefreshDuration,
	)
	refreshTokenUC := usecase.NewRefreshTokenUC(
		usersRepo, rTokensRepo,
		tokensGeneratorRepo, cfg.AccessDuration,
		cfg.RefreshDuration,
	)
	logoutUC := usecase.NewLogoutUC(rTokensRepo)
	getUserUC := usecase.NewGetUserUC(usersRepo)
	updateUserUC := usecase.NewUpdateUserUC(usersRepo)
	updateUserPicUC := usecase.NewUpdateUserPictureUC(usersRepo)
	calculateRouteUC := usecase.NewCalculateRouteUC(routeCalculator)
	searchPlacesUC := usecase.NewSearchPlacesUC(placesService)

	// ======================
	// 6. Handlers (REST)
	// ======================
	authHandler := adapterhttp.NewAuthHandler(
		logger, googleAuthUC,
		refreshTokenUC, logoutUC,
	)
	userHandler := adapterhttp.NewUserHandler(
		logger, getUserUC,
		updateUserUC, updateUserPicUC,
	)
	routeHandler := adapterhttp.NewRouteHandler(logger, calculateRouteUC)
	placesHandler := adapterhttp.NewPlacesHandler(logger, searchPlacesUC)

	// ======================
	// 7. Router
	// ======================
	router := adapterhttp.NewRouter(
		tokensGeneratorRepo,
		authHandler,
		userHandler,
		routeHandler,
		placesHandler,
	).InitRoutes()

	// ======================
	// 8. Router config
	// ======================
	router.Server.ReadTimeout = 5 * time.Second
	router.Server.WriteTimeout = 10 * time.Second
	router.Server.IdleTimeout = 60 * time.Second

	// ======================
	// 9. Run HTTP server
	// ======================

	srv := &http.Server{
		Addr:    cfg.HTTPAddress,
		Handler: router,
	}

	go func() {
		logger.Info("starting server", slog.String("address", cfg.HTTPAddress))
		if err := router.Start(cfg.HTTPAddress); err != nil && errors.Is(err, http.ErrServerClosed) {
			logger.Error("server error", slog.Any("err", err))
			os.Exit(1)
		}
	}()

	quit := make(chan os.Signal, 1)
	signal.Notify(quit, syscall.SIGINT, syscall.SIGTERM)

	<-quit
	logger.Info("shutdown signal received")

	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	if err := srv.Shutdown(ctx); err != nil {
		logger.Error("server forced to shutdown", slog.Any("err", err))
	}

	if err := db.Close(); err != nil {
		logger.Error("failed to close database", slog.Any("err", err))
	}

	logger.Info("server exited properly")
}
