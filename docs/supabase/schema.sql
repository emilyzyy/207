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

-- Profiles (username + avatar) and friendships.

create table if not exists public.profiles (
  id uuid primary key references auth.users (id) on delete cascade,
  username text not null,
  email text not null default '',
  avatar_color text not null default '#FFFFFF',
  avatar_image text,
  updated_at timestamptz not null default now(),
  constraint profiles_username_format check (
    char_length(username) between 3 and 24
    and username ~ '^[a-zA-Z0-9_]+$'
  )
);

create unique index if not exists profiles_username_lower_idx
  on public.profiles (lower(username));

create table if not exists public.friendships (
  id uuid primary key default gen_random_uuid(),
  requester_id uuid not null references public.profiles (id) on delete cascade,
  addressee_id uuid not null references public.profiles (id) on delete cascade,
  status text not null check (status in ('pending', 'accepted')),
  created_at timestamptz not null default now(),
  constraint friendships_not_self check (requester_id <> addressee_id),
  constraint friendships_pair_unique unique (requester_id, addressee_id)
);

create index if not exists friendships_requester_idx on public.friendships (requester_id);
create index if not exists friendships_addressee_idx on public.friendships (addressee_id);

alter table public.profiles enable row level security;
alter table public.friendships enable row level security;

drop policy if exists profiles_select_authenticated on public.profiles;
drop policy if exists profiles_insert_own on public.profiles;
drop policy if exists profiles_update_own on public.profiles;

create policy profiles_select_authenticated on public.profiles
  for select to authenticated using (true);
create policy profiles_insert_own on public.profiles
  for insert to authenticated with check (auth.uid() = id);
create policy profiles_update_own on public.profiles
  for update to authenticated using (auth.uid() = id) with check (auth.uid() = id);

drop policy if exists friendships_select_own on public.friendships;
drop policy if exists friendships_insert_own on public.friendships;
drop policy if exists friendships_update_addressee on public.friendships;
drop policy if exists friendships_delete_own on public.friendships;

create policy friendships_select_own on public.friendships
  for select to authenticated using (
    auth.uid() = requester_id or auth.uid() = addressee_id
  );
create policy friendships_insert_own on public.friendships
  for insert to authenticated with check (
    auth.uid() = requester_id and status = 'pending'
  );
create policy friendships_update_addressee on public.friendships
  for update to authenticated using (auth.uid() = addressee_id)
  with check (auth.uid() = addressee_id);
create policy friendships_delete_own on public.friendships
  for delete to authenticated using (
    auth.uid() = requester_id or auth.uid() = addressee_id
  );

grant select, insert, update on table public.profiles to authenticated;
grant select, insert, update, delete on table public.friendships to authenticated;

-- Reload PostgREST so /rest/v1/profiles is visible immediately.
notify pgrst, 'reload schema';
