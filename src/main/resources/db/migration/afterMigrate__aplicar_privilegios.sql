DO $$
DECLARE
	project_schema text;
BEGIN
	IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'oposicionapp_app')
			OR NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'oposicionapp_migrator') THEN
		RETURN;
	END IF;

	FOREACH project_schema IN ARRAY ARRAY['core', 'c1_tai', 'study'] LOOP
		IF EXISTS (SELECT 1 FROM information_schema.schemata WHERE schema_name = project_schema) THEN
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
		END IF;
	END LOOP;
END
$$;

DO $$
BEGIN
	IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'oposicionapp_migrator')
			AND to_regclass('public.flyway_schema_history') IS NOT NULL THEN
		REVOKE ALL ON TABLE public.flyway_schema_history FROM PUBLIC;
		REVOKE ALL ON TABLE public.flyway_schema_history FROM oposicionapp_app;
		REVOKE ALL ON TABLE public.flyway_schema_history FROM oposicionapp_migrator;
		GRANT ALL PRIVILEGES ON TABLE public.flyway_schema_history TO oposicionapp_migrator;
	END IF;
END
$$;
