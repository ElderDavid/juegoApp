-- Fase 7: clasificación online.
-- Las estadísticas detalladas siguen siendo privadas (RLS de player_stats). Esta función solo
-- expone, para quien tenga sesión, nombre, avatar, XP, victorias y partidas de los mejores
-- jugadores, más la posición propia aunque quede fuera del top.
-- Solo cuentan partidas online: las escribe el servidor al terminar cada sala (_apply_stats).

create function public.leaderboard(p_limit integer default 20)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  uid uuid := public._require_user();
  lim integer := least(greatest(coalesce(p_limit, 20), 1), 50);
  top jsonb;
  mine jsonb;
begin
  with ranked as (
    select s.user_id, p.display_name, p.avatar_id, s.xp, s.wins, s.games_played,
           rank() over (order by s.xp desc, s.wins desc) as pos,
           row_number() over (order by s.xp desc, s.wins desc, p.display_name, s.user_id) as n
    from public.player_stats s
    join public.profiles p on p.id = s.user_id
    where s.games_played > 0
  )
  select
    coalesce(jsonb_agg(jsonb_build_object(
      'rank', r.pos, 'name', r.display_name, 'avatar_id', r.avatar_id,
      'xp', r.xp, 'wins', r.wins, 'games', r.games_played, 'me', r.user_id = uid
    ) order by r.n) filter (where r.n <= lim), '[]'::jsonb),
    (array_agg(jsonb_build_object(
      'rank', r.pos, 'name', r.display_name, 'avatar_id', r.avatar_id,
      'xp', r.xp, 'wins', r.wins, 'games', r.games_played, 'me', true
    )) filter (where r.user_id = uid))[1]
  into top, mine
  from ranked r;

  return jsonb_build_object('top', top, 'me', mine);
end;
$$;

revoke all on function public.leaderboard(integer) from public, anon;
grant execute on function public.leaderboard(integer) to authenticated;
