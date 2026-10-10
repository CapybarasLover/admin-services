-- liquibase formatted sql

-- changeset Petra:1787037025929-7

ALTER TABLE item ALTER COLUMN cost SET NOT NULL;

-- changeset Petra:1787037025929-8

ALTER TABLE operation ALTER COLUMN operation_cost SET NOT NULL;