-- liquibase formatted sql

-- changeset Petra:1787037025929-4

ALTER TABLE item
    ADD CONSTRAINT unique_storage_product UNIQUE (item, storage_id);
