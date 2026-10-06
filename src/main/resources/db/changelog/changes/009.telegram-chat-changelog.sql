-- liquibase formatted sql

-- changeset Petra:telegram-chat-1
-- Чаты, в которые бот шлёт «надо закупить». Пополняется сам, когда бота
-- добавляют в группу, поэтому переживает перезапуск контейнера.
CREATE TABLE telegram_chat (
    id            BIGINT PRIMARY KEY,
    title         VARCHAR(255),
    chat_type     VARCHAR(32),
    active        BOOLEAN NOT NULL,
    registered_at TIMESTAMPTZ NOT NULL
);
