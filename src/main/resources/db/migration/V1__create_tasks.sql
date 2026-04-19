CREATE TABLE tasks (
    id BIGINT NOT NULL,
    title VARCHAR(500) NOT NULL,
    status VARCHAR(100) NOT NULL,
    assignee_id VARCHAR(100),
    deadline DATE,
    updated_at TIMESTAMP NOT NULL,
    percent_done INTEGER NOT NULL DEFAULT 0,

    CONSTRAINT pk_tasks PRIMARY KEY (id)
);