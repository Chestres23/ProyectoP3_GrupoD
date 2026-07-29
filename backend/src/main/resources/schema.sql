-- Esquema DDL para R2DBC (no tiene ddl-auto como JPA)
-- Se ejecuta automáticamente al iniciar la aplicación

CREATE TABLE IF NOT EXISTS users (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(120)  NOT NULL,
    email       VARCHAR(120)  NOT NULL UNIQUE,
    password    VARCHAR(255)  NOT NULL,
    phone       VARCHAR(20),
    role        VARCHAR(10)   NOT NULL DEFAULT 'USER',
    active      BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP,
    updated_at  TIMESTAMP
);

CREATE TABLE IF NOT EXISTS lost_items (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(120)  NOT NULL,
    description     TEXT,
    category        VARCHAR(50)   NOT NULL,
    location_found  VARCHAR(120),
    date_found      DATE,
    image_url       VARCHAR(500),
    image_data      BLOB,
    image_type      VARCHAR(60),
    status          VARCHAR(20)   NOT NULL DEFAULT 'FOUND',
    active          BOOLEAN       NOT NULL DEFAULT TRUE,
    user_id         BIGINT        NOT NULL,
    created_at      TIMESTAMP,
    updated_at      TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS claims (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    claim_date  TIMESTAMP     NOT NULL,
    observation TEXT          NOT NULL,
    status      VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    active      BOOLEAN       NOT NULL DEFAULT TRUE,
    user_id     BIGINT        NOT NULL,
    item_id     BIGINT        NOT NULL,
    created_at  TIMESTAMP,
    updated_at  TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (item_id) REFERENCES lost_items(id)
);
