CREATE TABLE users (
    id uuid PRIMARY KEY,
    username varchar(100) NOT NULL,
    password_hash varchar(255) NOT NULL,
    display_name varchar(200) NOT NULL,
    active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT current_timestamp,
    updated_at timestamptz NOT NULL DEFAULT current_timestamp,
    CONSTRAINT ck_users_username_nonblank CHECK (btrim(username) <> ''),
    CONSTRAINT ck_users_username_lowercase CHECK (username = lower(username)),
    CONSTRAINT ck_users_username_trimmed CHECK (username = btrim(username)),
    CONSTRAINT ck_users_password_hash_nonblank CHECK (btrim(password_hash) <> ''),
    CONSTRAINT ck_users_display_name_nonblank CHECK (btrim(display_name) <> '')
);

CREATE UNIQUE INDEX ux_users_username_normalized ON users (lower(username));

CREATE TABLE roles (
    id uuid PRIMARY KEY,
    code varchar(50) NOT NULL,
    name varchar(100) NOT NULL,
    active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT current_timestamp,
    updated_at timestamptz NOT NULL DEFAULT current_timestamp,
    CONSTRAINT uq_roles_code UNIQUE (code),
    CONSTRAINT ck_roles_code_allowed CHECK (
        code IN (
            'ADMIN',
            'PURCHASING_STAFF',
            'WAREHOUSE_STAFF',
            'SALES_STAFF',
            'MANAGER'
        )
    ),
    CONSTRAINT ck_roles_name_nonblank CHECK (btrim(name) <> '')
);

INSERT INTO roles (id, code, name) VALUES
    ('00000000-0000-0000-0000-000000000001', 'ADMIN', 'Admin'),
    ('00000000-0000-0000-0000-000000000002', 'PURCHASING_STAFF', 'Purchasing Staff'),
    ('00000000-0000-0000-0000-000000000003', 'WAREHOUSE_STAFF', 'Warehouse Staff'),
    ('00000000-0000-0000-0000-000000000004', 'SALES_STAFF', 'Sales Staff'),
    ('00000000-0000-0000-0000-000000000005', 'MANAGER', 'Manager');

CREATE TABLE user_roles (
    user_id uuid NOT NULL,
    role_id uuid NOT NULL,
    assigned_at timestamptz NOT NULL DEFAULT current_timestamp,
    assigned_by_user_id uuid NULL,
    CONSTRAINT pk_user_roles PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE RESTRICT,
    CONSTRAINT fk_user_roles_assigned_by_user FOREIGN KEY (assigned_by_user_id) REFERENCES users (id) ON DELETE RESTRICT
);

CREATE INDEX ix_user_roles_role_id ON user_roles (role_id, user_id);

CREATE TABLE security_audit_records (
    id uuid PRIMARY KEY,
    actor_user_id uuid NULL,
    target_user_id uuid NULL,
    event_type varchar(100) NOT NULL,
    outcome varchar(32) NOT NULL,
    target_reference varchar(100) NULL,
    correlation_reference varchar(100) NULL,
    metadata jsonb NULL,
    occurred_at timestamptz NOT NULL DEFAULT current_timestamp,
    CONSTRAINT fk_security_audit_records_actor_user FOREIGN KEY (actor_user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_security_audit_records_target_user FOREIGN KEY (target_user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT ck_security_audit_records_event_type_nonblank CHECK (btrim(event_type) <> ''),
    CONSTRAINT ck_security_audit_records_outcome CHECK (outcome IN ('SUCCESS', 'FAILURE', 'DENIED')),
    CONSTRAINT ck_security_audit_records_metadata_object CHECK (metadata IS NULL OR jsonb_typeof(metadata) = 'object')
);

CREATE INDEX ix_security_audit_records_occurred_at ON security_audit_records (occurred_at DESC);
CREATE INDEX ix_security_audit_records_actor_occurred_at ON security_audit_records (actor_user_id, occurred_at DESC);
CREATE INDEX ix_security_audit_records_target_occurred_at ON security_audit_records (target_user_id, occurred_at DESC);
CREATE INDEX ix_security_audit_records_event_occurred_at ON security_audit_records (event_type, occurred_at DESC);
