-- V1: Core users and authentication tables
-- Compatible with MySQL 8 and H2 (MySQL mode)

CREATE TABLE roles (
    id          BIGINT AUTO_INCREMENT NOT NULL,
    name        VARCHAR(50)  NOT NULL,
    description VARCHAR(255),
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_roles PRIMARY KEY (id),
    CONSTRAINT uq_roles_name UNIQUE (name)
);

CREATE TABLE users (
    id                   BIGINT AUTO_INCREMENT NOT NULL,
    username             VARCHAR(100) NOT NULL,
    password_hash        VARCHAR(255) NOT NULL,
    email                VARCHAR(255),
    phone                VARCHAR(50),
    full_name            VARCHAR(255),
    seller_credit_score  DECIMAL(5,2)  DEFAULT 0.00,
    force_password_reset BOOLEAN       DEFAULT FALSE,
    enabled              BOOLEAN       DEFAULT TRUE,
    locked               BOOLEAN       DEFAULT FALSE,
    locked_until         TIMESTAMP     NULL,
    failed_attempts      INT           DEFAULT 0,
    created_at           TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uq_users_username UNIQUE (username)
);

CREATE TABLE user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    CONSTRAINT pk_user_roles PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles (id)
);

CREATE TABLE login_attempts (
    id             BIGINT AUTO_INCREMENT NOT NULL,
    username       VARCHAR(100),
    ip_address     VARCHAR(45),
    success        BOOLEAN,
    failure_reason VARCHAR(255),
    attempted_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_login_attempts PRIMARY KEY (id)
);

CREATE TABLE refresh_tokens (
    id         BIGINT AUTO_INCREMENT NOT NULL,
    user_id    BIGINT       NOT NULL,
    token_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMP    NOT NULL,
    revoked    BOOLEAN      DEFAULT FALSE,
    created_at TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_refresh_tokens PRIMARY KEY (id),
    CONSTRAINT uq_refresh_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE admin_access_logs (
    id              BIGINT AUTO_INCREMENT NOT NULL,
    admin_user_id   BIGINT       NOT NULL,
    action_type     VARCHAR(100),
    target_entity   VARCHAR(100),
    target_id       BIGINT,
    fields_revealed VARCHAR(500),
    reason          VARCHAR(500),
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_admin_access_logs PRIMARY KEY (id),
    CONSTRAINT fk_admin_access_logs_user FOREIGN KEY (admin_user_id) REFERENCES users (id)
);

-- Seed roles
INSERT INTO roles (name, description) VALUES ('ROLE_USER',     'Standard authenticated user');
INSERT INTO roles (name, description) VALUES ('ROLE_REVIEWER', 'Reviewer with order management access');
INSERT INTO roles (name, description) VALUES ('ROLE_ADMIN',    'Full administrative access');
