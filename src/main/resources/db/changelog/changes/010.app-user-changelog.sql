-- liquibase formatted sql

-- changeset Petra:app-user-1
-- Простая авторизация для тестового стенда (ветка test-branch).
CREATE TABLE app_user (
    id            BIGSERIAL PRIMARY KEY,
    username      VARCHAR(50)  NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(16)  NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL
);
