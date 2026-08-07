-- CloseAI Supabase schema: per-user itineraries with lean place refs.

create extension if not exists "pgcrypto";

create table if not exists public.trips (
  id uuid primary key,
  user_id uuid not null references auth.users (id) on delete cascade,
  destination text not null,
  trip_date date not null,
  start_time time not null,
  end_time time not null,
  transportation_mode text not null check (transportation_mode in ('WALKING', 'DRIVING', 'TRANSIT')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create index if not exists trips_user_id_idx on public.trips (user_id);

create table if not exists public.trip_bookmarks (
  trip_id uuid not null references public.trips (id) on delete cascade,
  place_id text not null,
  name text not null,
  latitude double precision not null,
  longitude double precision not null,
  primary key (trip_id, place_id)
);

create table if not exists public.scheduled_events (
  id text primary key,
  trip_id uuid not null references public.trips (id) on delete cascade,
  event_type text not null check (event_type in ('ACTIVITY', 'TRAVEL')),
  start_time time not null,
  end_time time not null,
  notes text not null default '',
  sort_order int not null default 0,
  place_id text,
  name text,
  latitude double precision,
  longitude double precision
);

create index if not exists scheduled_events_trip_id_idx on public.scheduled_events (trip_id);

alter table public.trips enable row level security;
alter table public.trip_bookmarks enable row level security;
alter table public.scheduled_events enable row level security;

drop policy if exists trips_select_own on public.trips;
drop policy if exists trips_insert_own on public.trips;
drop policy if exists trips_update_own on public.trips;
drop policy if exists trips_delete_own on public.trips;

create policy trips_select_own on public.trips
  for select using (auth.uid() = user_id);
create policy trips_insert_own on public.trips
  for insert with check (auth.uid() = user_id);
create policy trips_update_own on public.trips
  for update using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy trips_delete_own on public.trips
  for delete using (auth.uid() = user_id);

drop policy if exists bookmarks_select_own on public.trip_bookmarks;
drop policy if exists bookmarks_insert_own on public.trip_bookmarks;
drop policy if exists bookmarks_update_own on public.trip_bookmarks;
drop policy if exists bookmarks_delete_own on public.trip_bookmarks;

create policy bookmarks_select_own on public.trip_bookmarks
  for select using (
    exists (select 1 from public.trips t where t.id = trip_id and t.user_id = auth.uid())
  );
create policy bookmarks_insert_own on public.trip_bookmarks
  for insert with check (
    exists (select 1 from public.trips t where t.id = trip_id and t.user_id = auth.uid())
  );
create policy bookmarks_update_own on public.trip_bookmarks
  for update using (
    exists (select 1 from public.trips t where t.id = trip_id and t.user_id = auth.uid())
  );
create policy bookmarks_delete_own on public.trip_bookmarks
  for delete using (
    exists (select 1 from public.trips t where t.id = trip_id and t.user_id = auth.uid())
  );

drop policy if exists events_select_own on public.scheduled_events;
drop policy if exists events_insert_own on public.scheduled_events;
drop policy if exists events_update_own on public.scheduled_events;
drop policy if exists events_delete_own on public.scheduled_events;

create policy events_select_own on public.scheduled_events
  for select using (
    exists (select 1 from public.trips t where t.id = trip_id and t.user_id = auth.uid())
  );
create policy events_insert_own on public.scheduled_events
  for insert with check (
    exists (select 1 from public.trips t where t.id = trip_id and t.user_id = auth.uid())
  );
create policy events_update_own on public.scheduled_events
  for update using (
    exists (select 1 from public.trips t where t.id = trip_id and t.user_id = auth.uid())
  );
create policy events_delete_own on public.scheduled_events
  for delete using (
    exists (select 1 from public.trips t where t.id = trip_id and t.user_id = auth.uid())
  );
