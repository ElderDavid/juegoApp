-- Fase 5: perfiles públicos y estadísticas.
-- Los clientes solo pueden LEER. Toda escritura pasa por funciones del servidor que validan
-- (update_profile aquí; las estadísticas las escribirán las funciones de partida online).

create table public.profiles (
  id uuid primary key references auth.users (id) on delete cascade,
  display_name text not null check (char_length(display_name) between 3 and 16),
  avatar_id smallint not null default 0 check (avatar_id between 0 and 11),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table public.player_stats (
  user_id uuid primary key references public.profiles (id) on delete cascade,
  games_played integer not null default 0 check (games_played >= 0),
  wins integer not null default 0 check (wins >= 0),
  losses integer not null default 0 check (losses >= 0),
  correct_answers integer not null default 0 check (correct_answers >= 0),
  objective_answered integer not null default 0 check (objective_answered >= correct_answers),
  best_score integer not null default 0 check (best_score >= 0),
  xp integer not null default 0 check (xp >= 0),
  updated_at timestamptz not null default now()
);

alter table public.profiles enable row level security;
alter table public.player_stats enable row level security;

-- Nombre y avatar son públicos para quien tenga sesión (se ven en las salas online).
create policy "perfiles visibles con sesion" on public.profiles
  for select to authenticated using (true);

-- Las estadísticas detalladas solo las ve su dueño.
create policy "estadisticas propias" on public.player_stats
  for select to authenticated using (user_id = (select auth.uid()));

-- Sin políticas de insert/update/delete: ningún cliente puede escribir directamente.

-- Misma regla que core/profile/ProfileName.isOffensive: minúsculas, sin tildes, sustituciones
-- de números y símbolos; las palabras cortas cuentan solo completas y las raíces largas en cualquier parte.
create function public.is_offensive_name(name text)
returns boolean
language sql
immutable
set search_path = ''
as $$
  with normalized as (
    select regexp_replace(translate(w, 'áàäâéèëêíìïîóòöôúùüûñ0!134@5$7', 'aaaaeeeeiiiioooouuuunoiieaasst'), '[^a-z]', '', 'g') as word
    from regexp_split_to_table(lower(name), '[^[:alnum:]@$!]+') as w
  ),
  joined as (select string_agg(word, '') as all_text from normalized)
  select
    exists (
      select 1 from normalized where word = any (array[
        'puta', 'puto', 'putas', 'putos', 'culo', 'verga', 'polla', 'perra', 'zorra', 'marica', 'idiota',
        'imbecil', 'pendejo', 'pendeja', 'huevon', 'guevon', 'cabron', 'estupido', 'estupida', 'nazi', 'cono'
      ])
    )
    or (select all_text from joined) like any (array[
      '%hijueputa%', '%hijoeputa%', '%hijodeputa%', '%malparid%', '%gonorrea%', '%maricon%', '%carechimba%',
      '%careverga%', '%hpta%', '%pirob%', '%mierda%', '%hitler%', '%sicari%', '%violador%'
    ]);
$$;

-- Perfil y estadísticas iniciales para cada usuario nuevo (también los invitados anónimos).
create function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  insert into public.profiles (id, display_name)
  values (new.id, 'Jugador ' || upper(substr(replace(new.id::text, '-', ''), 1, 5)));
  insert into public.player_stats (user_id) values (new.id);
  return new;
end;
$$;

create trigger on_auth_user_created
  after insert on auth.users
  for each row execute function public.handle_new_user();

-- Única forma de cambiar nombre y avatar. Los errores llevan un código estable que la app traduce.
create function public.update_profile(p_display_name text, p_avatar_id integer)
returns public.profiles
language plpgsql
security definer
set search_path = ''
as $$
declare
  uid uuid := auth.uid();
  clean text := regexp_replace(btrim(coalesce(p_display_name, '')), '\s+', ' ', 'g');
  result public.profiles;
begin
  if uid is null then
    raise exception 'not_authenticated' using errcode = '28000';
  end if;
  if char_length(clean) < 3 then
    raise exception 'name_too_short' using errcode = '22023';
  end if;
  if char_length(clean) > 16 then
    raise exception 'name_too_long' using errcode = '22023';
  end if;
  if clean !~ '^[[:alpha:][:digit:] ._-]+$' then
    raise exception 'name_invalid_characters' using errcode = '22023';
  end if;
  if public.is_offensive_name(clean) then
    raise exception 'name_offensive' using errcode = '22023';
  end if;
  if p_avatar_id is null or p_avatar_id not between 0 and 11 then
    raise exception 'avatar_invalid' using errcode = '22023';
  end if;

  update public.profiles
     set display_name = clean, avatar_id = p_avatar_id, updated_at = now()
   where id = uid
  returning * into result;
  return result;
end;
$$;

revoke all on function public.update_profile(text, integer) from public, anon;
grant execute on function public.update_profile(text, integer) to authenticated;
revoke all on function public.handle_new_user() from public, anon, authenticated;
