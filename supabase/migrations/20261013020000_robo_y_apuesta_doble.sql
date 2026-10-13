-- Pregunta robo y apuesta doble.
--
-- * Apuestas (ronda 3): acertar paga el doble de lo apostado (apuestas 100, ganas 200). Fallar
--   sigue restando lo apostado, salvo con preguntas para niños. Igual que Scoring.betDelta.
-- * Pregunta robo: la última de la ronda 2 (velocidad), a mitad de partida. Quien acierta más
--   rápido, sin empate exacto, le quita el 20 % de su saldo a cada rival. Solo en salas normales
--   (no en salas de niños ni mixtas). Igual que GameEngine.applyHeist.
-- * room_answers.stolen guarda lo robado (positivo) o perdido (negativo); ya está sumado en points.

alter table public.room_answers add column stolen integer not null default 0;

-- true si [p_idx] es la última pregunta de la ronda 2 (GameLength.isHeist).
create function public._is_heist(p_idx integer, p_count integer)
returns boolean
language sql
immutable
set search_path = ''
as $$
  select p_idx + 1 < p_count
     and public._round_for(p_idx, p_count) = 2
     and public._round_for(p_idx + 1, p_count) = 3;
$$;
revoke all on function public._is_heist(integer, integer) from public, anon, authenticated;

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
  thief uuid;
  fastest_ms integer;
  loss integer;
  gain integer := 0;
  v record;
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
            -- Acertar paga el doble de lo apostado. Con preguntas para niños fallar no resta.
            when a.bet > 0 then case when did_win then a.bet * 2 when a.kid then 0 else -a.bet end
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

  -- Pregunta robo (GameRules.HEIST_PERCENT): quien acierta más rápido, sin empate exacto, le quita
  -- el 20 % a cada rival sobre su saldo ya actualizado. Solo en salas normales.
  if r.audience = 'standard' and public._is_heist(r.question_index, r.question_count) then
    select ra.user_id, ra.elapsed_ms into thief, fastest_ms
      from public.room_answers ra
     where ra.room_id = p_room and not ra.is_tiebreak and ra.idx = r.question_index
       and ra.won and ra.elapsed_ms is not null
     order by ra.elapsed_ms
     limit 1;
    if (select count(*) from public.room_answers x
         where x.room_id = p_room and not x.is_tiebreak and x.idx = r.question_index
           and x.won and x.elapsed_ms = fastest_ms) > 1 then
      thief := null; -- empate exacto: no hay robo
    end if;
    if thief is not null then
      for v in select user_id, score from public.room_players where room_id = p_room and user_id <> thief loop
        loss := greatest(v.score, 0) * 20 / 100;
        gain := gain + loss;
        update public.room_players set score = score - loss where room_id = p_room and user_id = v.user_id;
        update public.room_answers set stolen = -loss, points = points - loss
         where room_id = p_room and not is_tiebreak and idx = r.question_index and user_id = v.user_id;
      end loop;
      update public.room_players set score = score + gain where room_id = p_room and user_id = thief;
      update public.room_answers set stolen = gain, points = points + gain
       where room_id = p_room and not is_tiebreak and idx = r.question_index and user_id = thief;
    end if;
  end if;
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
      'heist', not is_tb and r.audience = 'standard' and public._is_heist(cur_idx, r.question_count),
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
            'stolen', a.stolen,
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
