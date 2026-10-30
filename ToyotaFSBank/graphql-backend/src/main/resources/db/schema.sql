CREATE TABLE IF NOT EXISTS accounts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_number VARCHAR(64) NOT NULL,
    owner_name VARCHAR(255) NOT NULL,
    account_type VARCHAR(32) NOT NULL,
    balance DOUBLE NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS plaid_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    item_id VARCHAR(128) NOT NULL UNIQUE,
    access_token VARCHAR(255) NOT NULL,
    institution_id VARCHAR(128),
    institution_name VARCHAR(255),
    linked_at TIMESTAMP NOT NULL
);
