CREATE TABLE c1_tai.plan_tai_recurso (
    ruta text PRIMARY KEY CHECK (ruta <> '' AND ruta NOT LIKE '%..%' AND ruta NOT LIKE '/%'),
    sha256 varchar(64) NOT NULL CHECK (sha256 ~ '^[0-9a-f]{64}$'),
    bytes bigint NOT NULL CHECK (bytes > 0),
    tipo text NOT NULL,
    contenido_json jsonb,
    html_lectura text,
    css_lectura text,
    CHECK ((html_lectura IS NULL) = (css_lectura IS NULL))
);

CREATE TABLE study.plan_tai_estado (
    usuario varchar(255) PRIMARY KEY CHECK (btrim(usuario) <> ''),
    revision bigint NOT NULL DEFAULT 0 CHECK (revision >= 0),
    estado jsonb NOT NULL DEFAULT '{}' CHECK (jsonb_typeof(estado) = 'object'),
    actualizado_en timestamptz NOT NULL DEFAULT current_timestamp
);

CREATE TABLE study.plan_tai_operacion (
    usuario varchar(255) NOT NULL REFERENCES study.plan_tai_estado(usuario),
    id uuid NOT NULL,
    contenido_sha256 varchar(64) NOT NULL CHECK (contenido_sha256 ~ '^[0-9a-f]{64}$'),
    revision_resultado bigint NOT NULL CHECK (revision_resultado > 0),
    PRIMARY KEY (usuario, id)
);
