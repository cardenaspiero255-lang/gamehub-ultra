-- CAR webhook durable ledger. Apply only after human review, not as a side effect of PR.
-- No security-definer function, no grants to anon/authenticated.
create table if not exists public.car_autopilot_dispatch_state (
  repository_id bigint primary key,
  last_assigned_issue integer not null check (last_assigned_issue > 0),
  preceding_completed_issue integer not null check (preceding_completed_issue > 0),
  delivery_id uuid not null unique,
  updated_at timestamptz not null default now()
);
alter table public.car_autopilot_dispatch_state enable row level security;
revoke all on table public.car_autopilot_dispatch_state from public, anon, authenticated;
grant select, insert, update on table public.car_autopilot_dispatch_state to service_role;

create or replace function public.car_autopilot_reserve(
  p_repository_id bigint,
  p_completed_issue integer,
  p_next_issue integer,
  p_delivery_id uuid
) returns boolean
language plpgsql
security invoker
set search_path = pg_catalog, public
as $$
declare
  previous_issue integer;
begin
  if p_repository_id <> 1375408706 or
     p_completed_issue is null or p_completed_issue <= 0 or
     p_next_issue is null or p_next_issue <= 0 or
     p_completed_issue = p_next_issue or
     p_delivery_id is null then
    return false;
  end if;
  perform pg_advisory_xact_lock(p_repository_id);
  select last_assigned_issue
    into previous_issue
    from public.car_autopilot_dispatch_state
    where repository_id = p_repository_id
    for update;
  if not found then
    insert into public.car_autopilot_dispatch_state
       (repository_id, last_assigned_issue, preceding_completed_issue, delivery_id)
    values (p_repository_id, p_next_issue, p_completed_issue, p_delivery_id);
    return true;
  end if;
  -- Exactly one successor may follow the same completed CAR, even for
  -- distinct webhook delivery IDs. A stale/replayed close fails closed.
  if previous_issue <> p_completed_issue then
    return false;
  end if;
  update public.car_autopilot_dispatch_state
    set last_assigned_issue = p_next_issue,
        preceding_completed_issue = p_completed_issue,
        delivery_id = p_delivery_id,
        updated_at = now()
    where repository_id = p_repository_id;
  return true;
exception
  when unique_violation then
    return false;
end;
$$;
revoke all on function public.car_autopilot_reserve(bigint,integer,integer,uuid)
  from public, anon, authenticated;
grant execute on function public.car_autopilot_reserve(bigint,integer,integer,uuid)
  to service_role;

comment on table public.car_autopilot_dispatch_state is
  'Durable fail-closed CAR handoff ledger; service_role only. Never expose anon/authenticated writes.';
