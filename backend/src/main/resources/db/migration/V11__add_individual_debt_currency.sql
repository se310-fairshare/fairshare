-- #2: the currency each individual debt was recorded in. A new migration rather than an edit
-- to V10, so databases that already ran V10 from this branch keep a matching checksum.
ALTER TABLE individual_debt
    ADD COLUMN currency VARCHAR(3) NULL AFTER amount;

-- Entries recorded before this were in the creator's home currency (NZD if they have none set).
UPDATE individual_debt d
    JOIN users u ON d.creator_id = u.id
SET d.currency = COALESCE(u.currency, 'NZD');

-- VARCHAR2 is Oracle-specific; this project uses MySQL, where VARCHAR is required.
ALTER TABLE individual_debt
    MODIFY currency VARCHAR(3) NOT NULL; -- NOSONAR
