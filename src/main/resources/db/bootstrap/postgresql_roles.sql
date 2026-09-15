BEGIN;

DO $$
DECLARE
	conflicting_membership text;
	conflicting_privilege text;
	conflicting_object text;
BEGIN
	SELECT format('%I -> %I', member.rolname, parent.rolname)
	INTO conflicting_membership
	FROM pg_auth_members membership
	JOIN pg_roles member ON member.oid = membership.member
	JOIN pg_roles parent ON parent.oid = membership.roleid
	WHERE member.rolname IN ('oposicionapp_app', 'oposicionapp_migrator')
	LIMIT 1;

	IF conflicting_membership IS NOT NULL THEN
		RAISE EXCEPTION 'Project role has unexpected membership: %', conflicting_membership;
	END IF;

	SELECT format('%I.%I', table_schema, table_name)
	INTO conflicting_privilege
	FROM information_schema.table_privileges
	WHERE grantee IN ('oposicionapp_app', 'oposicionapp_migrator')
		AND (
			table_schema NOT IN ('core', 'c1_tai', 'study', 'public')
			OR (table_schema = 'public' AND table_name <> 'flyway_schema_history')
		)
	LIMIT 1;

	IF conflicting_privilege IS NOT NULL THEN
		RAISE EXCEPTION 'Project role has privileges outside project schemas: %', conflicting_privilege;
	END IF;

	SELECT format('%I.%I', n.nspname, c.relname)
	INTO conflicting_object
	FROM pg_class c
	JOIN pg_namespace n ON n.oid = c.relnamespace
	JOIN pg_roles owner_role ON owner_role.oid = c.relowner
	WHERE owner_role.rolname IN ('oposicionapp_app', 'oposicionapp_migrator')
		AND (
			n.nspname NOT IN ('core', 'c1_tai', 'study', 'public')
			OR (n.nspname = 'public' AND c.relname NOT LIKE 'flyway_schema_history%')
		)
		AND n.nspname NOT LIKE 'pg_toast%'
	LIMIT 1;

	IF conflicting_object IS NOT NULL THEN
		RAISE EXCEPTION 'Project role owns object outside project schemas: %', conflicting_object;
	END IF;

	IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'oposicionapp_app') THEN
		CREATE ROLE oposicionapp_app LOGIN;
	END IF;

	IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'oposicionapp_migrator') THEN
		CREATE ROLE oposicionapp_migrator LOGIN;
	END IF;
END
$$;

ALTER ROLE oposicionapp_app WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOREPLICATION NOBYPASSRLS;
ALTER ROLE oposicionapp_migrator WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOREPLICATION NOBYPASSRLS;

REVOKE ALL ON DATABASE :"POSTGRES_DB" FROM PUBLIC;
REVOKE ALL ON DATABASE :"POSTGRES_DB" FROM oposicionapp_app;
REVOKE ALL ON DATABASE :"POSTGRES_DB" FROM oposicionapp_migrator;
GRANT CONNECT ON DATABASE :"POSTGRES_DB" TO oposicionapp_app;
GRANT CONNECT, CREATE ON DATABASE :"POSTGRES_DB" TO oposicionapp_migrator;

REVOKE CREATE ON SCHEMA public FROM PUBLIC;
REVOKE ALL ON SCHEMA public FROM oposicionapp_app;
REVOKE ALL ON SCHEMA public FROM oposicionapp_migrator;
GRANT USAGE, CREATE ON SCHEMA public TO oposicionapp_migrator;

DO $$
DECLARE
	project_schema text;
	project_table text;
	project_sequence text;
	project_function text;
	project_procedure text;
	project_aggregate text;
	project_relation text;
	project_type text;
BEGIN
	FOREACH project_schema IN ARRAY ARRAY['core', 'c1_tai', 'study'] LOOP
		IF EXISTS (SELECT 1 FROM information_schema.schemata WHERE schema_name = project_schema) THEN
			EXECUTE format('ALTER SCHEMA %I OWNER TO oposicionapp_migrator', project_schema);
			EXECUTE format('REVOKE ALL ON SCHEMA %I FROM PUBLIC', project_schema);
			EXECUTE format('REVOKE ALL ON SCHEMA %I FROM oposicionapp_app', project_schema);
			EXECUTE format('REVOKE ALL ON SCHEMA %I FROM oposicionapp_migrator', project_schema);
			EXECUTE format('GRANT USAGE ON SCHEMA %I TO oposicionapp_app', project_schema);
			EXECUTE format('GRANT USAGE, CREATE ON SCHEMA %I TO oposicionapp_migrator', project_schema);
			EXECUTE format('REVOKE ALL PRIVILEGES ON ALL TABLES IN SCHEMA %I FROM oposicionapp_app', project_schema);
			EXECUTE format('REVOKE ALL PRIVILEGES ON ALL TABLES IN SCHEMA %I FROM oposicionapp_migrator', project_schema);
			EXECUTE format('GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA %I TO oposicionapp_app', project_schema);
			EXECUTE format('GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA %I TO oposicionapp_migrator', project_schema);
			EXECUTE format('REVOKE ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA %I FROM oposicionapp_app', project_schema);
			EXECUTE format('REVOKE ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA %I FROM oposicionapp_migrator', project_schema);
			EXECUTE format('GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA %I TO oposicionapp_app', project_schema);
			EXECUTE format('GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA %I TO oposicionapp_migrator', project_schema);
			EXECUTE format('ALTER DEFAULT PRIVILEGES FOR ROLE oposicionapp_migrator IN SCHEMA %I GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO oposicionapp_app', project_schema);
			EXECUTE format('ALTER DEFAULT PRIVILEGES FOR ROLE oposicionapp_migrator IN SCHEMA %I GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO oposicionapp_app', project_schema);

			FOR project_table IN
				SELECT format('%I.%I', schemaname, tablename)
				FROM pg_tables
				WHERE schemaname = project_schema
			LOOP
				EXECUTE format('ALTER TABLE %s OWNER TO oposicionapp_migrator', project_table);
			END LOOP;

			FOR project_sequence IN
				SELECT format('%I.%I', sequence_schema, sequence_name)
				FROM information_schema.sequences
				WHERE sequence_schema = project_schema
			LOOP
				EXECUTE format('ALTER SEQUENCE %s OWNER TO oposicionapp_migrator', project_sequence);
			END LOOP;

			FOR project_function IN
				SELECT oid::regprocedure::text
				FROM pg_proc
				WHERE pronamespace = project_schema::regnamespace
					AND prokind IN ('f', 'w')
			LOOP
				EXECUTE format('ALTER FUNCTION %s OWNER TO oposicionapp_migrator', project_function);
			END LOOP;

			FOR project_procedure IN
				SELECT oid::regprocedure::text
				FROM pg_proc
				WHERE pronamespace = project_schema::regnamespace
					AND prokind = 'p'
			LOOP
				EXECUTE format('ALTER PROCEDURE %s OWNER TO oposicionapp_migrator', project_procedure);
			END LOOP;

			FOR project_aggregate IN
				SELECT oid::regprocedure::text
				FROM pg_proc
				WHERE pronamespace = project_schema::regnamespace
					AND prokind = 'a'
			LOOP
				EXECUTE format('ALTER AGGREGATE %s OWNER TO oposicionapp_migrator', project_aggregate);
			END LOOP;

			FOR project_relation IN
				SELECT format('%I.%I', n.nspname, c.relname)
				FROM pg_class c
				JOIN pg_namespace n ON n.oid = c.relnamespace
				WHERE n.nspname = project_schema
					AND c.relkind = 'v'
			LOOP
				EXECUTE format('ALTER VIEW %s OWNER TO oposicionapp_migrator', project_relation);
			END LOOP;

			FOR project_relation IN
				SELECT format('%I.%I', n.nspname, c.relname)
				FROM pg_class c
				JOIN pg_namespace n ON n.oid = c.relnamespace
				WHERE n.nspname = project_schema
					AND c.relkind = 'm'
			LOOP
				EXECUTE format('ALTER MATERIALIZED VIEW %s OWNER TO oposicionapp_migrator', project_relation);
			END LOOP;

			FOR project_type IN
				SELECT format('%I.%I', n.nspname, t.typname)
				FROM pg_type t
				JOIN pg_namespace n ON n.oid = t.typnamespace
				WHERE n.nspname = project_schema
					AND (
						t.typtype IN ('d', 'e', 'r')
						OR (
							t.typtype = 'c'
							AND EXISTS (
								SELECT 1
								FROM pg_class c
								WHERE c.oid = t.typrelid
									AND c.relkind = 'c'
							)
						)
					)
			LOOP
				EXECUTE format('ALTER TYPE %s OWNER TO oposicionapp_migrator', project_type);
			END LOOP;
		END IF;
	END LOOP;
END
$$;

DO $$
BEGIN
	IF to_regclass('public.flyway_schema_history') IS NOT NULL THEN
		ALTER TABLE public.flyway_schema_history OWNER TO oposicionapp_migrator;
		REVOKE ALL ON TABLE public.flyway_schema_history FROM PUBLIC;
		REVOKE ALL ON TABLE public.flyway_schema_history FROM oposicionapp_app;
		REVOKE ALL ON TABLE public.flyway_schema_history FROM oposicionapp_migrator;
		GRANT ALL PRIVILEGES ON TABLE public.flyway_schema_history TO oposicionapp_migrator;
		REVOKE CREATE ON SCHEMA public FROM oposicionapp_migrator;
	END IF;
END
$$;

COMMIT;
