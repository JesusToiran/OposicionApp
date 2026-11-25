CREATE TABLE c1_tai.convocatoria (
    id uuid PRIMARY KEY,
    denominacion varchar(300) NOT NULL CHECK (btrim(denominacion) <> ''),
    anio_referencia integer CHECK (anio_referencia BETWEEN 1900 AND 9999),
    referencia_oficial varchar(500)
);

CREATE TABLE c1_tai.examen (
    id uuid PRIMARY KEY,
    convocatoria_id uuid NOT NULL REFERENCES c1_tai.convocatoria(id),
    denominacion varchar(300) NOT NULL CHECK (btrim(denominacion) <> '')
);
CREATE INDEX examen_convocatoria_idx ON c1_tai.examen(convocatoria_id);

CREATE TABLE c1_tai.ejercicio (
    id uuid PRIMARY KEY,
    examen_id uuid NOT NULL REFERENCES c1_tai.examen(id),
    denominacion varchar(300) NOT NULL CHECK (btrim(denominacion) <> '')
);
CREATE INDEX ejercicio_examen_idx ON c1_tai.ejercicio(examen_id);

CREATE TABLE c1_tai.documento_oficial (
    id uuid PRIMARY KEY,
    identificacion_original varchar(500) NOT NULL CHECK (btrim(identificacion_original) <> ''),
    procedencia varchar(500) NOT NULL CHECK (btrim(procedencia) <> ''),
    tipo varchar(100) NOT NULL CHECK (btrim(tipo) <> ''),
    referencia_origen varchar(500),
    url_origen varchar(2048) CHECK (url_origen IS NULL OR url_origen ~ '^https?://'),
    verificado boolean NOT NULL DEFAULT false,
    evidencia_verificacion varchar(1000),
    CONSTRAINT documento_evidencia CHECK (
        NOT verificado OR nullif(btrim(evidencia_verificacion), '') IS NOT NULL
    )
);

-- Una sola identidad documental puede vincularse a distintos niveles sin duplicarse.
CREATE TABLE c1_tai.vinculo_documental (
    id uuid PRIMARY KEY,
    documento_id uuid NOT NULL REFERENCES c1_tai.documento_oficial(id),
    convocatoria_id uuid REFERENCES c1_tai.convocatoria(id),
    examen_id uuid REFERENCES c1_tai.examen(id),
    ejercicio_id uuid REFERENCES c1_tai.ejercicio(id),
    CONSTRAINT vinculo_un_destino CHECK (num_nonnulls(convocatoria_id, examen_id, ejercicio_id) = 1)
);
CREATE UNIQUE INDEX vinculo_convocatoria_idx ON c1_tai.vinculo_documental(convocatoria_id, documento_id)
    WHERE convocatoria_id IS NOT NULL;
CREATE UNIQUE INDEX vinculo_examen_idx ON c1_tai.vinculo_documental(examen_id, documento_id)
    WHERE examen_id IS NOT NULL;
CREATE UNIQUE INDEX vinculo_ejercicio_idx ON c1_tai.vinculo_documental(ejercicio_id, documento_id)
    WHERE ejercicio_id IS NOT NULL;
CREATE INDEX vinculo_documento_idx ON c1_tai.vinculo_documental(documento_id);
