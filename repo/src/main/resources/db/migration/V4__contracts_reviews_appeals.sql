-- V4: Contracts, reviews, and appeals tables
-- Compatible with MySQL 8 and H2 (MySQL mode)

CREATE TABLE contract_templates (
    id          BIGINT AUTO_INCREMENT NOT NULL,
    name        VARCHAR(255),
    description TEXT,
    active      BOOLEAN   DEFAULT TRUE,
    created_by  BIGINT,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_contract_templates PRIMARY KEY (id),
    CONSTRAINT fk_contract_templates_user FOREIGN KEY (created_by) REFERENCES users (id)
);

CREATE TABLE contract_template_versions (
    id              BIGINT AUTO_INCREMENT NOT NULL,
    template_id     BIGINT,
    version_number  INT,
    content         TEXT,
    change_notes    TEXT,
    created_by      BIGINT,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_contract_template_versions PRIMARY KEY (id),
    CONSTRAINT fk_contract_template_versions_template FOREIGN KEY (template_id) REFERENCES contract_templates (id),
    CONSTRAINT fk_contract_template_versions_user     FOREIGN KEY (created_by)  REFERENCES users (id)
);

CREATE TABLE contract_clause_fields (
    id                  BIGINT AUTO_INCREMENT NOT NULL,
    template_version_id BIGINT,
    field_name          VARCHAR(100),
    field_type          VARCHAR(50),
    field_label         VARCHAR(255),
    required            BOOLEAN DEFAULT FALSE,
    default_value       VARCHAR(500),
    display_order       INT     DEFAULT 0,
    CONSTRAINT pk_contract_clause_fields PRIMARY KEY (id),
    CONSTRAINT fk_contract_clause_fields_version FOREIGN KEY (template_version_id) REFERENCES contract_template_versions (id)
);

CREATE TABLE contract_instances (
    id                  BIGINT AUTO_INCREMENT NOT NULL,
    order_id            BIGINT,
    template_version_id BIGINT,
    user_id             BIGINT,
    reviewer_id         BIGINT,
    contract_status     VARCHAR(30) DEFAULT 'INITIATED',
    rendered_content    TEXT,
    field_values        TEXT,
    start_date          DATE,
    end_date            DATE,
    signed_at           TIMESTAMP NULL,
    archived_at         TIMESTAMP NULL,
    version             INT         DEFAULT 0,
    created_at          TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_contract_instances PRIMARY KEY (id),
    CONSTRAINT fk_contract_instances_order           FOREIGN KEY (order_id)            REFERENCES orders (id),
    CONSTRAINT fk_contract_instances_template_ver    FOREIGN KEY (template_version_id) REFERENCES contract_template_versions (id),
    CONSTRAINT fk_contract_instances_user            FOREIGN KEY (user_id)             REFERENCES users (id),
    CONSTRAINT fk_contract_instances_reviewer        FOREIGN KEY (reviewer_id)         REFERENCES users (id)
);

CREATE TABLE signature_artifacts (
    id              BIGINT AUTO_INCREMENT NOT NULL,
    contract_id     BIGINT,
    signer_id       BIGINT,
    signature_type  VARCHAR(30),
    file_path       VARCHAR(500),
    signature_hash  VARCHAR(255),
    checksum        VARCHAR(255),
    signed_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_signature_artifacts PRIMARY KEY (id),
    CONSTRAINT fk_signature_artifacts_contract FOREIGN KEY (contract_id) REFERENCES contract_instances (id),
    CONSTRAINT fk_signature_artifacts_signer   FOREIGN KEY (signer_id)   REFERENCES users (id)
);

CREATE TABLE evidence_files (
    id           BIGINT AUTO_INCREMENT NOT NULL,
    entity_type  VARCHAR(50),
    entity_id    BIGINT,
    file_name    VARCHAR(255),
    file_path    VARCHAR(500),
    content_type VARCHAR(100),
    file_size    BIGINT,
    checksum     VARCHAR(255),
    uploaded_by  BIGINT,
    uploaded_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_evidence_files PRIMARY KEY (id),
    CONSTRAINT fk_evidence_files_user FOREIGN KEY (uploaded_by) REFERENCES users (id)
);

CREATE TABLE reviews (
    id               BIGINT AUTO_INCREMENT NOT NULL,
    order_id         BIGINT NOT NULL,
    reviewer_user_id BIGINT,
    rating           INT,
    review_text      VARCHAR(1000),
    created_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_reviews PRIMARY KEY (id),
    CONSTRAINT uq_reviews_order  UNIQUE (order_id),
    CONSTRAINT fk_reviews_order  FOREIGN KEY (order_id)         REFERENCES orders (id),
    CONSTRAINT fk_reviews_user   FOREIGN KEY (reviewer_user_id) REFERENCES users (id)
);

CREATE TABLE review_images (
    id            BIGINT AUTO_INCREMENT NOT NULL,
    review_id     BIGINT,
    file_name     VARCHAR(255),
    file_path     VARCHAR(500),
    content_type  VARCHAR(100),
    file_size     BIGINT,
    checksum      VARCHAR(255),
    display_order INT       DEFAULT 0,
    uploaded_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_review_images PRIMARY KEY (id),
    CONSTRAINT fk_review_images_review FOREIGN KEY (review_id) REFERENCES reviews (id)
);

CREATE TABLE appeals (
    id             BIGINT AUTO_INCREMENT NOT NULL,
    order_id       BIGINT,
    contract_id    BIGINT NULL,
    appellant_id   BIGINT,
    appeal_status  VARCHAR(30) DEFAULT 'OPEN',
    reason         TEXT,
    created_at     TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_appeals PRIMARY KEY (id),
    CONSTRAINT fk_appeals_order    FOREIGN KEY (order_id)     REFERENCES orders (id),
    CONSTRAINT fk_appeals_contract FOREIGN KEY (contract_id)  REFERENCES contract_instances (id),
    CONSTRAINT fk_appeals_user     FOREIGN KEY (appellant_id) REFERENCES users (id)
);

CREATE TABLE arbitration_outcomes (
    id          BIGINT AUTO_INCREMENT NOT NULL,
    appeal_id   BIGINT NOT NULL,
    decided_by  BIGINT,
    outcome     VARCHAR(50),
    reasoning   TEXT,
    decided_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_arbitration_outcomes PRIMARY KEY (id),
    CONSTRAINT uq_arbitration_outcomes_appeal UNIQUE (appeal_id),
    CONSTRAINT fk_arbitration_outcomes_appeal FOREIGN KEY (appeal_id)  REFERENCES appeals (id),
    CONSTRAINT fk_arbitration_outcomes_user   FOREIGN KEY (decided_by) REFERENCES users (id)
);
