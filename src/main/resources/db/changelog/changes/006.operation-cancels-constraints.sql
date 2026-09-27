-- liquibase formatted sql

-- changeset Petra:1787037025929-11
ALTER TABLE operation
    ADD CONSTRAINT fk_operation_cancels_operation
        FOREIGN KEY (cancels_operation_id) REFERENCES operation (id);

-- changeset Petra:1787037025929-12
ALTER TABLE operation
    ADD CONSTRAINT uq_operation_cancels_operation_id UNIQUE (cancels_operation_id);