-- Duración de la partida elegible: 8, 10 o 12 preguntas, siempre en 4 rondas (GameLength en core).
-- 8 = 2+2+2+2, 10 = 3+2+2+3, 12 = 3+3+3+3. Las salas existentes quedan en 12, como hasta ahora.

alter table public.rooms
  add column question_count smallint not null default 12 check (question_count in (8, 10, 12));

drop index if exists public.rooms_public_lobby_idx;
create index rooms_public_lobby_idx on public.rooms (mode, question_count, created_at)
  where visibility = 'public' and status in ('waiting', 'ready');

create function public._valid_length(p_questions integer)
returns boolean
language sql
immutable
set search_path = ''
as $$
  select p_questions in (8, 10, 12);
$$;

-- Ronda (1 a 4) de la pregunta [p_idx] en una partida de [p_count] preguntas (GameLength.roundFor).
create function public._round_for(p_idx integer, p_count integer)
returns integer
language sql
immutable
set search_path = ''
as $$
  select case p_count
    when 8 then p_idx / 2 + 1
    when 10 then case when p_idx < 3 then 1 when p_idx < 5 then 2 when p_idx < 7 then 3 else 4 end
    else p_idx / 3 + 1
  end;
$$;

create or replace function public._plan_room(p_room uuid, p_mode text, p_players integer)
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
  total integer;
begin
  select question_count into total from public.rooms where id = p_room;
  delete from public.room_questions where room_id = p_room;

  for i in 0..total - 1 loop
    round := public._round_for(i, total);
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

create or replace function public._next_phase(p_room uuid)
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
      if r.question_index + 1 >= r.question_count then
        perform public._finish_or_tiebreak(p_room);
      elsif public._round_for(r.question_index + 1, r.question_count) <> public._round_for(r.question_index, r.question_count) then
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
      'question_count', r.question_count,
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

-- Se sustituyen por versiones con la duración; las llamadas sin ella siguen creando partidas de 12.
drop function public.create_room(text, text);
drop function public.find_public_room(text);

create function public.create_room(p_visibility text, p_mode text, p_questions integer default 12)
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
  perform public._rate_limit(uid, 'create_room', 10, interval '10 minutes');
  perform public._leave_other_rooms(uid, null);
  insert into public.rooms (code, visibility, mode, question_count, host_id, created_by)
  values (public._new_room_code(), p_visibility, p_mode, p_questions, uid, uid)
  returning id into new_room;
  perform public._join(new_room, uid);
  return public._room_view(new_room, uid);
end;
$$;

create function public.find_public_room(p_mode text, p_questions integer default 12)
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
  perform public._rate_limit(uid, 'find_public', 30, interval '10 minutes');

  -- Si ya está esperando en una sala pública de esa modalidad y duración, sigue ahí.
  select r.id into rid from public.rooms r join public.room_players p on p.room_id = r.id
   where p.user_id = uid and r.visibility = 'public' and r.mode = p_mode and r.question_count = p_questions
     and r.status in ('waiting', 'ready')
   limit 1;

  if rid is null then
    -- Sala compatible (misma modalidad y duración, con sitio y con alguien conectado), la más antigua primero.
    select r.id into rid from public.rooms r
     where r.visibility = 'public' and r.mode = p_mode and r.question_count = p_questions
       and r.status in ('waiting', 'ready')
       and (select count(*) from public.room_players p where p.room_id = r.id) < 4
       and exists (select 1 from public.room_players p where p.room_id = r.id and public._connected(p.last_seen_at))
     order by r.created_at
     limit 1
     for update skip locked;
  end if;

  if rid is null then
    perform public._leave_other_rooms(uid, null);
    insert into public.rooms (code, visibility, mode, question_count, host_id, created_by)
    values (public._new_room_code(), 'public', p_mode, p_questions, uid, uid)
    returning id into rid;
  else
    perform public._advance(rid);
  end if;

  perform public._join(rid, uid);
  perform public._advance(rid);
  return public._room_view(rid, uid);
end;
$$;

revoke all on function public._valid_length(integer) from public, anon, authenticated;
revoke all on function public._round_for(integer, integer) from public, anon, authenticated;
revoke all on function public.create_room(text, text, integer) from public, anon;
revoke all on function public.find_public_room(text, integer) from public, anon;
grant execute on function public.create_room(text, text, integer) to authenticated;
grant execute on function public.find_public_room(text, integer) to authenticated;
