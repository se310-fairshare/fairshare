-- Baseline schema for FairShare. Consolidates the incremental migrations written during development
-- into the complete current schema. Tables are created in dependency order.

CREATE TABLE users
(
    id       BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(255) NOT NULL,
    password VARCHAR(255) NOT NULL,
    email    VARCHAR(255) NOT NULL UNIQUE,
    country  VARCHAR(255) NULL,
    currency VARCHAR(255) NULL
);

-- group_name is intentionally not unique: a user may belong to multiple groups with the same name.
-- They are distinguished by group_id, created_at and membership.
CREATE TABLE expense_group
(
    group_id      BIGINT AUTO_INCREMENT PRIMARY KEY,
    created_by    BIGINT       NOT NULL,
    group_name    VARCHAR(50)  NOT NULL,
    description   VARCHAR(255) NULL,
    base_currency CHAR(3)      NOT NULL,
    created_at    TIMESTAMP    NOT NULL,
    CONSTRAINT fk_group_created_by
        FOREIGN KEY (created_by) REFERENCES users (id)
);

CREATE TABLE user_in_group
(
    user_in_group_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id          BIGINT         NOT NULL,
    group_id         BIGINT         NOT NULL,
    net_balance      DECIMAL(19, 2) NOT NULL DEFAULT 0.00,
    CONSTRAINT uq_uig_user_group UNIQUE (user_id, group_id),
    CONSTRAINT fk_uig_user
        FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_uig_group
        FOREIGN KEY (group_id) REFERENCES expense_group (group_id)
);

CREATE TABLE recurring_expense
(
    recurring_expense_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    group_id             BIGINT         NOT NULL,
    paid_by              BIGINT         NOT NULL,
    amount               DECIMAL(10, 2) NOT NULL,
    description          VARCHAR(255)   NOT NULL,
    frequency            VARCHAR(20)    NOT NULL,
    start_date           DATE           NOT NULL,
    end_date             DATE           NULL,
    occurrence_count     INT            NOT NULL DEFAULT 0,
    next_due_date        DATE           NOT NULL,
    active               BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at           TIMESTAMP      NOT NULL,
    CONSTRAINT fk_recurring_expense_group
        FOREIGN KEY (group_id) REFERENCES expense_group (group_id),
    CONSTRAINT fk_recurring_expense_paid_by
        FOREIGN KEY (paid_by) REFERENCES users (id)
);

CREATE TABLE recurring_expense_participant
(
    recurring_expense_participant_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    recurring_expense_id             BIGINT NOT NULL,
    user_id                          BIGINT NOT NULL,
    CONSTRAINT uq_rep_user_recurring_expense
        UNIQUE (user_id, recurring_expense_id),
    CONSTRAINT fk_rep_recurring_expense
        FOREIGN KEY (recurring_expense_id) REFERENCES recurring_expense (recurring_expense_id),
    CONSTRAINT fk_rep_user
        FOREIGN KEY (user_id) REFERENCES users (id)
);

-- amount is in the group's base currency, so balances and settlements never need converting.
-- original_amount, original_currency and exchange_rate keep what the member actually entered.
-- Amounts go up to 9,999,999,999,999.99 because currencies such as KRW, IDR and JPY can pass
-- 99,999,999.99 in everyday use, e.g. USD 100,000 is about KRW 135,000,000.
CREATE TABLE expense
(
    expense_id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    group_id             BIGINT         NOT NULL,
    paid_by              BIGINT         NOT NULL,
    amount               DECIMAL(15, 2) NOT NULL,
    description          VARCHAR(255)   NOT NULL,
    expense_date         DATE           NOT NULL,
    created_at           TIMESTAMP      NOT NULL,
    recurring_expense_id BIGINT         NULL,
    original_amount      DECIMAL(15, 2) NOT NULL,
    original_currency    CHAR(3)        NOT NULL,
    exchange_rate        DECIMAL(18, 8) NOT NULL,
    CONSTRAINT fk_expense_group
        FOREIGN KEY (group_id) REFERENCES expense_group (group_id),
    CONSTRAINT fk_expense_paid_by
        FOREIGN KEY (paid_by) REFERENCES users (id),
    CONSTRAINT fk_expense_recurring_expense
        FOREIGN KEY (recurring_expense_id) REFERENCES recurring_expense (recurring_expense_id),
    CONSTRAINT uq_expense_recurring_expense_date
        UNIQUE (recurring_expense_id, expense_date)
);

-- Shares and settlements match the DECIMAL(19,2) net balances they are built from,
-- so they can always hold whatever an expense or balance does.
CREATE TABLE expense_share
(
    expense_share_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    expense_id       BIGINT         NOT NULL,
    user_id          BIGINT         NOT NULL,
    share_amount     DECIMAL(19, 2) NOT NULL,
    CONSTRAINT fk_expense_share_expense
        FOREIGN KEY (expense_id) REFERENCES expense (expense_id),
    CONSTRAINT fk_expense_share_user
        FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uq_es_user_expense
        UNIQUE (user_id, expense_id)
);

CREATE TABLE settlement
(
    id              BIGINT AUTO_INCREMENT NOT NULL,
    group_id        BIGINT                NOT NULL,
    from_user       BIGINT                NOT NULL,
    to_user         BIGINT                NOT NULL,
    amount          DECIMAL(19, 2)        NOT NULL,
    settlement_date DATE                  NULL,
    CONSTRAINT pk_settlement PRIMARY KEY (id),
    CONSTRAINT FK_SETTLEMENT_ON_GROUP
        FOREIGN KEY (group_id) REFERENCES expense_group (group_id),
    CONSTRAINT FK_SETTLEMENT_ON_FROM_USER
        FOREIGN KEY (from_user) REFERENCES users (id),
    CONSTRAINT FK_SETTLEMENT_ON_TO_USER
        FOREIGN KEY (to_user) REFERENCES users (id)
);

-- currency is the currency each debt was recorded in.
-- VARCHAR2 is Oracle-specific; this project uses MySQL, where VARCHAR is required.
CREATE TABLE individual_debt
(
    id          BIGINT AUTO_INCREMENT NOT NULL,
    creator_id  BIGINT                NOT NULL,
    payer_id    BIGINT                NOT NULL,
    debtor_id   BIGINT                NOT NULL,
    amount      DECIMAL(19, 2)        NOT NULL,
    currency    VARCHAR(3)            NOT NULL, -- NOSONAR
    description VARCHAR(255)          NOT NULL,
    debt_date   DATE                  NOT NULL,
    created_at  TIMESTAMP             NOT NULL,
    CONSTRAINT pk_individual_debt PRIMARY KEY (id),
    CONSTRAINT FK_INDIVIDUAL_DEBT_ON_CREATOR
        FOREIGN KEY (creator_id) REFERENCES users (id),
    CONSTRAINT FK_INDIVIDUAL_DEBT_ON_PAYER
        FOREIGN KEY (payer_id) REFERENCES users (id),
    CONSTRAINT FK_INDIVIDUAL_DEBT_ON_DEBTOR
        FOREIGN KEY (debtor_id) REFERENCES users (id)
);
