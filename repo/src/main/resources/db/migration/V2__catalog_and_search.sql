-- V2: Catalog, search, and seller metrics tables
-- Compatible with MySQL 8 and H2 (MySQL mode)

CREATE TABLE recycling_items (
    id               BIGINT AUTO_INCREMENT NOT NULL,
    title            VARCHAR(500),
    normalized_title VARCHAR(500),
    description      TEXT,
    category         VARCHAR(100),
    item_condition   VARCHAR(50),
    price            DECIMAL(10,2),
    currency         VARCHAR(3)  DEFAULT 'USD',
    seller_id        BIGINT,
    active           BOOLEAN     DEFAULT TRUE,
    created_at       TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_recycling_items PRIMARY KEY (id),
    CONSTRAINT fk_recycling_items_seller FOREIGN KEY (seller_id) REFERENCES users (id)
);

CREATE TABLE item_fingerprints (
    id                   BIGINT AUTO_INCREMENT NOT NULL,
    item_id              BIGINT       NOT NULL,
    fingerprint_hash     VARCHAR(255),
    normalized_attributes TEXT,
    duplicate_status     VARCHAR(20) DEFAULT 'UNIQUE',
    reviewed             BOOLEAN     DEFAULT FALSE,
    created_at           TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_item_fingerprints PRIMARY KEY (id),
    CONSTRAINT uq_item_fingerprints_item UNIQUE (item_id),
    CONSTRAINT fk_item_fingerprints_item FOREIGN KEY (item_id) REFERENCES recycling_items (id)
);

CREATE TABLE seller_metrics (
    id                   BIGINT AUTO_INCREMENT NOT NULL,
    seller_id            BIGINT         NOT NULL,
    credit_score         DECIMAL(5,2)   DEFAULT 50.00,
    positive_rate        DECIMAL(5,4)   DEFAULT 0.0000,
    total_transactions   INT            DEFAULT 0,
    positive_transactions INT           DEFAULT 0,
    average_rating       DECIMAL(3,2)   DEFAULT 0.00,
    recent_review_quality DECIMAL(5,2)  DEFAULT 0.00,
    updated_at           TIMESTAMP      DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_seller_metrics PRIMARY KEY (id),
    CONSTRAINT uq_seller_metrics_seller UNIQUE (seller_id),
    CONSTRAINT fk_seller_metrics_seller FOREIGN KEY (seller_id) REFERENCES users (id)
);

CREATE TABLE search_logs (
    id               BIGINT AUTO_INCREMENT NOT NULL,
    user_id          BIGINT,
    search_term      VARCHAR(500),
    category_filter  VARCHAR(100),
    condition_filter VARCHAR(50),
    min_price        DECIMAL(10,2),
    max_price        DECIMAL(10,2),
    result_count     INT,
    searched_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_search_logs PRIMARY KEY (id),
    CONSTRAINT fk_search_logs_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE search_click_logs (
    id            BIGINT AUTO_INCREMENT NOT NULL,
    user_id       BIGINT,
    search_log_id BIGINT,
    item_id       BIGINT,
    clicked_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_search_click_logs PRIMARY KEY (id),
    CONSTRAINT fk_search_click_logs_user   FOREIGN KEY (user_id)       REFERENCES users (id),
    CONSTRAINT fk_search_click_logs_log    FOREIGN KEY (search_log_id) REFERENCES search_logs (id),
    CONSTRAINT fk_search_click_logs_item   FOREIGN KEY (item_id)       REFERENCES recycling_items (id)
);

CREATE TABLE search_trends (
    id              BIGINT AUTO_INCREMENT NOT NULL,
    search_term     VARCHAR(500),
    search_count    INT  DEFAULT 1,
    last_searched_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    period_start    DATE,
    period_end      DATE,
    CONSTRAINT pk_search_trends PRIMARY KEY (id)
);

CREATE TABLE ranking_strategy_versions (
    id                         BIGINT AUTO_INCREMENT NOT NULL,
    version_label              VARCHAR(100),
    credit_score_weight        DECIMAL(5,4) DEFAULT 0.3000,
    positive_rate_weight       DECIMAL(5,4) DEFAULT 0.4000,
    review_quality_weight      DECIMAL(5,4) DEFAULT 0.3000,
    min_credit_score_threshold DECIMAL(5,2) DEFAULT 0.00,
    min_positive_rate_threshold DECIMAL(5,4) DEFAULT 0.0000,
    active                     BOOLEAN      DEFAULT FALSE,
    created_by                 BIGINT,
    created_at                 TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_ranking_strategy_versions PRIMARY KEY (id),
    CONSTRAINT fk_ranking_strategy_versions_user FOREIGN KEY (created_by) REFERENCES users (id)
);
