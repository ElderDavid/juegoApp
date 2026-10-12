-- Fase 6: salas online con el servidor como autoridad.
--
-- Principios:
-- * Los clientes no pueden leer ni escribir ninguna tabla de este archivo. Todo pasa por funciones
--   security definer que validan quién llama, en qué fase está la sala y si el tiempo sigue abierto.
-- * El servidor elige las preguntas, mide los tiempos, calcula los puntos y aplica las estadísticas.
--   Las reglas son las mismas que core/game (GameRules, Scoring, GamePlanner, GameEngine).
-- * La respuesta correcta vive en question_keys y solo sale del servidor en la fase de revelación.
-- * No hay procesos en segundo plano: cada llamada avanza la sala si ya venció su fase
--   («avance perezoso»). Con jugadores conectados, la app consulta el estado cada segundo.

-- ---------------------------------------------------------------- Preguntas

create table public.questions (
  id text primary key,
  region_id text,
  category_id text not null,
  kind text not null check (kind in ('OBJECTIVE', 'DEBATE', 'OPINION')),
  format text not null check (format in ('MULTIPLE_CHOICE', 'TRUE_FALSE')),
  difficulty text not null check (difficulty in ('EASY', 'MEDIUM', 'HARD')),
  is_vote boolean not null,
  text text not null,
  options jsonb not null check (jsonb_typeof(options) = 'array' and jsonb_array_length(options) between 2 and 4),
  explanation text not null,
  source text
);

create index questions_pool_idx on public.questions (region_id);

-- Solución de cada pregunta. null en las votaciones. Nunca se expone durante la pregunta.
create table public.question_keys (
  question_id text primary key references public.questions (id) on delete cascade,
  correct_index smallint,
  game_rule text
);

-- ---------------------------------------------------------------- Salas

create table public.rooms (
  id uuid primary key default gen_random_uuid(),
  code text not null check (code ~ '^[A-HJ-NP-Z2-9]{6}$'),
  visibility text not null check (visibility in ('private', 'public')),
  mode text not null,
  host_id uuid not null references public.profiles (id) on delete cascade,
  created_by uuid not null references public.profiles (id) on delete cascade,
  status text not null default 'waiting' check (status in ('waiting', 'ready', 'playing', 'finished', 'cancelled')),
  phase text check (phase in ('roulette', 'betting', 'question', 'reveal', 'summary', 'tiebreak_question', 'tiebreak_reveal')),
  question_index smallint not null default 0,
  tiebreak_index smallint not null default 0,
  phase_started_at timestamptz,
  phase_ends_at timestamptz,
  autostart_at timestamptz,
  players_at_start smallint,
  winners uuid[] not null default '{}',
  cancel_reason text,
  stats_applied boolean not null default false,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  finished_at timestamptz
);

-- El código solo tiene que ser único entre las salas activas.
create unique index rooms_active_code_idx on public.rooms (code) where status in ('waiting', 'ready', 'playing');
create index rooms_public_lobby_idx on public.rooms (mode, created_at) where visibility = 'public' and status in ('waiting', 'ready');
create index rooms_created_by_idx on public.rooms (created_by, created_at);

create table public.room_players (
  room_id uuid not null references public.rooms (id) on delete cascade,
  user_id uuid not null references public.profiles (id) on delete cascade,
  seat smallint not null check (seat between 0 and 3),
  -- Copia del perfil al entrar: el marcador no cambia si alguien se renombra durante la partida.
  display_name text not null,
  avatar_id smallint not null,
  score integer not null default 0 check (score >= 0),
  joined_at timestamptz not null default now(),
  last_seen_at timestamptz not null default now(),
  -- Abandonó una partida en curso: sus preguntas cuentan como no respondidas y no puede ganar.
  left_at timestamptz,
  in_tiebreak boolean not null default false,
  primary key (room_id, user_id),
  unique (room_id, seat)
);

create index room_players_user_idx on public.room_players (user_id);

create table public.room_questions (
  room_id uuid not null references public.rooms (id) on delete cascade,
  is_tiebreak boolean not null,
  idx smallint not null,
  round smallint not null check (round between 0 and 4),
  question_id text not null references public.questions (id),
  category_id text not null,
  primary key (room_id, is_tiebreak, idx)
);

create table public.room_answers (
  room_id uuid not null references public.rooms (id) on delete cascade,
  is_tiebreak boolean not null,
  idx smallint not null,
  user_id uuid not null references public.profiles (id) on delete cascade,
  -- Ronda de estrategia: 50 o 100; 0 = apuesta libre; null fuera de esa ronda.
  bet integer check (bet in (0, 50, 100)),
  choice smallint,
  elapsed_ms integer check (elapsed_ms between 0 and 8000),
  answered_at timestamptz,
  won boolean,
  points integer not null default 0,
  primary key (room_id, is_tiebreak, idx, user_id)
);

create table public.room_results (
  room_id uuid not null references public.rooms (id) on delete cascade,
  is_tiebreak boolean not null,
  idx smallint not null,
  winning_options smallint[] not null,
  vote_counts jsonb not null default '{}',
  scored boolean not null,
  tiebreak_winner uuid,
  primary key (room_id, is_tiebreak, idx)
);

-- Registro para limitar la frecuencia de acciones caras (crear salas, buscar, probar códigos).
create table public.rate_events (
  user_id uuid not null,
  kind text not null,
  at timestamptz not null default now()
);

create index rate_events_idx on public.rate_events (user_id, kind, at);

-- Ningún cliente accede directamente a estas tablas.
alter table public.questions enable row level security;
alter table public.question_keys enable row level security;
alter table public.rooms enable row level security;
alter table public.room_players enable row level security;
alter table public.room_questions enable row level security;
alter table public.room_answers enable row level security;
alter table public.room_results enable row level security;
alter table public.rate_events enable row level security;

revoke all on public.questions, public.question_keys, public.rooms, public.room_players,
  public.room_questions, public.room_answers, public.room_results, public.rate_events
  from anon, authenticated;

-- ---------------------------------------------------------------- Constantes y utilidades

-- Tiempos de cada fase en milisegundos. La pregunta dura lo mismo que en core (GameRules).
create function public._phase_ms(p_phase text)
returns integer
language sql
immutable
set search_path = ''
as $$
  select case p_phase
    when 'roulette' then 3500
    when 'betting' then 7000
    when 'question' then 8000
    when 'tiebreak_question' then 8000
    when 'reveal' then 6000
    when 'tiebreak_reveal' then 5000
    when 'summary' then 6000
  end;
$$;

-- Margen para la latencia de red: una respuesta enviada a tiempo puede llegar algo después.
create function public._answer_grace_ms()
returns integer language sql immutable set search_path = '' as $$ select 1500 $$;

create function public._ms(p interval)
returns integer language sql immutable set search_path = '' as $$
  select (extract(epoch from p) * 1000)::integer
$$;

create function public._require_user()
returns uuid
language plpgsql
stable
set search_path = ''
as $$
declare
  uid uuid := auth.uid();
begin
  if uid is null then
    raise exception 'not_authenticated' using errcode = '28000';
  end if;
  return uid;
end;
$$;

create function public._rate_limit(p_user uuid, p_kind text, p_max integer, p_window interval)
returns void
language plpgsql
set search_path = ''
as $$
begin
  delete from public.rate_events where at < now() - interval '1 day';
  if (select count(*) from public.rate_events
       where user_id = p_user and kind = p_kind and at > now() - p_window) >= p_max then
    raise exception 'rate_limited' using errcode = '53400';
  end if;
  insert into public.rate_events (user_id, kind) values (p_user, p_kind);
end;
$$;

-- Código de 6 caracteres sin letras ni números que se confundan (sin I, O, 0, 1).
create function public._new_room_code()
returns text
language plpgsql
volatile
set search_path = ''
as $$
declare
  alphabet constant text := 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
  bytes bytea;
  candidate text;
begin
  loop
    -- gen_random_uuid usa el generador criptográfico de PostgreSQL.
    bytes := decode(replace(gen_random_uuid()::text, '-', ''), 'hex');
    candidate := '';
    for i in 0..5 loop
      candidate := candidate || substr(alphabet, (get_byte(bytes, i) % 32) + 1, 1);
    end loop;
    exit when not exists (
      select 1 from public.rooms where code = candidate and status in ('waiting', 'ready', 'playing')
    );
  end loop;
  return candidate;
end;
$$;

create function public._valid_mode(p_mode text)
returns boolean
language sql
stable
set search_path = ''
as $$
  select p_mode = 'general' or exists (select 1 from public.questions where region_id = p_mode)
$$;

-- Jugador sigue conectado si consultó el estado en los últimos segundos.
create function public._connected(p_last_seen timestamptz)
returns boolean language sql stable set search_path = '' as $$
  select p_last_seen > now() - interval '10 seconds'
$$;

-- ---------------------------------------------------------------- Planificación (igual que GamePlanner)

-- Filtros por ronda, del más estricto (nivel 1) al más permisivo (nivel 3).
create function public._eligible(q public.questions, p_round integer, p_tier integer)
returns boolean
language sql
immutable
set search_path = ''
as $$
  select
    -- Las votaciones solo en conocimiento y estrategia (no tienen sentido con cronómetro).
    (not q.is_vote or p_round in (1, 3))
    and case p_round
      when 1 then case p_tier
        when 1 then q.format = 'MULTIPLE_CHOICE' and q.difficulty <> 'HARD'
        when 2 then q.format = 'MULTIPLE_CHOICE'
        else false end
      when 2 then (not q.is_vote) and case p_tier
        when 1 then q.format = 'TRUE_FALSE'
        when 2 then q.difficulty = 'EASY'
        else q.difficulty <> 'HARD' end
      when 3 then case p_tier
        when 1 then q.difficulty = 'MEDIUM' or q.is_vote
        when 2 then q.difficulty <> 'HARD'
        else true end
      when 4 then (not q.is_vote) and case p_tier
        when 1 then q.difficulty = 'HARD'
        when 2 then q.difficulty = 'MEDIUM'
        else true end
      else false
    end
$$;

create function public._plan_room(p_room uuid, p_mode text, p_players integer)
returns void
language plpgsql
set search_path = ''
as $$
declare
  votes_allowed integer := case when p_players >= 3 then 1 else 0 end;
  votes_used integer := 0;
  used text[] := '{}';
  recent text[] := '{}';
  round integer;
  categories text[];
  fresh text[];
  chosen_category text;
  chosen public.questions;
begin
  delete from public.room_questions where room_id = p_room;

  for i in 0..11 loop
    round := i / 3 + 1;
    categories := null;
    for tier in 1..3 loop
      select array_agg(distinct q.category_id order by q.category_id) into categories
        from public.questions q
       where (q.region_id is not distinct from nullif(p_mode, 'general'))
         and not (q.id = any (used))
         and (not q.is_vote or votes_used < votes_allowed)
         and public._eligible(q, round, tier);
      exit when categories is not null;
    end loop;
    if categories is null then
      raise exception 'not_enough_questions' using errcode = 'P0001';
    end if;

    -- Ruleta: evita las dos últimas categorías si hay alternativas (CategoryRoulette).
    select array_agg(c) into fresh from unnest(categories) c where not (c = any (recent[greatest(1, cardinality(recent) - 1):]));
    if fresh is null then
      select array_agg(c) into fresh from unnest(categories) c where c is distinct from recent[cardinality(recent)];
    end if;
    if fresh is null then
      fresh := categories;
    end if;
    chosen_category := fresh[1 + floor(random() * cardinality(fresh))::integer];

    -- Se repite el filtro con el nivel que dio resultado, ya dentro de la categoría elegida.
    for tier in 1..3 loop
      select q.* into chosen
        from public.questions q
       where (q.region_id is not distinct from nullif(p_mode, 'general'))
         and q.category_id = chosen_category
         and not (q.id = any (used))
         and (not q.is_vote or votes_used < votes_allowed)
         and public._eligible(q, round, tier)
       order by random()
       limit 1;
      exit when found;
    end loop;

    used := used || chosen.id;
    recent := recent || chosen_category;
    if chosen.is_vote then
      votes_used := votes_used + 1;
    end if;
    insert into public.room_questions (room_id, is_tiebreak, idx, round, question_id, category_id)
    values (p_room, false, i, round, chosen.id, chosen_category);
  end loop;

  -- Reserva de desempate: objetivas no usadas, las más difíciles primero.
  insert into public.room_questions (room_id, is_tiebreak, idx, round, question_id, category_id)
  select p_room, true, (row_number() over (order by ord) - 1)::smallint, 0, id, category_id
    from (
      select q.id, q.category_id,
             row_number() over (order by case q.difficulty when 'HARD' then 0 when 'MEDIUM' then 1 else 2 end, random()) as ord
        from public.questions q
       where (q.region_id is not distinct from nullif(p_mode, 'general'))
         and not q.is_vote
         and not (q.id = any (used))
    ) t
   where ord <= 5;
end;
$$;

-- ---------------------------------------------------------------- Máquina de estados

-- Las preguntas se cierran con el margen de red incluido; el reloj que ve el jugador es de 8 s.
create function public._set_phase(p_room uuid, p_phase text)
returns void
language sql
set search_path = ''
as $$
  update public.rooms
     set phase = p_phase,
         phase_started_at = now(),
         phase_ends_at = now() + make_interval(secs => (public._phase_ms(p_phase)
           + case when p_phase in ('question', 'tiebreak_question') then public._answer_grace_ms() else 0 end) / 1000.0),
         updated_at = now()
   where id = p_room;
$$;

create function public._start_game(p_room uuid)
returns void
language plpgsql
set search_path = ''
as $$
declare
  r public.rooms;
  n integer;
begin
  select * into r from public.rooms where id = p_room;
  select count(*) into n from public.room_players where room_id = p_room and left_at is null;
  if n < 2 then
    raise exception 'not_enough_players' using errcode = 'P0001';
  end if;
  perform public._plan_room(p_room, r.mode, n);
  update public.room_players set score = 0, in_tiebreak = false where room_id = p_room;
  update public.rooms
     set status = 'playing', question_index = 0, tiebreak_index = 0, players_at_start = n,
         autostart_at = null, winners = '{}'
   where id = p_room;
  perform public._set_phase(p_room, 'roulette');
end;
$$;

-- Apuesta mínima posible (Scoring.defaultBet): 50 si alcanza, si no apuesta libre (0).
create function public._default_bet(p_score integer)
returns integer language sql immutable set search_path = '' as $$
  select case when p_score >= 50 then 50 else 0 end
$$;

-- Al empezar la pregunta de estrategia, quien no apostó juega con la apuesta mínima.
create function public._fill_default_bets(p_room uuid, p_idx integer)
returns void
language sql
set search_path = ''
as $$
  insert into public.room_answers (room_id, is_tiebreak, idx, user_id, bet)
  select p.room_id, false, p_idx, p.user_id, public._default_bet(p.score)
    from public.room_players p
   where p.room_id = p_room
  on conflict (room_id, is_tiebreak, idx, user_id) do nothing;
$$;

-- Puntúa la pregunta actual una sola vez (GameEngine.closeQuestion).
create function public._close_question(p_room uuid)
returns void
language plpgsql
set search_path = ''
as $$
declare
  r public.rooms;
  rq public.room_questions;
  q public.questions;
  k public.question_keys;
  winning smallint[];
  counts jsonb := '{}';
  valid_votes integer;
  max_votes integer;
  is_scored boolean;
  a record;
  delta integer;
  did_win boolean;
begin
  select * into r from public.rooms where id = p_room;
  if exists (select 1 from public.room_results where room_id = p_room and not is_tiebreak and idx = r.question_index) then
    return; -- ya puntuada: nunca se suman puntos dos veces
  end if;
  select * into rq from public.room_questions where room_id = p_room and not is_tiebreak and idx = r.question_index;
  select * into q from public.questions where id = rq.question_id;
  select * into k from public.question_keys where question_id = rq.question_id;

  -- Quien no respondió (o abandonó) cuenta como tiempo agotado.
  insert into public.room_answers (room_id, is_tiebreak, idx, user_id, bet)
  select p.room_id, false, r.question_index, p.user_id,
         case when rq.round = 3 then public._default_bet(p.score) end
    from public.room_players p
   where p.room_id = p_room
  on conflict (room_id, is_tiebreak, idx, user_id) do nothing;

  if k.correct_index is not null then
    winning := array[k.correct_index];
    is_scored := true;
  else
    select count(*) into valid_votes from public.room_answers
     where room_id = p_room and not is_tiebreak and idx = r.question_index and choice is not null;
    select coalesce(jsonb_object_agg(choice::text, n), '{}'), coalesce(max(n), 0) into counts, max_votes
      from (select choice, count(*)::integer as n from public.room_answers
             where room_id = p_room and not is_tiebreak and idx = r.question_index and choice is not null
             group by choice) v;
    is_scored := valid_votes >= 2;
    if is_scored then
      select array_agg(choice order by choice) into winning
        from (select choice, count(*) as n from public.room_answers
               where room_id = p_room and not is_tiebreak and idx = r.question_index and choice is not null
               group by choice) v
       where n = max_votes;
    else
      winning := '{}';
    end if;
  end if;

  for a in
    select ra.*, p.score from public.room_answers ra
      join public.room_players p on p.room_id = ra.room_id and p.user_id = ra.user_id
     where ra.room_id = p_room and not ra.is_tiebreak and ra.idx = r.question_index
  loop
    if not is_scored then
      did_win := null;
      delta := 0; -- votación sin votos suficientes: ni premio ni pérdida de apuesta
    else
      did_win := a.choice is not null and a.choice = any (winning);
      if rq.round = 3 then
        delta := case
          when a.bet > 0 then case when did_win then a.bet else -a.bet end
          else case when did_win then 50 else 0 end -- apuesta libre
        end;
      elsif q.is_vote then
        delta := case when did_win then 100 else 0 end;
      elsif not did_win then
        delta := 0;
      else
        delta := case rq.round when 4 then 150 else 100 end
          + case when rq.round in (2, 4)
                 then (50 * (8000 - least(greatest(coalesce(a.elapsed_ms, 8000), 0), 8000))) / 8000
                 else 0 end;
      end if;
    end if;
    update public.room_answers set won = did_win, points = delta
     where room_id = p_room and not is_tiebreak and idx = r.question_index and user_id = a.user_id;
    update public.room_players set score = score + delta
     where room_id = p_room and user_id = a.user_id;
  end loop;

  insert into public.room_results (room_id, is_tiebreak, idx, winning_options, vote_counts, scored)
  values (p_room, false, r.question_index, winning, case when q.is_vote then counts else '{}' end, is_scored);
end;
$$;

-- Desempate: gana la respuesta correcta más rápida; sin ganador único se repite.
create function public._close_tiebreak(p_room uuid)
returns void
language plpgsql
set search_path = ''
as $$
declare
  r public.rooms;
  k public.question_keys;
  winner uuid;
begin
  select * into r from public.rooms where id = p_room;
  if exists (select 1 from public.room_results where room_id = p_room and is_tiebreak and idx = r.tiebreak_index) then
    return;
  end if;
  select qk.* into k from public.room_questions rq join public.question_keys qk on qk.question_id = rq.question_id
   where rq.room_id = p_room and rq.is_tiebreak and rq.idx = r.tiebreak_index;

  insert into public.room_answers (room_id, is_tiebreak, idx, user_id)
  select room_id, true, r.tiebreak_index, user_id from public.room_players
   where room_id = p_room and in_tiebreak
  on conflict (room_id, is_tiebreak, idx, user_id) do nothing;

  update public.room_answers set won = (choice is not null and choice = k.correct_index)
   where room_id = p_room and is_tiebreak and idx = r.tiebreak_index;

  select case when count(*) = 1 then (array_agg(user_id))[1] end into winner
    from public.room_answers
   where room_id = p_room and is_tiebreak and idx = r.tiebreak_index and won
     and elapsed_ms = (select min(elapsed_ms) from public.room_answers
                        where room_id = p_room and is_tiebreak and idx = r.tiebreak_index and won);

  insert into public.room_results (room_id, is_tiebreak, idx, winning_options, scored, tiebreak_winner)
  values (p_room, true, r.tiebreak_index, array[k.correct_index], true, winner);
end;
$$;

-- Estadísticas al terminar, una sola vez por sala (mismas cuentas que core/profile/Levels).
create function public._apply_stats(p_room uuid)
returns void
language plpgsql
set search_path = ''
as $$
declare
  r public.rooms;
  p record;
  correct integer;
  objective integer;
  won boolean;
begin
  update public.rooms set stats_applied = true where id = p_room and not stats_applied returning * into r;
  if not found then
    return;
  end if;
  -- Preguntas objetivas: las que se puntuaron sin recuento de votos.
  select count(*) into objective from public.room_results
   where room_id = p_room and not is_tiebreak and scored and vote_counts = '{}'::jsonb;
  for p in select * from public.room_players where room_id = p_room loop
    select count(*) into correct
      from public.room_answers ra
      join public.room_results rr on rr.room_id = ra.room_id and rr.is_tiebreak = ra.is_tiebreak and rr.idx = ra.idx
     where ra.room_id = p_room and not ra.is_tiebreak and ra.user_id = p.user_id and ra.won
       and rr.scored and rr.vote_counts = '{}'::jsonb;
    won := p.user_id = any (r.winners);
    update public.player_stats
       set games_played = games_played + 1,
           wins = wins + case when won then 1 else 0 end,
           losses = losses + case when won then 0 else 1 end,
           correct_answers = correct_answers + correct,
           objective_answered = objective_answered + objective,
           best_score = greatest(best_score, p.score),
           xp = xp + 20 + correct * 5 + case when won then 30 else 0 end,
           updated_at = now()
     where user_id = p.user_id;
  end loop;
end;
$$;

create function public._finish(p_room uuid, p_winners uuid[])
returns void
language plpgsql
set search_path = ''
as $$
begin
  update public.rooms
     set status = 'finished', phase = null, phase_started_at = null, phase_ends_at = null,
         winners = p_winners, finished_at = now(), updated_at = now()
   where id = p_room;
  perform public._apply_stats(p_room);
end;
$$;

-- Tras la pregunta 12: gana el único líder o se juega el desempate (GameEngine.finishOrTiebreak).
-- Quien abandonó conserva sus puntos en el marcador pero no puede ganar.
create function public._finish_or_tiebreak(p_room uuid)
returns void
language plpgsql
set search_path = ''
as $$
declare
  top integer;
  leaders uuid[];
begin
  select max(score) into top from public.room_players where room_id = p_room and left_at is null;
  select array_agg(user_id order by seat) into leaders
    from public.room_players where room_id = p_room and left_at is null and score = top;
  if leaders is null then
    perform public._finish(p_room, '{}');
  elsif cardinality(leaders) = 1
     or not exists (select 1 from public.room_questions where room_id = p_room and is_tiebreak) then
    perform public._finish(p_room, leaders);
  else
    update public.room_players set in_tiebreak = (user_id = any (leaders)) where room_id = p_room;
    update public.rooms set tiebreak_index = 0 where id = p_room;
    perform public._set_phase(p_room, 'tiebreak_question');
  end if;
end;
$$;

-- Pasa a la fase siguiente cuando vence la actual (o cuando todos ya respondieron).
create function public._next_phase(p_room uuid)
returns void
language plpgsql
set search_path = ''
as $$
declare
  r public.rooms;
  rq_round integer;
  res public.room_results;
  remaining_tiebreak integer;
begin
  select * into r from public.rooms where id = p_room;
  case r.phase
    when 'roulette' then
      select round into rq_round from public.room_questions where room_id = p_room and not is_tiebreak and idx = r.question_index;
      perform public._set_phase(p_room, case when rq_round = 3 then 'betting' else 'question' end);
    when 'betting' then
      perform public._fill_default_bets(p_room, r.question_index);
      perform public._set_phase(p_room, 'question');
    when 'question' then
      perform public._close_question(p_room);
      perform public._set_phase(p_room, 'reveal');
    when 'reveal' then
      if r.question_index + 1 >= 12 then
        perform public._finish_or_tiebreak(p_room);
      elsif (r.question_index + 1) % 3 = 0 then
        perform public._set_phase(p_room, 'summary');
      else
        update public.rooms set question_index = question_index + 1 where id = p_room;
        perform public._set_phase(p_room, 'roulette');
      end if;
    when 'summary' then
      update public.rooms set question_index = question_index + 1 where id = p_room;
      perform public._set_phase(p_room, 'roulette');
    when 'tiebreak_question' then
      perform public._close_tiebreak(p_room);
      perform public._set_phase(p_room, 'tiebreak_reveal');
    when 'tiebreak_reveal' then
      select * into res from public.room_results where room_id = p_room and is_tiebreak and idx = r.tiebreak_index;
      select count(*) into remaining_tiebreak from public.room_questions
       where room_id = p_room and is_tiebreak and idx > r.tiebreak_index;
      if res.tiebreak_winner is not null then
        perform public._finish(p_room, array[res.tiebreak_winner]);
      elsif remaining_tiebreak = 0 then
        -- Sin más preguntas de desempate: victoria compartida, sin inventar un ganador.
        perform public._finish(p_room, (select array_agg(user_id order by seat) from public.room_players
                                         where room_id = p_room and in_tiebreak and left_at is null));
      else
        update public.rooms set tiebreak_index = tiebreak_index + 1 where id = p_room;
        perform public._set_phase(p_room, 'tiebreak_question');
      end if;
    else
      null;
  end case;
end;
$$;

create function public._transfer_host(p_room uuid)
returns void
language sql
set search_path = ''
as $$
  update public.rooms r
     set host_id = p.user_id, updated_at = now()
    from (select user_id from public.room_players
           where room_id = p_room and left_at is null
           order by public._connected(last_seen_at) desc, joined_at
           limit 1) p
   where r.id = p_room;
$$;

-- Avance perezoso: aplica todo lo que ya debió ocurrir. Se llama con la sala bloqueada.
create function public._advance(p_room uuid)
returns void
language plpgsql
set search_path = ''
as $$
declare
  r public.rooms;
  active integer;
  pending integer;
  guard integer := 0;
begin
  loop
    guard := guard + 1;
    exit when guard > 40;
    select * into r from public.rooms where id = p_room;

    if r.status in ('waiting', 'ready') then
      -- Sala de espera: quien dejó de responder hace 20 s sale de la sala.
      delete from public.room_players
       where room_id = p_room and last_seen_at < now() - interval '20 seconds';
      select count(*) into active from public.room_players where room_id = p_room and left_at is null;
      if active = 0 then
        update public.rooms set status = 'cancelled', cancel_reason = 'empty', updated_at = now() where id = p_room;
        exit;
      end if;
      if not exists (select 1 from public.room_players where room_id = p_room and user_id = r.host_id) then
        perform public._transfer_host(p_room);
      end if;
      update public.rooms
         set status = case when active >= 2 then 'ready' else 'waiting' end,
             -- Salas públicas: arrancan solas 15 s después de tener 2 jugadores, o al llenarse.
             autostart_at = case
               when visibility <> 'public' or active < 2 then null
               else coalesce(autostart_at, now() + interval '15 seconds') end
       where id = p_room
       returning * into r;
      if r.visibility = 'public' and active >= 2 and (active >= 4 or r.autostart_at <= now()) then
        perform public._start_game(p_room);
        continue;
      end if;
      exit;
    end if;

    exit when r.status <> 'playing';

    -- Partida abandonada: nadie conectado durante un minuto.
    if not exists (select 1 from public.room_players where room_id = p_room and left_at is null
                     and last_seen_at > now() - interval '60 seconds') then
      update public.rooms set status = 'cancelled', cancel_reason = 'abandoned', phase = null, updated_at = now()
       where id = p_room;
      exit;
    end if;

    if r.phase_ends_at <= now() then
      perform public._next_phase(p_room);
      continue;
    end if;

    -- Todos los que siguen conectados ya respondieron: no hace falta esperar al reloj.
    if r.phase in ('question', 'tiebreak_question') then
      select count(*) into pending
        from public.room_players p
       where p.room_id = p_room and p.left_at is null and public._connected(p.last_seen_at)
         and (r.phase = 'question' or p.in_tiebreak)
         and not exists (
           select 1 from public.room_answers a
            where a.room_id = p_room and a.is_tiebreak = (r.phase = 'tiebreak_question')
              and a.idx = case when r.phase = 'question' then r.question_index else r.tiebreak_index end
              and a.user_id = p.user_id and a.answered_at is not null);
      if pending = 0 then
        perform public._next_phase(p_room);
        continue;
      end if;
    end if;
    exit;
  end loop;
end;
$$;

-- ---------------------------------------------------------------- Vista del jugador

-- Estado que ve un jugador. Solo incluye la pregunta actual mientras está en juego y la solución
-- solo en la revelación.
create function public._room_view(p_room uuid, p_user uuid)
returns jsonb
language plpgsql
stable
set search_path = ''
as $$
declare
  r public.rooms;
  rq public.room_questions;
  q public.questions;
  k public.question_keys;
  res public.room_results;
  me public.room_players;
  is_tb boolean;
  cur_idx integer;
  show_question boolean;
  show_result boolean;
  view jsonb;
begin
  select * into r from public.rooms where id = p_room;
  select * into me from public.room_players where room_id = p_room and user_id = p_user;
  is_tb := r.phase in ('tiebreak_question', 'tiebreak_reveal');
  cur_idx := case when is_tb then r.tiebreak_index else r.question_index end;

  view := jsonb_build_object(
    'room', jsonb_build_object(
      'id', r.id,
      'code', r.code,
      'visibility', r.visibility,
      'mode', r.mode,
      'status', r.status,
      'phase', r.phase,
      'host_id', r.host_id,
      'question_index', r.question_index,
      'tiebreak_index', r.tiebreak_index,
      'phase_total_ms', public._phase_ms(r.phase),
      'phase_remaining_ms', case when r.phase_started_at is null then null
        else greatest(0, public._ms(r.phase_started_at + make_interval(secs => public._phase_ms(r.phase) / 1000.0) - now())) end,
      'autostart_remaining_ms', case when r.autostart_at is null then null
                                     else greatest(0, public._ms(r.autostart_at - now())) end,
      'winners', to_jsonb(r.winners),
      'cancel_reason', r.cancel_reason
    ),
    'me', p_user,
    'players', (
      select coalesce(jsonb_agg(jsonb_build_object(
        'user_id', p.user_id,
        'seat', p.seat,
        'name', p.display_name,
        'avatar_id', p.avatar_id,
        'score', p.score,
        'connected', public._connected(p.last_seen_at),
        'left', p.left_at is not null,
        'in_tiebreak', p.in_tiebreak,
        'answered', exists (
          select 1 from public.room_answers a
           where a.room_id = p_room and a.is_tiebreak = is_tb and a.idx = cur_idx
             and a.user_id = p.user_id and a.answered_at is not null),
        'bet_placed', exists (
          select 1 from public.room_answers a
           where r.phase = 'betting' and a.room_id = p_room and not a.is_tiebreak and a.idx = cur_idx
             and a.user_id = p.user_id and a.bet is not null)
      ) order by p.seat), '[]')
      from public.room_players p where p.room_id = p_room
    )
  );

  if r.status = 'playing' then
    select * into rq from public.room_questions where room_id = p_room and is_tiebreak = is_tb and idx = cur_idx;
    select * into q from public.questions where id = rq.question_id;
    show_question := r.phase in ('question', 'reveal', 'tiebreak_question', 'tiebreak_reveal');
    show_result := r.phase in ('reveal', 'tiebreak_reveal');

    view := view || jsonb_build_object('current', jsonb_build_object(
      'index', cur_idx,
      'is_tiebreak', is_tb,
      'round', rq.round,
      'category_id', rq.category_id,
      'question', case when show_question then jsonb_build_object(
        'text', q.text,
        'options', q.options,
        'kind', q.kind,
        'format', q.format,
        'is_vote', q.is_vote
      ) end,
      'my_bet', (select bet from public.room_answers
                  where room_id = p_room and is_tiebreak = is_tb and idx = cur_idx and user_id = p_user),
      'my_choice', (select choice from public.room_answers
                     where room_id = p_room and is_tiebreak = is_tb and idx = cur_idx and user_id = p_user),
      'allowed_bets', case when r.phase = 'betting' then
        case when me.score >= 100 then '[50, 100]'::jsonb when me.score >= 50 then '[50]'::jsonb else '[]'::jsonb end
      end
    ));

    if show_result then
      select * into k from public.question_keys where question_id = rq.question_id;
      select * into res from public.room_results where room_id = p_room and is_tiebreak = is_tb and idx = cur_idx;
      view := view || jsonb_build_object('result', jsonb_build_object(
        'winning_options', to_jsonb(res.winning_options),
        'vote_counts', res.vote_counts,
        'scored', res.scored,
        'tiebreak_winner', res.tiebreak_winner,
        'explanation', q.explanation,
        'source', q.source,
        'game_rule', k.game_rule,
        'outcomes', (
          select coalesce(jsonb_agg(jsonb_build_object(
            'user_id', a.user_id,
            'choice', a.choice,
            'elapsed_ms', a.elapsed_ms,
            'won', a.won,
            'points', a.points,
            'bet', a.bet
          ) order by p.seat), '[]')
          from public.room_answers a join public.room_players p on p.room_id = a.room_id and p.user_id = a.user_id
          where a.room_id = p_room and a.is_tiebreak = is_tb and a.idx = cur_idx
        )
      ));
    end if;
  end if;

  if r.status = 'finished' then
    view := view || jsonb_build_object('final', (
      select coalesce(jsonb_agg(jsonb_build_object('user_id', p.user_id, 'correct', (
        select count(*) from public.room_answers a
          join public.room_results rr on rr.room_id = a.room_id and rr.is_tiebreak = a.is_tiebreak and rr.idx = a.idx
         where a.room_id = p_room and not a.is_tiebreak and a.user_id = p.user_id and a.won
           and rr.scored and rr.vote_counts = '{}'::jsonb)) order by p.seat), '[]')
      from public.room_players p where p.room_id = p_room
    ));
  end if;

  return view;
end;
$$;

-- ---------------------------------------------------------------- Funciones públicas

-- Saca al jugador de cualquier otra sala activa (solo se juega en una a la vez).
create function public._leave_other_rooms(p_user uuid, p_keep uuid)
returns void
language plpgsql
set search_path = ''
as $$
declare
  rid uuid;
begin
  for rid in
    select p.room_id from public.room_players p join public.rooms r on r.id = p.room_id
     where p.user_id = p_user and p.room_id is distinct from p_keep and p.left_at is null
       and r.status in ('waiting', 'ready', 'playing')
  loop
    perform 1 from public.rooms where id = rid for update;
    perform public._leave(rid, p_user);
  end loop;
end;
$$;

create function public._leave(p_room uuid, p_user uuid)
returns void
language plpgsql
set search_path = ''
as $$
declare
  r public.rooms;
begin
  select * into r from public.rooms where id = p_room;
  if r.status in ('waiting', 'ready') then
    delete from public.room_players where room_id = p_room and user_id = p_user;
    if not exists (select 1 from public.room_players where room_id = p_room) then
      update public.rooms set status = 'cancelled', cancel_reason = 'empty', updated_at = now() where id = p_room;
    elsif r.host_id = p_user then
      perform public._transfer_host(p_room);
    end if;
  elsif r.status = 'playing' then
    update public.room_players set left_at = now() where room_id = p_room and user_id = p_user and left_at is null;
    if not exists (select 1 from public.room_players where room_id = p_room and left_at is null) then
      update public.rooms set status = 'cancelled', cancel_reason = 'abandoned', phase = null, updated_at = now()
       where id = p_room;
    end if;
  end if;
end;
$$;

create function public._join(p_room uuid, p_user uuid)
returns void
language plpgsql
set search_path = ''
as $$
declare
  r public.rooms;
  prof public.profiles;
  free_seat integer;
begin
  select * into r from public.rooms where id = p_room;
  if exists (select 1 from public.room_players where room_id = p_room and user_id = p_user) then
    if exists (select 1 from public.room_players where room_id = p_room and user_id = p_user and left_at is not null) then
      raise exception 'room_left' using errcode = 'P0001';
    end if;
    update public.room_players set last_seen_at = now() where room_id = p_room and user_id = p_user;
    return; -- reconexión
  end if;
  if r.status = 'playing' then
    raise exception 'room_in_progress' using errcode = 'P0001';
  end if;
  if r.status not in ('waiting', 'ready') then
    raise exception 'room_closed' using errcode = 'P0001';
  end if;
  select min(s) into free_seat from generate_series(0, 3) s
   where s not in (select seat from public.room_players where room_id = p_room);
  if free_seat is null then
    raise exception 'room_full' using errcode = 'P0001';
  end if;
  select * into prof from public.profiles where id = p_user;
  if prof.id is null then
    raise exception 'profile_missing' using errcode = 'P0001';
  end if;
  perform public._leave_other_rooms(p_user, p_room);
  insert into public.room_players (room_id, user_id, seat, display_name, avatar_id)
  values (p_room, p_user, free_seat, prof.display_name, prof.avatar_id);
  update public.rooms set updated_at = now() where id = p_room;
end;
$$;

create function public.create_room(p_visibility text, p_mode text)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  uid uuid := public._require_user();
  new_room uuid;
begin
  if p_visibility not in ('private', 'public') then
    raise exception 'invalid_visibility' using errcode = '22023';
  end if;
  if not public._valid_mode(p_mode) then
    raise exception 'invalid_mode' using errcode = '22023';
  end if;
  perform public._rate_limit(uid, 'create_room', 10, interval '10 minutes');
  perform public._leave_other_rooms(uid, null);
  insert into public.rooms (code, visibility, mode, host_id, created_by)
  values (public._new_room_code(), p_visibility, p_mode, uid, uid)
  returning id into new_room;
  perform public._join(new_room, uid);
  return public._room_view(new_room, uid);
end;
$$;

create function public.join_room_by_code(p_code text)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  uid uuid := public._require_user();
  clean text := upper(regexp_replace(coalesce(p_code, ''), '[^A-Za-z0-9]', '', 'g'));
  rid uuid;
begin
  -- Limita los intentos para que no se puedan adivinar códigos por fuerza bruta.
  perform public._rate_limit(uid, 'join_code', 20, interval '10 minutes');
  select id into rid from public.rooms
   where code = clean and status in ('waiting', 'ready', 'playing')
   for update;
  if rid is null then
    -- Se responde sin lanzar error para que el intento fallido quede registrado en el límite.
    return jsonb_build_object('error', 'room_not_found');
  end if;
  perform public._advance(rid);
  perform public._join(rid, uid);
  perform public._advance(rid);
  return public._room_view(rid, uid);
end;
$$;

create function public.find_public_room(p_mode text)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  uid uuid := public._require_user();
  rid uuid;
begin
  if not public._valid_mode(p_mode) then
    raise exception 'invalid_mode' using errcode = '22023';
  end if;
  perform public._rate_limit(uid, 'find_public', 30, interval '10 minutes');

  -- Si ya está esperando en una sala pública de esa modalidad, sigue ahí.
  select r.id into rid from public.rooms r join public.room_players p on p.room_id = r.id
   where p.user_id = uid and r.visibility = 'public' and r.mode = p_mode and r.status in ('waiting', 'ready')
   limit 1;

  if rid is null then
    -- Sala compatible (misma modalidad, con sitio y con alguien conectado), la más antigua primero.
    select r.id into rid from public.rooms r
     where r.visibility = 'public' and r.mode = p_mode and r.status in ('waiting', 'ready')
       and (select count(*) from public.room_players p where p.room_id = r.id) < 4
       and exists (select 1 from public.room_players p where p.room_id = r.id and public._connected(p.last_seen_at))
     order by r.created_at
     limit 1
     for update skip locked;
  end if;

  if rid is null then
    perform public._leave_other_rooms(uid, null);
    insert into public.rooms (code, visibility, mode, host_id, created_by)
    values (public._new_room_code(), 'public', p_mode, uid, uid)
    returning id into rid;
  else
    perform public._advance(rid);
  end if;

  perform public._join(rid, uid);
  perform public._advance(rid);
  return public._room_view(rid, uid);
end;
$$;

-- Estado de la sala para quien está dentro. También sirve de latido de conexión y de reconexión.
create function public.room_state(p_room uuid)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  uid uuid := public._require_user();
begin
  perform 1 from public.rooms where id = p_room for update;
  if not found or not exists (select 1 from public.room_players where room_id = p_room and user_id = uid) then
    raise exception 'not_in_room' using errcode = 'P0001';
  end if;
  update public.room_players set last_seen_at = now()
   where room_id = p_room and user_id = uid and left_at is null;
  perform public._advance(p_room);
  if not exists (select 1 from public.room_players where room_id = p_room and user_id = uid) then
    raise exception 'not_in_room' using errcode = 'P0001';
  end if;
  return public._room_view(p_room, uid);
end;
$$;

-- Sala activa del jugador, para volver a ella tras cerrar la app o perder la conexión. null si no hay.
create function public.current_room()
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  uid uuid := public._require_user();
  rid uuid;
begin
  select p.room_id into rid from public.room_players p join public.rooms r on r.id = p.room_id
   where p.user_id = uid and p.left_at is null and r.status in ('waiting', 'ready', 'playing')
   order by r.updated_at desc
   limit 1;
  if rid is null then
    return null;
  end if;
  perform 1 from public.rooms where id = rid for update;
  update public.room_players set last_seen_at = now() where room_id = rid and user_id = uid;
  perform public._advance(rid);
  if not exists (select 1 from public.room_players where room_id = rid and user_id = uid) then
    return null;
  end if;
  return public._room_view(rid, uid);
end;
$$;

create function public.start_room(p_room uuid)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  uid uuid := public._require_user();
  r public.rooms;
begin
  select * into r from public.rooms where id = p_room for update;
  if r.id is null or not exists (select 1 from public.room_players where room_id = p_room and user_id = uid) then
    raise exception 'not_in_room' using errcode = 'P0001';
  end if;
  update public.room_players set last_seen_at = now() where room_id = p_room and user_id = uid;
  perform public._advance(p_room);
  select * into r from public.rooms where id = p_room;
  if r.host_id <> uid then
    raise exception 'not_host' using errcode = 'P0001';
  end if;
  if r.status <> 'ready' then
    if r.status = 'waiting' then
      raise exception 'not_enough_players' using errcode = 'P0001';
    end if;
    raise exception 'wrong_status' using errcode = 'P0001';
  end if;
  perform public._start_game(p_room);
  return public._room_view(p_room, uid);
end;
$$;

create function public.leave_room(p_room uuid)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  uid uuid := public._require_user();
begin
  perform 1 from public.rooms where id = p_room for update;
  if found then
    perform public._leave(p_room, uid);
  end if;
end;
$$;

-- Apuesta de la ronda de estrategia, antes de ver la pregunta.
create function public.submit_bet(p_room uuid, p_index integer, p_amount integer)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  uid uuid := public._require_user();
  r public.rooms;
  me public.room_players;
begin
  perform 1 from public.rooms where id = p_room for update;
  select * into me from public.room_players where room_id = p_room and user_id = uid;
  if me.user_id is null then
    raise exception 'not_in_room' using errcode = 'P0001';
  end if;
  update public.room_players set last_seen_at = now() where room_id = p_room and user_id = uid;
  perform public._advance(p_room);
  select * into r from public.rooms where id = p_room;
  if me.left_at is not null then
    raise exception 'room_left' using errcode = 'P0001';
  end if;
  if r.status <> 'playing' or r.phase <> 'betting' or r.question_index <> p_index then
    raise exception 'wrong_phase' using errcode = 'P0001';
  end if;
  if not (p_amount in (50, 100) and p_amount <= me.score) and not (p_amount = 0 and me.score < 50) then
    raise exception 'invalid_bet' using errcode = '22023';
  end if;
  insert into public.room_answers (room_id, is_tiebreak, idx, user_id, bet)
  values (p_room, false, p_index, uid, p_amount)
  on conflict do nothing;
  if not found then
    raise exception 'already_bet' using errcode = 'P0001';
  end if;
  return public._room_view(p_room, uid);
end;
$$;

-- Respuesta. El tiempo lo mide el servidor; el cliente puede descontar como máximo 1,5 s de
-- latencia demostrada (nunca responder «antes» de recibir la pregunta).
create function public.submit_answer(p_room uuid, p_index integer, p_choice integer, p_client_elapsed_ms integer)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  uid uuid := public._require_user();
  r public.rooms;
  me public.room_players;
  is_tb boolean;
  rq public.room_questions;
  n_options integer;
  server_ms integer;
  elapsed integer;
  existing public.room_answers;
begin
  perform 1 from public.rooms where id = p_room for update;
  select * into me from public.room_players where room_id = p_room and user_id = uid;
  if me.user_id is null then
    raise exception 'not_in_room' using errcode = 'P0001';
  end if;
  update public.room_players set last_seen_at = now() where room_id = p_room and user_id = uid;
  select * into r from public.rooms where id = p_room;
  -- Primero se comprueba el reloj con el estado actual, sin avanzar: quien responde dentro del margen
  -- de red no pierde la pregunta porque otro haya hecho avanzar la sala justo antes.
  if me.left_at is not null then
    raise exception 'room_left' using errcode = 'P0001';
  end if;
  if r.status <> 'playing' or r.phase not in ('question', 'tiebreak_question') then
    perform public._advance(p_room);
    raise exception 'wrong_phase' using errcode = 'P0001';
  end if;
  is_tb := r.phase = 'tiebreak_question';
  if (case when is_tb then r.tiebreak_index else r.question_index end) <> p_index then
    raise exception 'wrong_question' using errcode = 'P0001';
  end if;
  if is_tb and not me.in_tiebreak then
    raise exception 'not_a_tiebreak_player' using errcode = 'P0001';
  end if;

  server_ms := public._ms(now() - r.phase_started_at);
  if server_ms > 8000 + public._answer_grace_ms() then
    perform public._advance(p_room);
    raise exception 'time_expired' using errcode = 'P0001';
  end if;

  select * into rq from public.room_questions where room_id = p_room and is_tiebreak = is_tb and idx = p_index;
  select jsonb_array_length(options) into n_options from public.questions where id = rq.question_id;
  if p_choice is null or p_choice < 0 or p_choice >= n_options then
    raise exception 'invalid_option' using errcode = '22023';
  end if;

  select * into existing from public.room_answers
   where room_id = p_room and is_tiebreak = is_tb and idx = p_index and user_id = uid;
  if existing.answered_at is not null then
    raise exception 'already_answered' using errcode = 'P0001';
  end if;

  elapsed := least(8000, greatest(0, least(server_ms, greatest(coalesce(p_client_elapsed_ms, server_ms), server_ms - public._answer_grace_ms()))));

  insert into public.room_answers (room_id, is_tiebreak, idx, user_id, choice, elapsed_ms, answered_at)
  values (p_room, is_tb, p_index, uid, p_choice, elapsed, now())
  on conflict (room_id, is_tiebreak, idx, user_id)
  do update set choice = excluded.choice, elapsed_ms = excluded.elapsed_ms, answered_at = excluded.answered_at;

  -- Solo ahora se avanza: puede cerrar la pregunta si ya respondieron todos.
  perform public._advance(p_room);
  return public._room_view(p_room, uid);
end;
$$;

-- ---------------------------------------------------------------- Permisos

do $$
declare
  f record;
begin
  -- Las funciones internas (prefijo _) no se pueden llamar desde la API.
  for f in
    select p.oid::regprocedure as sig from pg_proc p join pg_namespace n on n.oid = p.pronamespace
     where n.nspname = 'public' and p.proname like '\_%'
  loop
    execute format('revoke all on function %s from public, anon, authenticated', f.sig);
  end loop;
end;
$$;

revoke all on function public.create_room(text, text) from public, anon;
revoke all on function public.join_room_by_code(text) from public, anon;
revoke all on function public.find_public_room(text) from public, anon;
revoke all on function public.room_state(uuid) from public, anon;
revoke all on function public.current_room() from public, anon;
revoke all on function public.start_room(uuid) from public, anon;
revoke all on function public.leave_room(uuid) from public, anon;
revoke all on function public.submit_bet(uuid, integer, integer) from public, anon;
revoke all on function public.submit_answer(uuid, integer, integer, integer) from public, anon;

grant execute on function public.create_room(text, text) to authenticated;
grant execute on function public.join_room_by_code(text) to authenticated;
grant execute on function public.find_public_room(text) to authenticated;
grant execute on function public.room_state(uuid) to authenticated;
grant execute on function public.current_room() to authenticated;
grant execute on function public.start_room(uuid) to authenticated;
grant execute on function public.leave_room(uuid) to authenticated;
grant execute on function public.submit_bet(uuid, integer, integer) to authenticated;
grant execute on function public.submit_answer(uuid, integer, integer, integer) to authenticated;
