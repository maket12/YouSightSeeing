CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY,
    google_sub VARCHAR(255) UNIQUE NOT NULL,

    email VARCHAR(255) UNIQUE NOT NULL,
    full_name VARCHAR(255),
    picture TEXT,

    first_name VARCHAR(100),
    last_name VARCHAR(100),
    email_verified BOOLEAN DEFAULT FALSE,
    google_domain VARCHAR(100),
    locale VARCHAR(10),

    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP
);
