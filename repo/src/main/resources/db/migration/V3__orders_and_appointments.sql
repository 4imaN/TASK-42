-- V3: Appointments and orders tables
-- Compatible with MySQL 8 and H2 (MySQL mode)

CREATE TABLE appointments (
    id               BIGINT AUTO_INCREMENT NOT NULL,
    appointment_date DATE,
    start_time       VARCHAR(5),
    end_time         VARCHAR(5),
    appointment_type VARCHAR(20),
    slots_available  INT       DEFAULT 1,
    slots_booked     INT       DEFAULT 0,
    created_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_appointments PRIMARY KEY (id)
);

CREATE TABLE orders (
    id                       BIGINT AUTO_INCREMENT NOT NULL,
    user_id                  BIGINT,
    appointment_id           BIGINT,
    order_status             VARCHAR(30)   DEFAULT 'PENDING_CONFIRMATION',
    appointment_type         VARCHAR(20),
    reschedule_count         INT           DEFAULT 0,
    cancellation_reason      VARCHAR(500),
    cancellation_approved_by BIGINT,
    reviewer_id              BIGINT,
    total_price              DECIMAL(10,2) DEFAULT 0.00,
    currency                 VARCHAR(3)    DEFAULT 'USD',
    version                  INT           DEFAULT 0,
    created_at               TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    updated_at               TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_orders PRIMARY KEY (id),
    CONSTRAINT fk_orders_user              FOREIGN KEY (user_id)                  REFERENCES users (id),
    CONSTRAINT fk_orders_appointment       FOREIGN KEY (appointment_id)           REFERENCES appointments (id),
    CONSTRAINT fk_orders_cancellation_user FOREIGN KEY (cancellation_approved_by) REFERENCES users (id),
    CONSTRAINT fk_orders_reviewer         FOREIGN KEY (reviewer_id)              REFERENCES users (id)
);

CREATE TABLE order_items (
    id                 BIGINT AUTO_INCREMENT NOT NULL,
    order_id           BIGINT,
    item_id            BIGINT,
    snapshot_title     VARCHAR(500),
    snapshot_category  VARCHAR(100),
    snapshot_condition VARCHAR(50),
    snapshot_price     DECIMAL(10,2),
    adjusted_category  VARCHAR(100),
    adjusted_by        BIGINT,
    adjusted_at        TIMESTAMP NULL,
    CONSTRAINT pk_order_items PRIMARY KEY (id),
    CONSTRAINT fk_order_items_order       FOREIGN KEY (order_id)    REFERENCES orders (id),
    CONSTRAINT fk_order_items_item        FOREIGN KEY (item_id)     REFERENCES recycling_items (id),
    CONSTRAINT fk_order_items_adjusted_by FOREIGN KEY (adjusted_by) REFERENCES users (id)
);

CREATE TABLE order_operation_logs (
    id              BIGINT AUTO_INCREMENT NOT NULL,
    order_id        BIGINT,
    actor_id        BIGINT,
    operation       VARCHAR(50),
    previous_status VARCHAR(30),
    new_status      VARCHAR(30),
    details         TEXT,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_order_operation_logs PRIMARY KEY (id),
    CONSTRAINT fk_order_operation_logs_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT fk_order_operation_logs_actor FOREIGN KEY (actor_id) REFERENCES users (id)
);
