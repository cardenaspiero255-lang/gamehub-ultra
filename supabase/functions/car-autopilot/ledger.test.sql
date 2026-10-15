-- Offline database-contract test for the CAR handoff migration.
-- CI runs this in an isolated PostgreSQL container, never in production.
\set ON_ERROR_STOP on
BEGIN;

DO $audit$
DECLARE
  fn_is_definer boolean;
  rls_enabled boolean;
BEGIN
  SELECT c.relrowsecurity INTO rls_enabled
    FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = 'public' AND c.relname = 'car_autopilot_dispatch_state';
  IF rls_enabled IS DISTINCT FROM true THEN
    RAISE EXCEPTION 'P1: CAR ledger RLS is not enabled';
  END IF;
  SELECT p.prosecdef INTO fn_is_definer
    FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace
    WHERE n.nspname='public' AND p.proname='car_autopilot_reserve';
  IF fn_is_definer IS DISTINCT FROM false THEN
    RAISE EXCEPTION 'P1: CAR RPC must remain SECURITY INVOKER';
  END IF;
  IF has_table_privilege('anon','public.car_autopilot_dispatch_state','SELECT')
     OR has_table_privilege('authenticated','public.car_autopilot_dispatch_state','UPDATE')
     OR has_table_privilege('service_role','public.car_autopilot_dispatch_state','DELETE')
     OR has_function_privilege('anon','public.car_autopilot_reserve(bigint,integer,integer,uuid)','EXECUTE')
     OR has_function_privilege('authenticated','public.car_autopilot_reserve(bigint,integer,integer,uuid)','EXECUTE') THEN
    RAISE EXCEPTION 'P1: CAR ledger/RPC has excessive privileges';
  END IF;
  IF NOT has_function_privilege('service_role','public.car_autopilot_reserve(bigint,integer,integer,uuid)','EXECUTE')
     OR NOT has_table_privilege('service_role','public.car_autopilot_dispatch_state','INSERT')
     OR NOT has_table_privilege('service_role','public.car_autopilot_dispatch_state','UPDATE') THEN
    RAISE EXCEPTION 'P1: service_role cannot use the CAR ledger';
  END IF;
END;
$audit$;

SET ROLE service_role;
DO $dispatch$
BEGIN
  IF NOT public.car_autopilot_reserve(1375408706, 254, 256, '00000000-0000-4000-8000-000000000001') THEN
    RAISE EXCEPTION 'P1: initial atomic reservation failed';
  END IF;
  IF public.car_autopilot_reserve(1375408706, 254, 257, '00000000-0000-4000-8000-000000000002') THEN
    RAISE EXCEPTION 'P1: replayed close advanced the CAR twice';
  END IF;
  IF public.car_autopilot_reserve(1375408706, 254, 256, '00000000-0000-4000-8000-000000000001') THEN
    RAISE EXCEPTION 'P1: replayed delivery accepted';
  END IF;
  IF public.car_autopilot_reserve(9000, 256, 257, '00000000-0000-4000-8000-000000000003') THEN
    RAISE EXCEPTION 'P1: unexpected repository ID accepted';
  END IF;
  IF public.car_autopilot_reserve(1375408706, 256, 256, '00000000-0000-4000-8000-000000000004') THEN
    RAISE EXCEPTION 'P1: self-transition accepted';
  END IF;
  IF NOT public.car_autopilot_reserve(1375408706, 256, 257, '00000000-0000-4000-8000-000000000005') THEN
    RAISE EXCEPTION 'P1: legitimate successor reservation failed';
  END IF;
  IF (SELECT last_assigned_issue FROM public.car_autopilot_dispatch_state
       WHERE repository_id = 1375408706) <> 257 THEN
    RAISE EXCEPTION 'P1: ledger state was not updated correctly';
  END IF;
END;
$dispatch$;
RESET ROLE;
ROLLBACK;
