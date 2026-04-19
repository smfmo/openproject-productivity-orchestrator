CREATE TABLE time_entries (
    id BIGINT NOT NULL,
    task_id BIGINT,
    hours DECIMAL(10, 2) NOT NULL,
    date DATE NOT NULL,

    CONSTRAINT pk_time_entries PRIMARY KEY (id)
);