package config

import (
	"time"

	"github.com/caarlos0/env/v11"
)

type Config struct {
	HTTPAddress string `env:"HTTP_ADDRESS" envDefault:":8080"`
	LogLevel    string `env:"LOG_LEVEL" envDefault:"INFO"`

	DatabaseDSN string `env:"DATABASE_DSN,required"`

	GoogleClientID string `env:"GOOGLE_CLIENT_ID,required"`

	AccessSecret    string        `env:"ACCESS_SECRET,required"`
	AccessDuration  time.Duration `env:"ACCESS_DURATION" envDefault:"15m"`
	RefreshSecret   string        `env:"REFRESH_SECRET,required"`
	RefreshDuration time.Duration `env:"REFRESH_DURATION" envDefault:"720h"`
}

func Load() (*Config, error) {
	cfg := &Config{}
	if err := env.Parse(cfg); err != nil {
		return nil, err
	}
	return cfg, nil
}
