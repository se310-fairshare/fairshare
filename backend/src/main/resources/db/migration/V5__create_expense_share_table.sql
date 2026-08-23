CREATE TABLE expense_share (
    expense_share_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    expense_id       BIGINT NOT NULL,
    user_id          BIGINT NOT NULL,
    share_amount     DECIMAL(10,2) NOT NULL,
    CONSTRAINT fk_expense_share_expense
        FOREIGN KEY (expense_id) REFERENCES expense (expense_id),
    CONSTRAINT fk_expense_share_user
        FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uq_es_user_expense
        UNIQUE (user_id, expense_id)
);
