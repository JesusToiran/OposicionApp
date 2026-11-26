CREATE TABLE c1_tai.csv_lote (
    id uuid PRIMARY KEY,
    version text NOT NULL,
    csv_sha256 varchar(64) NOT NULL UNIQUE CHECK (csv_sha256 ~ '^[0-9a-f]{64}$'),
    plan_sha256 varchar(64) NOT NULL CHECK (plan_sha256 ~ '^[0-9a-f]{64}$'),
    cabeceras jsonb NOT NULL CHECK (jsonb_typeof(cabeceras) = 'array'),
    filas integer NOT NULL CHECK (filas > 0)
);

CREATE TABLE c1_tai.csv_fila (
    id uuid PRIMARY KEY,
    lote_id uuid NOT NULL REFERENCES c1_tai.csv_lote(id),
    numero integer NOT NULL CHECK (numero >= 2),
    tipo text NOT NULL CHECK (tipo IN ('preguntas', 'plantilla_respuestas', 'extraccion_pendiente_revision')),
    datos jsonb NOT NULL CHECK (jsonb_typeof(datos) = 'object'),
    contenido_sha256 varchar(64) NOT NULL CHECK (contenido_sha256 ~ '^[0-9a-f]{64}$'),
    UNIQUE (lote_id, numero)
);

CREATE TABLE c1_tai.csv_seccion (
    id uuid PRIMARY KEY,
    ejercicio_id uuid NOT NULL REFERENCES c1_tai.ejercicio(id),
    examen_origen text NOT NULL,
    codigo text NOT NULL,
    orden integer NOT NULL CHECK (orden > 0),
    denominacion text NOT NULL,
    estado_plantilla text NOT NULL,
    contexto text,
    datos jsonb NOT NULL,
    UNIQUE (examen_origen, codigo),
    UNIQUE (ejercicio_id, orden)
);
CREATE INDEX csv_seccion_ejercicio_idx ON c1_tai.csv_seccion(ejercicio_id);

CREATE TABLE c1_tai.csv_anexo (
    id uuid PRIMARY KEY,
    ruta text NOT NULL UNIQUE CHECK (ruta LIKE 'historico/%.webp' AND ruta NOT LIKE '%..%'),
    sha256 varchar(64) NOT NULL CHECK (sha256 ~ '^[0-9a-f]{64}$'),
    bytes integer NOT NULL CHECK (bytes > 0),
    tipo text NOT NULL CHECK (tipo = 'image/webp')
);
CREATE TABLE c1_tai.csv_seccion_anexo (
    id uuid PRIMARY KEY,
    seccion_id uuid NOT NULL REFERENCES c1_tai.csv_seccion(id),
    anexo_id uuid NOT NULL REFERENCES c1_tai.csv_anexo(id),
    orden integer NOT NULL CHECK (orden > 0),
    UNIQUE (seccion_id, anexo_id),
    UNIQUE (seccion_id, orden)
);

CREATE TABLE c1_tai.csv_pregunta (
    id uuid PRIMARY KEY,
    seccion_id uuid NOT NULL REFERENCES c1_tai.csv_seccion(id),
    fila_id uuid NOT NULL UNIQUE REFERENCES c1_tai.csv_fila(id),
    orden integer NOT NULL CHECK (orden > 0),
    numero integer NOT NULL CHECK (numero > 0),
    reserva boolean NOT NULL,
    anulada boolean,
    identificador_origen text NOT NULL,
    enunciado text NOT NULL CHECK (btrim(enunciado) <> ''),
    opciones jsonb NOT NULL CHECK (jsonb_typeof(opciones) = 'array' AND jsonb_array_length(opciones) = 4),
    clave varchar(1) CHECK (clave IN ('A', 'B', 'C', 'D')),
    estado_plantilla text NOT NULL,
    evidencia_plantilla jsonb NOT NULL,
    datos jsonb NOT NULL,
    UNIQUE (seccion_id, orden),
    UNIQUE (seccion_id, numero, reserva),
    CHECK (NOT coalesce(anulada, false) OR clave IS NULL),
    CHECK (estado_plantilla NOT LIKE 'sin plantilla%' OR clave IS NULL)
);
CREATE TABLE c1_tai.csv_procedencia (
    id uuid PRIMARY KEY,
    fila_id uuid NOT NULL UNIQUE REFERENCES c1_tai.csv_fila(id),
    pregunta_id uuid NOT NULL REFERENCES c1_tai.csv_pregunta(id),
    relacion text NOT NULL
);
CREATE INDEX csv_procedencia_pregunta_idx ON c1_tai.csv_procedencia(pregunta_id);
CREATE TABLE c1_tai.csv_variante (
    id uuid PRIMARY KEY,
    fila_id uuid NOT NULL UNIQUE REFERENCES c1_tai.csv_fila(id),
    pregunta_id uuid NOT NULL REFERENCES c1_tai.csv_pregunta(id),
    enunciado text NOT NULL,
    opciones jsonb NOT NULL CHECK (jsonb_typeof(opciones) = 'array' AND jsonb_array_length(opciones) = 4),
    clave_declarada text NOT NULL,
    estado text NOT NULL
);
CREATE INDEX csv_variante_pregunta_idx ON c1_tai.csv_variante(pregunta_id);
CREATE TABLE c1_tai.csv_plantilla (
    id uuid PRIMARY KEY,
    fila_id uuid NOT NULL UNIQUE REFERENCES c1_tai.csv_fila(id),
    seccion_id uuid NOT NULL REFERENCES c1_tai.csv_seccion(id),
    pregunta_id uuid UNIQUE REFERENCES c1_tai.csv_pregunta(id),
    orden integer NOT NULL CHECK (orden > 0),
    numero integer NOT NULL CHECK (numero > 0),
    reserva boolean NOT NULL,
    clave_origen text NOT NULL,
    estado text NOT NULL,
    UNIQUE (seccion_id, numero, reserva)
);
CREATE TABLE c1_tai.csv_extraccion (
    id uuid PRIMARY KEY,
    fila_id uuid NOT NULL UNIQUE REFERENCES c1_tai.csv_fila(id),
    seccion_id uuid NOT NULL REFERENCES c1_tai.csv_seccion(id),
    documento text NOT NULL,
    pagina integer NOT NULL CHECK (pagina > 0),
    contenido text NOT NULL,
    estado text NOT NULL
);
CREATE INDEX csv_extraccion_seccion_idx ON c1_tai.csv_extraccion(seccion_id);
CREATE TABLE c1_tai.csv_resolucion (
    id uuid PRIMARY KEY,
    fila_id uuid NOT NULL REFERENCES c1_tai.csv_fila(id),
    campo text NOT NULL,
    valor_original text NOT NULL,
    valor_corregido text NOT NULL,
    motivo text NOT NULL,
    evidencia jsonb NOT NULL,
    UNIQUE (fila_id, campo)
);
