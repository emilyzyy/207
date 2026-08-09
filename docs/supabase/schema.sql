-- CloseAI Supabase schema: per-user itineraries with lean place refs and per-day schedules.

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

create table if not exists public.trip_days (
  trip_id uuid not null references public.trips (id) on delete cascade,
  day_index int not null,
  trip_date date not null,
  start_time time not null,
  end_time time not null,
  primary key (trip_id, day_index)
);

create index if not exists trip_days_trip_id_idx on public.trip_days (trip_id);

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
  day_index int not null default 0,
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
alter table public.trip_days enable row level security;
alter table public.trip_bookmarks enable row level security;
alter table public.scheduled_events enable row level security;

-- Shared itinerary members (friends who can view/edit/admin the trip).
create table if not exists public.trip_members (
  trip_id uuid not null references public.trips (id) on delete cascade,
  user_id uuid not null references auth.users (id) on delete cascade,
  role text not null default 'edit' check (role in ('view', 'edit', 'admin')),
  created_at timestamptz not null default now(),
  primary key (trip_id, user_id)
);

alter table public.trip_members add column if not exists role text;
update public.trip_members set role = 'edit' where role is null or role = '';
alter table public.trip_members alter column role set default 'edit';
alter table public.trip_members alter column role set not null;
do $$
begin
  if not exists (
    select 1 from pg_constraint where conname = 'trip_members_role_check'
  ) then
    alter table public.trip_members
      add constraint trip_members_role_check check (role in ('view', 'edit', 'admin'));
  end if;
end $$;

create index if not exists trip_members_user_id_idx on public.trip_members (user_id);

alter table public.trip_members enable row level security;

create or replace function public.can_access_trip(p_trip_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1 from public.trips t
    where t.id = p_trip_id and t.user_id = auth.uid()
  ) or exists (
    select 1 from public.trip_members m
    where m.trip_id = p_trip_id and m.user_id = auth.uid()
  );
$$;

create or replace function public.can_edit_trip(p_trip_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1 from public.trips t
    where t.id = p_trip_id and t.user_id = auth.uid()
  ) or exists (
    select 1 from public.trip_members m
    where m.trip_id = p_trip_id
      and m.user_id = auth.uid()
      and m.role in ('edit', 'admin')
  );
$$;

create or replace function public.can_manage_trip_members(p_trip_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1 from public.trips t
    where t.id = p_trip_id and t.user_id = auth.uid()
  ) or exists (
    select 1 from public.trip_members m
    where m.trip_id = p_trip_id
      and m.user_id = auth.uid()
      and m.role = 'admin'
  );
$$;

revoke all on function public.can_access_trip(uuid) from public;
revoke all on function public.can_edit_trip(uuid) from public;
revoke all on function public.can_manage_trip_members(uuid) from public;
grant execute on function public.can_access_trip(uuid) to authenticated;
grant execute on function public.can_edit_trip(uuid) to authenticated;
grant execute on function public.can_manage_trip_members(uuid) to authenticated;

drop policy if exists trips_select_own on public.trips;
drop policy if exists trips_insert_own on public.trips;
drop policy if exists trips_update_own on public.trips;
drop policy if exists trips_delete_own on public.trips;
drop policy if exists trips_select_member on public.trips;
drop policy if exists trips_update_member on public.trips;

create policy trips_select_member on public.trips
  for select to authenticated using (public.can_access_trip(id));
create policy trips_insert_own on public.trips
  for insert to authenticated with check (auth.uid() = user_id);
create policy trips_update_member on public.trips
  for update to authenticated
  using (public.can_edit_trip(id))
  with check (public.can_edit_trip(id));
create policy trips_delete_own on public.trips
  for delete to authenticated using (auth.uid() = user_id);

grant select, insert, update, delete on table public.trips to authenticated;
grant select, insert, update, delete on table public.trip_days to authenticated;
grant select, insert, update, delete on table public.trip_bookmarks to authenticated;
grant select, insert, update, delete on table public.scheduled_events to authenticated;

drop policy if exists days_select_own on public.trip_days;
drop policy if exists days_insert_own on public.trip_days;
drop policy if exists days_update_own on public.trip_days;
drop policy if exists days_delete_own on public.trip_days;
drop policy if exists days_select_member on public.trip_days;
drop policy if exists days_insert_member on public.trip_days;
drop policy if exists days_update_member on public.trip_days;
drop policy if exists days_delete_member on public.trip_days;

create policy days_select_member on public.trip_days
  for select to authenticated using (public.can_access_trip(trip_id));
create policy days_insert_member on public.trip_days
  for insert to authenticated with check (public.can_edit_trip(trip_id));
create policy days_update_member on public.trip_days
  for update to authenticated
  using (public.can_edit_trip(trip_id))
  with check (public.can_edit_trip(trip_id));
create policy days_delete_member on public.trip_days
  for delete to authenticated using (public.can_edit_trip(trip_id));

drop policy if exists bookmarks_select_own on public.trip_bookmarks;
drop policy if exists bookmarks_insert_own on public.trip_bookmarks;
drop policy if exists bookmarks_update_own on public.trip_bookmarks;
drop policy if exists bookmarks_delete_own on public.trip_bookmarks;
drop policy if exists bookmarks_select_member on public.trip_bookmarks;
drop policy if exists bookmarks_insert_member on public.trip_bookmarks;
drop policy if exists bookmarks_update_member on public.trip_bookmarks;
drop policy if exists bookmarks_delete_member on public.trip_bookmarks;

create policy bookmarks_select_member on public.trip_bookmarks
  for select to authenticated using (public.can_access_trip(trip_id));
create policy bookmarks_insert_member on public.trip_bookmarks
  for insert to authenticated with check (public.can_edit_trip(trip_id));
create policy bookmarks_update_member on public.trip_bookmarks
  for update to authenticated
  using (public.can_edit_trip(trip_id))
  with check (public.can_edit_trip(trip_id));
create policy bookmarks_delete_member on public.trip_bookmarks
  for delete to authenticated using (public.can_edit_trip(trip_id));

drop policy if exists events_select_own on public.scheduled_events;
drop policy if exists events_insert_own on public.scheduled_events;
drop policy if exists events_update_own on public.scheduled_events;
drop policy if exists events_delete_own on public.scheduled_events;
drop policy if exists events_select_member on public.scheduled_events;
drop policy if exists events_insert_member on public.scheduled_events;
drop policy if exists events_update_member on public.scheduled_events;
drop policy if exists events_delete_member on public.scheduled_events;

create policy events_select_member on public.scheduled_events
  for select to authenticated using (public.can_access_trip(trip_id));
create policy events_insert_member on public.scheduled_events
  for insert to authenticated with check (public.can_edit_trip(trip_id));
create policy events_update_member on public.scheduled_events
  for update to authenticated
  using (public.can_edit_trip(trip_id))
  with check (public.can_edit_trip(trip_id));
create policy events_delete_member on public.scheduled_events
  for delete to authenticated using (public.can_edit_trip(trip_id));

drop policy if exists trip_members_select on public.trip_members;
drop policy if exists trip_members_insert on public.trip_members;
drop policy if exists trip_members_update on public.trip_members;
drop policy if exists trip_members_delete on public.trip_members;

create policy trip_members_select on public.trip_members
  for select to authenticated using (public.can_access_trip(trip_id));
create policy trip_members_insert on public.trip_members
  for insert to authenticated with check (public.can_manage_trip_members(trip_id));
create policy trip_members_update on public.trip_members
  for update to authenticated
  using (public.can_manage_trip_members(trip_id))
  with check (public.can_manage_trip_members(trip_id));
create policy trip_members_delete on public.trip_members
  for delete to authenticated using (public.can_manage_trip_members(trip_id));

grant select, insert, update, delete on table public.trip_members to authenticated;

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

-- Reload PostgREST so new tables/policies are visible immediately.
notify pgrst, 'reload schema';
