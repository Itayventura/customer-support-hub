-- Baseline schema for the Customer Support Hub.
-- Design notes:
--   * users holds profile only (identity + display info)
--   * credentials holds login secrets (shared PK with users)
--   * user_roles is its own table (multi-role capable + Spring Security alignment)
--   * customers is a sub-table with a NOT NULL agent_id (no nullable business columns)
--   * tickets belong to a customer via customer_id (== customers.user_id)

CREATE TABLE users (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    email       VARCHAR(255) NOT NULL,
    full_name   VARCHAR(255) NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE credentials (
    user_id       BIGINT       NOT NULL,
    username      VARCHAR(64)  NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id),
    UNIQUE KEY uk_credentials_username (username),
    CONSTRAINT fk_credentials_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE user_roles (
    id      BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    role    ENUM('ADMIN', 'AGENT', 'CUSTOMER') NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_roles_user_role (user_id, role),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE customers (
    user_id  BIGINT NOT NULL,
    agent_id BIGINT NOT NULL,
    PRIMARY KEY (user_id),
    KEY idx_customers_agent (agent_id),
    CONSTRAINT fk_customers_user  FOREIGN KEY (user_id)  REFERENCES users (id),
    CONSTRAINT fk_customers_agent FOREIGN KEY (agent_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE tickets (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    external_id BINARY(16)   NOT NULL,
    title       VARCHAR(255) NOT NULL,
    description TEXT         NOT NULL,
    status      ENUM('OPEN', 'IN_PROGRESS', 'RESOLVED', 'CLOSED') NOT NULL,
    customer_id BIGINT       NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tickets_external_id (external_id),
    KEY idx_tickets_customer_created (customer_id, created_at),
    CONSTRAINT fk_tickets_customer FOREIGN KEY (customer_id) REFERENCES customers (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
