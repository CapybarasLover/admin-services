-- liquibase formatted sql

-- changeset Petra:1787037025929-5

ALTER TABLE item ADD COLUMN cost DECIMAL;

-- changeset Petra:1787037025929-6

ALTER TABLE Operation ADD COLUMN operation_cost DECIMAL;