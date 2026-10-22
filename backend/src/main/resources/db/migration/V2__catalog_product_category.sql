CREATE TABLE business_audit_records (
    id uuid PRIMARY KEY,
    module_code varchar(50) NOT NULL,
    action_code varchar(100) NOT NULL,
    actor_user_id uuid NULL REFERENCES users(id) ON DELETE RESTRICT,
    target_type varchar(50) NOT NULL,
    target_id uuid NOT NULL,
    target_reference varchar(100) NULL,
    from_status varchar(32) NULL,
    to_status varchar(32) NULL,
    reason text NULL,
    occurred_at timestamptz NOT NULL,
    correlation_reference varchar(100) NULL
);

CREATE INDEX ix_business_audit_target ON business_audit_records(target_type, target_id);
CREATE INDEX ix_business_audit_actor_occurred ON business_audit_records(actor_user_id, occurred_at);
CREATE INDEX ix_business_audit_module_occurred ON business_audit_records(module_code, occurred_at);

CREATE TABLE categories (
    id uuid PRIMARY KEY,
    code varchar(50) NOT NULL,
    name varchar(150) NOT NULL,
    active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT ck_categories_code_canonical CHECK (code = upper(btrim(code)) AND code <> ''),
    CONSTRAINT ck_categories_name_trimmed CHECK (name = btrim(name) AND name <> '')
);

CREATE UNIQUE INDEX ux_categories_code ON categories(code);
CREATE UNIQUE INDEX ux_categories_normalized_name ON categories(lower(name));

CREATE TABLE products (
    id uuid PRIMARY KEY,
    sku varchar(100) NOT NULL,
    name varchar(250) NOT NULL,
    description text NULL,
    category_id uuid NOT NULL REFERENCES categories(id) ON DELETE RESTRICT,
    unit_of_measure varchar(20) NOT NULL,
    active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT ck_products_sku_canonical CHECK (sku = upper(btrim(sku)) AND sku <> ''),
    CONSTRAINT ck_products_name_trimmed CHECK (name = btrim(name) AND name <> ''),
    CONSTRAINT ck_products_description_length CHECK (description IS NULL OR char_length(description) <= 2000),
    CONSTRAINT ck_products_unit_of_measure CHECK (unit_of_measure IN ('PIECE', 'SET', 'BOX'))
);

CREATE UNIQUE INDEX ux_products_sku ON products(sku);
CREATE INDEX ix_products_category_active ON products(category_id, active);
CREATE INDEX ix_products_active_sku ON products(active, sku);
CREATE INDEX ix_products_normalized_name ON products(lower(name));
