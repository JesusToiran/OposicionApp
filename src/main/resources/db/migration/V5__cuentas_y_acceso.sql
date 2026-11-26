CREATE TABLE core.cuenta (
    id uuid PRIMARY KEY,
    correo varchar(254) NOT NULL UNIQUE CHECK (correo = lower(correo) AND btrim(correo) = correo),
    password_hash varchar(255),
    rol varchar(10) NOT NULL CHECK (rol IN ('USER','ADMIN')),
    activa boolean NOT NULL DEFAULT true,
    correo_verificado_en timestamptz,
    propietario_inicial boolean NOT NULL DEFAULT false,
    version_seguridad bigint NOT NULL DEFAULT 0 CHECK (version_seguridad >= 0),
    creada_en timestamptz NOT NULL DEFAULT current_timestamp,
    CHECK (NOT propietario_inicial OR rol = 'ADMIN')
);
CREATE TABLE core.token_cuenta (
    id uuid PRIMARY KEY,
    cuenta_id uuid NOT NULL REFERENCES core.cuenta(id),
    proposito varchar(12) NOT NULL CHECK (proposito IN ('INVITACION','VERIFICAR','RECUPERAR')),
    token_hash varchar(64) NOT NULL UNIQUE CHECK (token_hash ~ '^[0-9a-f]{64}$'),
    caduca_en timestamptz NOT NULL,
    usado_en timestamptz,
    creado_en timestamptz NOT NULL DEFAULT current_timestamp
);
CREATE INDEX token_cuenta_cuenta_idx ON core.token_cuenta(cuenta_id);
CREATE TABLE core.auditoria_cuentas (
    id uuid PRIMARY KEY,
    actor_id uuid REFERENCES core.cuenta(id),
    cuenta_id uuid REFERENCES core.cuenta(id),
    evento varchar(50) NOT NULL,
    ocurrido_en timestamptz NOT NULL DEFAULT current_timestamp
);
