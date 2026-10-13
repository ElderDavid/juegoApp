-- Modo niños en el juego online. Dos tipos de sala nuevos, además de la normal ('standard'):
--
-- * 'kids'  (sala de niños): todos responden preguntas del banco para niños, con 12 s por pregunta,
--   y en la ronda de estrategia fallar no resta lo apostado.
-- * 'mixed' (sala mixta): cada jugador elige su nivel. Quien juega como niño recibe la pregunta para
--   niños de la misma categoría, con 12 s y sin perder apuestas; los demás, la pregunta normal con
--   8 s. La ruleta es común: las dos preguntas de cada turno son de la misma categoría.
--
-- Las dos solo existen en la modalidad general (el banco para niños no tiene preguntas regionales)
-- y no tienen preguntas de votación. Las reglas son las de core (GameRules.KIDS_QUESTION_TIME_MILLIS,
-- Scoring.betDelta con noLoss) y docs/MODO_NINOS.md.

-- ---------------------------------------------------------------- Esquema

alter table public.questions
  add column audience text not null default 'ALL' check (audience in ('ALL', 'KIDS'));

drop index if exists public.questions_pool_idx;
create index questions_pool_idx on public.questions (audience, region_id);

alter table public.rooms
  add column audience text not null default 'standard' check (audience in ('standard', 'kids', 'mixed'));

drop index if exists public.rooms_public_lobby_idx;
create index rooms_public_lobby_idx on public.rooms (mode, audience, question_count, created_at)
  where visibility = 'public' and status in ('waiting', 'ready');

-- Nivel del jugador en una sala mixta: true = preguntas para niños.
alter table public.room_players add column kids boolean not null default false;

-- En las salas mixtas cada turno tiene dos preguntas: la normal (false) y la de niños (true).
alter table public.room_questions add column for_kids boolean not null default false;
alter table public.room_questions drop constraint room_questions_pkey;
alter table public.room_questions add primary key (room_id, is_tiebreak, idx, for_kids);

alter table public.room_results add column for_kids boolean not null default false;
alter table public.room_results drop constraint room_results_pkey;
alter table public.room_results add primary key (room_id, is_tiebreak, idx, for_kids);

-- Con preguntas para niños se puede tardar hasta 12 s.
alter table public.room_answers drop constraint room_answers_elapsed_ms_check;
alter table public.room_answers add constraint room_answers_elapsed_ms_check check (elapsed_ms between 0 and 12000);

-- ---------------------------------------------------------------- Utilidades

create function public._valid_audience(p_audience text, p_mode text)
returns boolean
language sql
immutable
set search_path = ''
as $$
  select p_audience = 'standard' or (p_audience in ('kids', 'mixed') and p_mode = 'general');
$$;

-- true si el jugador responde preguntas para niños en una sala de este tipo.
create function public._plays_kids(p_audience text, p_kids boolean)
returns boolean
language sql
immutable
set search_path = ''
as $$
  select p_audience = 'kids' or (p_audience = 'mixed' and coalesce(p_kids, false));
$$;

-- Tiempo para responder de un jugador: 12 s con preguntas para niños, 8 s con las normales.
create function public._answer_ms(p_kids_player boolean)
returns integer
language sql
immutable
set search_path = ''
as $$
  select case when p_kids_player then 12000 else 8000 end;
$$;

-- Duración de una fase en una sala. Las preguntas duran lo que necesita el jugador más lento de
-- la sala: 12 s si alguien puede jugar como niño.
create function public._room_phase_ms(p_phase text, p_audience text)
returns integer
language sql
immutable
set search_path = ''
as $$
  select case
    when p_phase in ('question', 'tiebreak_question') and p_audience in ('kids', 'mixed') then 12000
    else public._phase_ms(p_phase)
  end;
$$;

create or replace function public._set_phase(p_room uuid, p_phase text)
returns void
language sql
set search_path = ''
as $$
  update public.rooms
     set phase = p_phase,
         phase_started_at = now(),
         phase_ends_at = now() + make_interval(secs => (public._room_phase_ms(p_phase, audience)
           + case when p_phase in ('question', 'tiebreak_question') then public._answer_grace_ms() else 0 end) / 1000.0),
         updated_at = now()
   where id = p_room;
$$;

-- ---------------------------------------------------------------- Planificación

-- Preguntas que puede usar una sala: [p_kids] elige el banco para niños o el normal.
create function public._in_pool(q public.questions, p_mode text, p_kids boolean)
returns boolean
language sql
immutable
set search_path = ''
as $$
  select case when p_kids then q.audience = 'KIDS'
              else q.audience = 'ALL' and q.region_id is not distinct from nullif(p_mode, 'general') end;
$$;

create or replace function public._plan_room(p_room uuid, p_mode text, p_players integer)
returns void
language plpgsql
set search_path = ''
as $$
declare
  room_audience text;
  total integer;
  -- Bancos que se planifican: normal, de niños o los dos (sala mixta).
  pools boolean[];
  -- Las votaciones solo en salas normales con 3 o más jugadores, como en local.
  votes_allowed integer;
  votes_used integer := 0;
  used text[] := '{}';
  recent text[] := '{}';
  round integer;
  categories text[];
  pool_categories text[];
  fresh text[];
  chosen_category text;
  chosen public.questions;
  for_kids boolean;
begin
  select question_count, audience into total, room_audience from public.rooms where id = p_room;
  pools := case room_audience when 'kids' then array[true] when 'mixed' then array[false, true] else array[false] end;
  votes_allowed := case when room_audience = 'standard' and p_players >= 3 then 1 else 0 end;
  delete from public.room_questions where room_id = p_room;

  for i in 0..total - 1 loop
    round := public._round_for(i, total);
    -- Categorías posibles en todos los bancos de la sala, del filtro más estricto al más permisivo.
    categories := null;
    for tier in 1..3 loop
      categories := null;
      foreach for_kids in array pools loop
        select array_agg(distinct q.category_id order by q.category_id) into pool_categories
          from public.questions q
         where public._in_pool(q, p_mode, for_kids)
           and not (q.id = any (used))
           and (not q.is_vote or votes_used < votes_allowed)
           and public._eligible(q, round, tier);
        categories := case when categories is null then pool_categories
                           else array(select unnest(categories) intersect select unnest(pool_categories) order by 1) end;
      end loop;
      exit when cardinality(categories) > 0;
    end loop;
    if categories is null or cardinality(categories) = 0 then
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

    -- Una pregunta de esa categoría en cada banco, con el filtro más estricto que tenga alguna.
    foreach for_kids in array pools loop
      for tier in 1..3 loop
        select q.* into chosen
          from public.questions q
         where public._in_pool(q, p_mode, for_kids)
           and q.category_id = chosen_category
           and not (q.id = any (used))
           and (not q.is_vote or votes_used < votes_allowed)
           and public._eligible(q, round, tier)
         order by random()
         limit 1;
        exit when found;
      end loop;
      used := used || chosen.id;
      if chosen.is_vote then
        votes_used := votes_used + 1;
      end if;
      insert into public.room_questions (room_id, is_tiebreak, idx, round, question_id, category_id, for_kids)
      values (p_room, false, i, round, chosen.id, chosen_category, for_kids);
    end loop;
    recent := recent || chosen_category;
  end loop;

  -- Reserva de desempate en cada banco: objetivas no usadas, las más difíciles primero.
  foreach for_kids in array pools loop
    insert into public.room_questions (room_id, is_tiebreak, idx, round, question_id, category_id, for_kids)
    select p_room, true, (row_number() over (order by ord) - 1)::smallint, 0, id, category_id, for_kids
      from (
        select q.id, q.category_id,
               row_number() over (order by case q.difficulty when 'HARD' then 0 when 'MEDIUM' then 1 else 2 end, random()) as ord
          from public.questions q
         where public._in_pool(q, p_mode, for_kids)
           and not q.is_vote
           and not (q.id = any (used))
      ) t
     where ord <= 5;
  end loop;
end;
$$;

-- ---------------------------------------------------------------- Puntuación

create or replace function public._close_question(p_room uuid)
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
  counts jsonb;
  valid_votes integer;
  max_votes integer;
  is_scored boolean;
  a record;
  delta integer;
  did_win boolean;
  limit_ms integer;
begin
  select * into r from public.rooms where id = p_room;
  if exists (select 1 from public.room_results where room_id = p_room and not is_tiebreak and idx = r.question_index) then
    return; -- ya puntuada: nunca se suman puntos dos veces
  end if;

  -- Quien no respondió (o abandonó) cuenta como tiempo agotado.
  insert into public.room_answers (room_id, is_tiebreak, idx, user_id, bet)
  select p.room_id, false, r.question_index, p.user_id,
         case when public._round_for(r.question_index, r.question_count) = 3 then public._default_bet(p.score) end
    from public.room_players p
   where p.room_id = p_room
  on conflict (room_id, is_tiebreak, idx, user_id) do nothing;

  -- Cada pregunta del turno (una, o dos en las salas mixtas) se puntúa con quienes la vieron.
  for rq in select * from public.room_questions where room_id = p_room and not is_tiebreak and idx = r.question_index loop
    select * into q from public.questions where id = rq.question_id;
    select * into k from public.question_keys where question_id = rq.question_id;
    counts := '{}';

    if k.correct_index is not null then
      winning := array[k.correct_index];
      is_scored := true;
    else
      -- Votación (solo salas normales: allí todos ven la misma pregunta).
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
      select ra.*, p.score, public._plays_kids(r.audience, p.kids) as kid
        from public.room_answers ra
        join public.room_players p on p.room_id = ra.room_id and p.user_id = ra.user_id
       where ra.room_id = p_room and not ra.is_tiebreak and ra.idx = r.question_index
         and public._plays_kids(r.audience, p.kids) = rq.for_kids
    loop
      limit_ms := public._answer_ms(a.kid);
      if not is_scored then
        did_win := null;
        delta := 0; -- votación sin votos suficientes: ni premio ni pérdida de apuesta
      else
        did_win := a.choice is not null and a.choice = any (winning);
        if rq.round = 3 then
          delta := case
            -- Con preguntas para niños fallar no resta lo apostado.
            when a.bet > 0 then case when did_win then a.bet when a.kid then 0 else -a.bet end
            else case when did_win then 50 else 0 end -- apuesta libre
          end;
        elsif q.is_vote then
          delta := case when did_win then 100 else 0 end;
        elsif not did_win then
          delta := 0;
        else
          -- Bono de rapidez sobre el tiempo de cada jugador (8 o 12 s).
          delta := case rq.round when 4 then 150 else 100 end
            + case when rq.round in (2, 4)
                   then (50 * (limit_ms - least(greatest(coalesce(a.elapsed_ms, limit_ms), 0), limit_ms))) / limit_ms
                   else 0 end;
        end if;
      end if;
      update public.room_answers set won = did_win, points = delta
       where room_id = p_room and not is_tiebreak and idx = r.question_index and user_id = a.user_id;
      update public.room_players set score = score + delta
       where room_id = p_room and user_id = a.user_id;
    end loop;

    insert into public.room_results (room_id, is_tiebreak, idx, for_kids, winning_options, vote_counts, scored)
    values (p_room, false, r.question_index, rq.for_kids, winning, case when q.is_vote then counts else '{}' end, is_scored);
  end loop;
end;
$$;

-- Desempate: gana la respuesta correcta más rápida en proporción al tiempo de cada uno.
create or replace function public._close_tiebreak(p_room uuid)
returns void
language plpgsql
set search_path = ''
as $$
declare
  r public.rooms;
  winner uuid;
begin
  select * into r from public.rooms where id = p_room;
  if exists (select 1 from public.room_results where room_id = p_room and is_tiebreak and idx = r.tiebreak_index) then
    return;
  end if;

  insert into public.room_answers (room_id, is_tiebreak, idx, user_id)
  select room_id, true, r.tiebreak_index, user_id from public.room_players
   where room_id = p_room and in_tiebreak
  on conflict (room_id, is_tiebreak, idx, user_id) do nothing;

  -- Cada uno se corrige con la clave de la pregunta que vio.
  update public.room_answers a
     set won = (a.choice is not null and a.choice = k.correct_index)
    from public.room_players p, public.room_questions rq, public.question_keys k
   where a.room_id = p_room and a.is_tiebreak and a.idx = r.tiebreak_index
     and p.room_id = a.room_id and p.user_id = a.user_id
     and rq.room_id = a.room_id and rq.is_tiebreak and rq.idx = a.idx
     and rq.for_kids = public._plays_kids(r.audience, p.kids)
     and k.question_id = rq.question_id;

  with ranked as (
    select a.user_id, a.elapsed_ms::numeric / public._answer_ms(public._plays_kids(r.audience, p.kids)) as fraction
      from public.room_answers a
      join public.room_players p on p.room_id = a.room_id and p.user_id = a.user_id
     where a.room_id = p_room and a.is_tiebreak and a.idx = r.tiebreak_index and a.won
  )
  select case when count(*) = 1 then (array_agg(user_id))[1] end into winner
    from ranked where fraction = (select min(fraction) from ranked);

  insert into public.room_results (room_id, is_tiebreak, idx, for_kids, winning_options, scored, tiebreak_winner)
  select p_room, true, r.tiebreak_index, rq.for_kids, array[k.correct_index], true, winner
    from public.room_questions rq join public.question_keys k on k.question_id = rq.question_id
   where rq.room_id = p_room and rq.is_tiebreak and rq.idx = r.tiebreak_index;
end;
$$;

-- Aciertos de un jugador en preguntas objetivas puntuadas (para estadísticas y el final).
create function public._correct_answers(p_room uuid, p_user uuid)
returns integer
language sql
stable
set search_path = ''
as $$
  select count(*)::integer
    from public.room_answers ra
    join public.rooms r on r.id = ra.room_id
    join public.room_players p on p.room_id = ra.room_id and p.user_id = ra.user_id
    join public.room_results rr on rr.room_id = ra.room_id and rr.is_tiebreak = ra.is_tiebreak and rr.idx = ra.idx
                               and rr.for_kids = public._plays_kids(r.audience, p.kids)
   where ra.room_id = p_room and not ra.is_tiebreak and ra.user_id = p_user and ra.won
     and rr.scored and rr.vote_counts = '{}'::jsonb;
$$;

create or replace function public._apply_stats(p_room uuid)
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
  for p in select * from public.room_players where room_id = p_room loop
    -- Preguntas objetivas que vio este jugador: las que se puntuaron sin recuento de votos.
    select count(*) into objective from public.room_results
     where room_id = p_room and not is_tiebreak and scored and vote_counts = '{}'::jsonb
       and for_kids = public._plays_kids(r.audience, p.kids);
    correct := public._correct_answers(p_room, p.user_id);
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

-- ---------------------------------------------------------------- Vista del jugador

create or replace function public._room_view(p_room uuid, p_user uuid)
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
  my_kids boolean;
  my_ms integer;
  total_ms integer;
  show_question boolean;
  show_result boolean;
  view jsonb;
begin
  select * into r from public.rooms where id = p_room;
  select * into me from public.room_players where room_id = p_room and user_id = p_user;
  is_tb := r.phase in ('tiebreak_question', 'tiebreak_reveal');
  cur_idx := case when is_tb then r.tiebreak_index else r.question_index end;
  my_kids := public._plays_kids(r.audience, me.kids);
  my_ms := public._answer_ms(my_kids);
  -- En las preguntas cada uno ve su propio reloj: 8 s o 12 s aunque la fase dure 12 s para todos.
  total_ms := case when r.phase in ('question', 'tiebreak_question') then my_ms
                   else public._room_phase_ms(r.phase, r.audience) end;

  view := jsonb_build_object(
    'room', jsonb_build_object(
      'id', r.id,
      'code', r.code,
      'visibility', r.visibility,
      'mode', r.mode,
      'audience', r.audience,
      'status', r.status,
      'phase', r.phase,
      'host_id', r.host_id,
      'question_index', r.question_index,
      'question_count', r.question_count,
      'tiebreak_index', r.tiebreak_index,
      'phase_total_ms', total_ms,
      'phase_remaining_ms', case when r.phase_started_at is null then null
        else greatest(0, public._ms(r.phase_started_at + make_interval(secs => total_ms / 1000.0) - now())) end,
      'autostart_remaining_ms', case when r.autostart_at is null then null
                                     else greatest(0, public._ms(r.autostart_at - now())) end,
      'winners', to_jsonb(r.winners),
      'cancel_reason', r.cancel_reason
    ),
    'me', p_user,
    'my_kids', my_kids,
    'players', (
      select coalesce(jsonb_agg(jsonb_build_object(
        'user_id', p.user_id,
        'seat', p.seat,
        'name', p.display_name,
        'avatar_id', p.avatar_id,
        'score', p.score,
        'kids', public._plays_kids(r.audience, p.kids),
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
    select * into rq from public.room_questions
     where room_id = p_room and is_tiebreak = is_tb and idx = cur_idx and for_kids = my_kids;
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
      select * into res from public.room_results
       where room_id = p_room and is_tiebreak = is_tb and idx = cur_idx and for_kids = my_kids;
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
            'bet', a.bet,
            -- false si este jugador respondió otra pregunta (sala mixta): su opción no es de esta.
            'same_question', public._plays_kids(r.audience, p.kids) = my_kids
          ) order by p.seat), '[]')
          from public.room_answers a join public.room_players p on p.room_id = a.room_id and p.user_id = a.user_id
          where a.room_id = p_room and a.is_tiebreak = is_tb and a.idx = cur_idx
        )
      ));
    end if;
  end if;

  if r.status = 'finished' then
    view := view || jsonb_build_object('final', (
      select coalesce(jsonb_agg(jsonb_build_object('user_id', p.user_id,
               'correct', public._correct_answers(p_room, p.user_id)) order by p.seat), '[]')
      from public.room_players p where p.room_id = p_room
    ));
  end if;

  return view;
end;
$$;

-- ---------------------------------------------------------------- Entrar y elegir nivel

drop function public._join(uuid, uuid);

create function public._join(p_room uuid, p_user uuid, p_kids boolean default false)
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
    return; -- reconexión: conserva el nivel elegido
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
  insert into public.room_players (room_id, user_id, seat, display_name, avatar_id, kids)
  values (p_room, p_user, free_seat, prof.display_name, prof.avatar_id, r.audience = 'mixed' and coalesce(p_kids, false));
  update public.rooms set updated_at = now() where id = p_room;
end;
$$;

drop function public.create_room(text, text, integer);
drop function public.join_room_by_code(text);
drop function public.find_public_room(text, integer);

-- [p_audience]: 'standard', 'kids' o 'mixed'. [p_kids]: nivel de quien crea la sala si es mixta.
create function public.create_room(
  p_visibility text, p_mode text, p_questions integer default 12,
  p_audience text default 'standard', p_kids boolean default false)
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
  if not public._valid_length(p_questions) then
    raise exception 'invalid_length' using errcode = '22023';
  end if;
  if not public._valid_audience(p_audience, p_mode) then
    raise exception 'invalid_audience' using errcode = '22023';
  end if;
  perform public._rate_limit(uid, 'create_room', 10, interval '10 minutes');
  perform public._leave_other_rooms(uid, null);
  insert into public.rooms (code, visibility, mode, question_count, audience, host_id, created_by)
  values (public._new_room_code(), p_visibility, p_mode, p_questions, p_audience, uid, uid)
  returning id into new_room;
  perform public._join(new_room, uid, p_kids);
  return public._room_view(new_room, uid);
end;
$$;

create function public.join_room_by_code(p_code text, p_kids boolean default false)
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
  perform public._join(rid, uid, p_kids);
  perform public._advance(rid);
  return public._room_view(rid, uid);
end;
$$;

create function public.find_public_room(
  p_mode text, p_questions integer default 12,
  p_audience text default 'standard', p_kids boolean default false)
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
  if not public._valid_length(p_questions) then
    raise exception 'invalid_length' using errcode = '22023';
  end if;
  if not public._valid_audience(p_audience, p_mode) then
    raise exception 'invalid_audience' using errcode = '22023';
  end if;
  perform public._rate_limit(uid, 'find_public', 30, interval '10 minutes');

  -- Si ya está esperando en una sala pública igual, sigue ahí.
  select r.id into rid from public.rooms r join public.room_players p on p.room_id = r.id
   where p.user_id = uid and r.visibility = 'public' and r.mode = p_mode and r.question_count = p_questions
     and r.audience = p_audience and r.status in ('waiting', 'ready')
   limit 1;

  if rid is null then
    -- Sala compatible (misma modalidad, tipo y duración, con sitio y con alguien conectado), la más antigua primero.
    select r.id into rid from public.rooms r
     where r.visibility = 'public' and r.mode = p_mode and r.question_count = p_questions
       and r.audience = p_audience
       and r.status in ('waiting', 'ready')
       and (select count(*) from public.room_players p where p.room_id = r.id) < 4
       and exists (select 1 from public.room_players p where p.room_id = r.id and public._connected(p.last_seen_at))
     order by r.created_at
     limit 1
     for update skip locked;
  end if;

  if rid is null then
    perform public._leave_other_rooms(uid, null);
    insert into public.rooms (code, visibility, mode, question_count, audience, host_id, created_by)
    values (public._new_room_code(), 'public', p_mode, p_questions, p_audience, uid, uid)
    returning id into rid;
  else
    perform public._advance(rid);
  end if;

  perform public._join(rid, uid, p_kids);
  perform public._advance(rid);
  return public._room_view(rid, uid);
end;
$$;

-- Cambia el nivel del jugador en una sala mixta, solo antes de empezar.
create function public.set_kids_level(p_room uuid, p_kids boolean)
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
  if r.audience <> 'mixed' then
    raise exception 'invalid_audience' using errcode = '22023';
  end if;
  if r.status not in ('waiting', 'ready') then
    raise exception 'wrong_status' using errcode = 'P0001';
  end if;
  update public.room_players set kids = coalesce(p_kids, false), last_seen_at = now()
   where room_id = p_room and user_id = uid;
  perform public._advance(p_room);
  return public._room_view(p_room, uid);
end;
$$;

-- ---------------------------------------------------------------- Respuesta

create or replace function public.submit_answer(p_room uuid, p_index integer, p_choice integer, p_client_elapsed_ms integer)
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
  limit_ms integer;
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

  -- 8 s con preguntas normales y 12 s con preguntas para niños, aunque la fase dure más.
  limit_ms := public._answer_ms(public._plays_kids(r.audience, me.kids));
  server_ms := public._ms(now() - r.phase_started_at);
  if server_ms > limit_ms + public._answer_grace_ms() then
    perform public._advance(p_room);
    raise exception 'time_expired' using errcode = 'P0001';
  end if;

  select * into rq from public.room_questions
   where room_id = p_room and is_tiebreak = is_tb and idx = p_index
     and for_kids = public._plays_kids(r.audience, me.kids);
  select jsonb_array_length(options) into n_options from public.questions where id = rq.question_id;
  if p_choice is null or p_choice < 0 or p_choice >= n_options then
    raise exception 'invalid_option' using errcode = '22023';
  end if;

  select * into existing from public.room_answers
   where room_id = p_room and is_tiebreak = is_tb and idx = p_index and user_id = uid;
  if existing.answered_at is not null then
    raise exception 'already_answered' using errcode = 'P0001';
  end if;

  elapsed := least(limit_ms, greatest(0, least(server_ms, greatest(coalesce(p_client_elapsed_ms, server_ms), server_ms - public._answer_grace_ms()))));

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
  for f in
    select p.oid::regprocedure as sig from pg_proc p join pg_namespace n on n.oid = p.pronamespace
     where n.nspname = 'public' and p.proname like '\_%'
  loop
    execute format('revoke all on function %s from public, anon, authenticated', f.sig);
  end loop;
end;
$$;

revoke all on function public.create_room(text, text, integer, text, boolean) from public, anon;
revoke all on function public.join_room_by_code(text, boolean) from public, anon;
revoke all on function public.find_public_room(text, integer, text, boolean) from public, anon;
revoke all on function public.set_kids_level(uuid, boolean) from public, anon;
grant execute on function public.create_room(text, text, integer, text, boolean) to authenticated;
grant execute on function public.join_room_by_code(text, boolean) to authenticated;
grant execute on function public.find_public_room(text, integer, text, boolean) to authenticated;
grant execute on function public.set_kids_level(uuid, boolean) to authenticated;
