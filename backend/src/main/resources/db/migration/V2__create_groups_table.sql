CREATE TABLE expense_group (
                               group_id      BIGINT AUTO_INCREMENT PRIMARY KEY,
                               created_by    BIGINT       NOT NULL,
                               group_name    VARCHAR(50)  NOT NULL,
                               description   VARCHAR(255) NULL,
                               base_currency CHAR(3)      NOT NULL,
                               created_at    TIMESTAMP    NOT NULL,
                               CONSTRAINT fk_group_created_by
                                   FOREIGN KEY (created_by) REFERENCES users (id)
);

-- group_name is intentionally not unique: a user may belong to
-- multiple groups with the same name They are distinguished by
-- group_id, created_at and membership.

CREATE TABLE user_in_group (
                            user_in_group_id BIGINT AUTO_INCREMENT PRIMARY KEY,
                            user_id  BIGINT NOT NULL,
                            group_id BIGINT NOT NULL,
                            CONSTRAINT uq_uig_user_group UNIQUE (user_id, group_id),
                            CONSTRAINT fk_uig_user
                               FOREIGN KEY (user_id) REFERENCES users (id),
                            CONSTRAINT fk_uig_group
                               FOREIGN KEY (group_id) REFERENCES expense_group (group_id)
);