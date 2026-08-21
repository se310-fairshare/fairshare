CREATE TABLE expense (
    expense_id   BIGINT AUTO_INCREMENT PRIMARY KEY,
    group_id     BIGINT       NOT NULL,
    paid_by      BIGINT       NOT NULL,
    amount       DECIMAL(10,2) NOT NULL,
    description  VARCHAR(255) NOT NULL,
    expense_date DATE         NOT NULL,
    created_at   TIMESTAMP    NOT NULL,
    CONSTRAINT fk_expense_group
        FOREIGN KEY (group_id) REFERENCES expense_group (group_id),
    CONSTRAINT fk_expense_paid_by
        FOREIGN KEY (paid_by) REFERENCES users (id)
);
