-- Economía del juego: monedas, premios por nivel, logros, racha diaria, videos recompensados,
-- comodines de partida local, tienda de cosméticos y compras de Google Play.
--
-- Reglas (las mismas que core/economy/Economy.kt; EconomyCatalogTest compara los catálogos):
-- - El saldo solo lo cambia el servidor. Los clientes no leen ni escriben estas tablas: todo pasa
--   por funciones (RLS activado sin políticas).
-- - Cada movimiento de monedas queda en coin_ledger. Los premios únicos llevan una referencia
--   (nivel, logro, día, compra, video) y la restricción unique impide cobrarlos dos veces.
-- - Los días se cuentan en hora de Colombia.
-- - Los videos y las compras solo los puede acreditar el servidor (funciones de Supabase con la
--   clave de servicio, tras verificar la firma de AdMob o la compra con Google Play).

-- ------------------------------------------------------------------ Tablas

-- XP de partidas locales, separado del XP online para que la clasificación siga siendo solo online.
alter table public.player_stats
  add column local_xp integer not null default 0 check (local_xp >= 0);

create table public.wallets (
  user_id uuid primary key references public.profiles (id) on delete cascade,
  coins integer not null default 0 check (coins >= 0),
  -- Registro de que compró «Quitar anuncios». La app lo comprueba con Google Play.
  remove_ads boolean not null default false,
  streak_day integer not null default 0 check (streak_day >= 0),
  last_daily date,
  updated_at timestamptz not null default now()
);

create table public.coin_ledger (
  id bigserial primary key,
  user_id uuid not null references public.profiles (id) on delete cascade,
  delta integer not null check (delta <> 0),
  reason text not null,
  -- Referencia del premio o gasto único (null = repetible, como las monedas por partida).
  ref text,
  day date not null,
  created_at timestamptz not null default now(),
  unique (user_id, reason, ref)
);
create index coin_ledger_user_day on public.coin_ledger (user_id, day, reason);

create table public.local_games (
  user_id uuid not null references public.profiles (id) on delete cascade,
  game_key text not null check (char_length(game_key) between 8 and 64),
  day date not null,
  regional boolean not null,
  perfect_round boolean not null,
  xp integer not null,
  created_at timestamptz not null default now(),
  primary key (user_id, game_key)
);

create table public.cosmetics (
  id text primary key,
  kind text not null check (kind in ('FRAME', 'TITLE', 'THEME', 'ROULETTE_SOUND', 'VICTORY')),
  name text not null,
  unlock_level integer check (unlock_level >= 2),
  price integer check (price > 0),
  check ((unlock_level is null) <> (price is null))
);

-- Solo lo comprado. Lo ganado por nivel se calcula con el nivel actual.
create table public.owned_cosmetics (
  user_id uuid not null references public.profiles (id) on delete cascade,
  cosmetic_id text not null references public.cosmetics (id),
  acquired_at timestamptz not null default now(),
  primary key (user_id, cosmetic_id)
);

create table public.equipped_cosmetics (
  user_id uuid not null references public.profiles (id) on delete cascade,
  kind text not null,
  cosmetic_id text not null references public.cosmetics (id),
  primary key (user_id, kind)
);

create table public.achievements (
  id text primary key,
  name text not null,
  coins integer not null check (coins > 0)
);

create table public.user_achievements (
  user_id uuid not null references public.profiles (id) on delete cascade,
  achievement_id text not null references public.achievements (id),
  unlocked_at timestamptz not null default now(),
  primary key (user_id, achievement_id)
);

-- Productos de Google Play. Los ids deben coincidir con los de Play Console.
create table public.store_products (
  id text primary key,
  kind text not null check (kind in ('remove_ads', 'coins')),
  coins integer not null default 0 check (coins >= 0)
);

-- Compras verificadas. El token de compra es único: una compra no se acredita dos veces.
create table public.purchases (
  purchase_token text primary key,
  user_id uuid not null references public.profiles (id) on delete cascade,
  product_id text not null references public.store_products (id),
  order_id text,
  created_at timestamptz not null default now()
);

-- Videos recompensados confirmados por AdMob (verificación del lado del servidor).
create table public.rewarded_views (
  transaction_id text primary key,
  user_id uuid not null references public.profiles (id) on delete cascade,
  day date not null,
  credited boolean not null,
  created_at timestamptz not null default now()
);
create index rewarded_views_user_day on public.rewarded_views (user_id, day);

alter table public.wallets enable row level security;
alter table public.coin_ledger enable row level security;
alter table public.local_games enable row level security;
alter table public.cosmetics enable row level security;
alter table public.owned_cosmetics enable row level security;
alter table public.equipped_cosmetics enable row level security;
alter table public.achievements enable row level security;
alter table public.user_achievements enable row level security;
alter table public.store_products enable row level security;
alter table public.purchases enable row level security;
alter table public.rewarded_views enable row level security;

-- ------------------------------------------------------------------ Catálogos

insert into public.cosmetics (id, kind, name, unlock_level, price) values
  ('frame_bronze', 'FRAME', 'Marco Bronce', 2, null),
  ('sound_retro', 'ROULETTE_SOUND', 'Ruleta retro', 3, null),
  ('theme_neon_caribe', 'THEME', 'Neón Caribe', 5, null),
  ('title_sabelotodo', 'TITLE', 'Sabelotodo', 7, null),
  ('victory_confetti', 'VICTORY', 'Lluvia de confeti', 10, null),
  ('frame_costa', 'FRAME', 'Marco Costa', 15, null),
  ('frame_paisa', 'FRAME', 'Marco Paisa', 20, null),
  ('frame_llanero', 'FRAME', 'Marco Llanero', 25, null),
  ('frame_pacifico', 'FRAME', 'Marco Pacífico', 30, null),
  ('title_el_que_sabe', 'TITLE', 'El que sabe', null, 150),
  ('title_profe', 'TITLE', 'Profe', null, 150),
  ('sound_campana', 'ROULETTE_SOUND', 'Campana', null, 200),
  ('frame_oro', 'FRAME', 'Marco Oro', null, 300),
  ('theme_atardecer', 'THEME', 'Atardecer', null, 400),
  ('victory_fireworks', 'VICTORY', 'Fuegos artificiales', null, 600);

insert into public.achievements (id, name, coins) values
  ('first_local', 'Primera partida', 20),
  ('local_25', 'Fiestero', 100),
  ('regional_10', 'Experto en De aquí somos', 100),
  ('perfect_round', 'Ronda perfecta', 50),
  ('first_online_win', 'Primera victoria online', 50),
  ('online_wins_10', '10 victorias', 150),
  ('level_10', 'Nivel 10', 100),
  ('streak_7', 'Semana completa', 100);

insert into public.store_products (id, kind, coins) values
  ('remove_ads', 'remove_ads', 0),
  ('coins_300', 'coins', 300),
  ('coins_900', 'coins', 900),
  ('coins_2000', 'coins', 2000);

-- ------------------------------------------------------------------ Funciones internas

create function public._economy_day()
returns date
language sql
stable
set search_path = ''
as $$
  select (now() at time zone 'America/Bogota')::date;
$$;

-- Misma curva que core/profile/Levels.levelFor: cada nivel cuesta 100 XP más que el anterior.
create function public._level_for(p_xp integer)
returns integer
language plpgsql
immutable
set search_path = ''
as $$
declare
  lvl integer := 1;
  needed integer := 100;
  remaining integer := greatest(coalesce(p_xp, 0), 0);
begin
  while remaining >= needed loop
    remaining := remaining - needed;
    lvl := lvl + 1;
    needed := needed + 100;
  end loop;
  return lvl;
end;
$$;

create function public._ensure_wallet(p_user uuid)
returns void
language sql
security definer
set search_path = ''
as $$
  insert into public.wallets (user_id) values (p_user) on conflict (user_id) do nothing;
$$;

-- Suma o resta monedas. Devuelve false si ese movimiento único ya existía (no hace nada) o, en un
-- gasto, si no hay saldo suficiente (en ese caso lanza not_enough_coins).
create function public._move_coins(p_user uuid, p_delta integer, p_reason text, p_ref text)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
declare
  inserted integer;
begin
  perform public._ensure_wallet(p_user);
  if p_ref is not null and exists (
    select 1 from public.coin_ledger where user_id = p_user and reason = p_reason and ref = p_ref
  ) then
    return false;
  end if;
  if p_delta < 0 and (select coins from public.wallets where user_id = p_user for update) < -p_delta then
    raise exception 'not_enough_coins' using errcode = 'P0001';
  end if;
  insert into public.coin_ledger (user_id, delta, reason, ref, day)
  values (p_user, p_delta, p_reason, p_ref, public._economy_day())
  on conflict (user_id, reason, ref) do nothing;
  get diagnostics inserted = row_count;
  if inserted = 0 then
    return false;
  end if;
  update public.wallets set coins = coins + p_delta, updated_at = now() where user_id = p_user;
  return true;
end;
$$;

-- Monedas repetibles con tope diario: acredita [p_amount] por cada una de las [p_times] veces que
-- quepan en el tope de hoy. Devuelve las monedas acreditadas.
create function public._daily_capped(p_user uuid, p_reason text, p_amount integer, p_times integer, p_cap integer)
returns integer
language plpgsql
security definer
set search_path = ''
as $$
declare
  used integer;
  allowed integer;
begin
  select count(*) into used
    from public.coin_ledger
   where user_id = p_user and reason = p_reason and day = public._economy_day();
  allowed := least(greatest(p_times, 0), greatest(p_cap - used, 0));
  for i in 1..allowed loop
    perform public._move_coins(p_user, p_amount, p_reason, null);
  end loop;
  return allowed * p_amount;
end;
$$;

create function public._unlock(p_user uuid, p_achievement text)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
declare
  inserted integer;
begin
  insert into public.user_achievements (user_id, achievement_id) values (p_user, p_achievement)
  on conflict do nothing;
  get diagnostics inserted = row_count;
  if inserted = 0 then
    return false;
  end if;
  perform public._move_coins(p_user, (select coins from public.achievements where id = p_achievement), 'achievement', p_achievement);
  return true;
end;
$$;

-- Bonos por nivel y monedas de partidas online. Se dispara cuando el servidor actualiza las
-- estadísticas (al terminar una sala online o al registrar una partida local).
create function public._on_stats_change()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  old_level integer := public._level_for(old.xp + old.local_xp);
  new_level integer := public._level_for(new.xp + new.local_xp);
begin
  for lvl in (old_level + 1)..new_level loop
    perform public._move_coins(new.user_id, 50 + 10 * lvl, 'level', lvl::text);
  end loop;
  if new_level >= 10 then
    perform public._unlock(new.user_id, 'level_10');
  end if;
  if new.games_played > old.games_played then
    perform public._daily_capped(new.user_id, 'game', 5, new.games_played - old.games_played, 10);
  end if;
  if new.wins > old.wins then
    perform public._daily_capped(new.user_id, 'online_win', 10, new.wins - old.wins, 10);
    perform public._unlock(new.user_id, 'first_online_win');
    if new.wins >= 10 then
      perform public._unlock(new.user_id, 'online_wins_10');
    end if;
  end if;
  return new;
end;
$$;

create trigger on_player_stats_change
  after update on public.player_stats
  for each row execute function public._on_stats_change();

-- ------------------------------------------------------------------ Funciones para la app

-- Todo lo que la app necesita mostrar, en una sola llamada.
create function public.economy_state()
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  uid uuid := public._require_user();
  today date := public._economy_day();
  w public.wallets;
  total_xp integer;
  lvl integer;
  streak_next integer;
begin
  perform public._ensure_wallet(uid);
  select * into w from public.wallets where user_id = uid;
  select coalesce(s.xp + s.local_xp, 0) into total_xp from public.player_stats s where s.user_id = uid;
  lvl := public._level_for(coalesce(total_xp, 0));
  streak_next := case when w.last_daily = today - 1 then w.streak_day + 1 else 1 end;
  return jsonb_build_object(
    'coins', w.coins,
    'remove_ads', w.remove_ads,
    'xp', coalesce(total_xp, 0),
    'level', lvl,
    'owned', coalesce((
      select jsonb_agg(c.id order by c.id) from public.cosmetics c
       where c.unlock_level <= lvl
          or exists (select 1 from public.owned_cosmetics o where o.user_id = uid and o.cosmetic_id = c.id)
    ), '[]'::jsonb),
    'equipped', coalesce((
      select jsonb_object_agg(e.kind, e.cosmetic_id) from public.equipped_cosmetics e where e.user_id = uid
    ), '{}'::jsonb),
    'achievements', coalesce((
      select jsonb_agg(a.achievement_id order by a.unlocked_at) from public.user_achievements a where a.user_id = uid
    ), '[]'::jsonb),
    'streak_day', case when w.last_daily >= today - 1 then w.streak_day else 0 end,
    'daily_claimed', coalesce(w.last_daily = today, false),
    'next_streak_day', streak_next,
    'videos_left', greatest(5 - (
      select count(*) from public.rewarded_views v where v.user_id = uid and v.day = today and v.credited
    ), 0),
    'local_xp_games_left', greatest(5 - (
      select count(*) from public.local_games g where g.user_id = uid and g.day = today and g.xp > 0
    ), 0)
  );
end;
$$;

-- Partida local terminada en el teléfono del dueño de la cuenta. Idempotente por p_game_key.
create function public.record_local_game(p_game_key text, p_regional boolean, p_perfect_round boolean)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  uid uuid := public._require_user();
  today date := public._economy_day();
  gained_xp integer := 0;
  gained_coins integer := 0;
  inserted integer;
  coins_before integer;
begin
  if p_game_key is null or char_length(p_game_key) not between 8 and 64 then
    raise exception 'invalid_game_key' using errcode = '22023';
  end if;
  perform public._ensure_wallet(uid);
  select coins into coins_before from public.wallets where user_id = uid;

  if (select count(*) from public.local_games where user_id = uid and day = today and xp > 0) < 5 then
    gained_xp := 10;
  end if;
  insert into public.local_games (user_id, game_key, day, regional, perfect_round, xp)
  values (uid, p_game_key, today, coalesce(p_regional, false), coalesce(p_perfect_round, false), gained_xp)
  on conflict do nothing;
  get diagnostics inserted = row_count;
  if inserted = 0 then
    return jsonb_build_object('xp', 0, 'coins', 0, 'duplicate', true);
  end if;

  if gained_xp > 0 then
    -- El disparador de player_stats da el bono si se sube de nivel.
    update public.player_stats set local_xp = local_xp + gained_xp, updated_at = now() where user_id = uid;
  end if;
  perform public._daily_capped(uid, 'game', 5, 1, 10);

  perform public._unlock(uid, 'first_local');
  if (select count(*) from public.local_games where user_id = uid) >= 25 then
    perform public._unlock(uid, 'local_25');
  end if;
  if (select count(*) from public.local_games where user_id = uid and regional) >= 10 then
    perform public._unlock(uid, 'regional_10');
  end if;
  if p_perfect_round then
    perform public._unlock(uid, 'perfect_round');
  end if;

  select coins - coins_before into gained_coins from public.wallets where user_id = uid;
  return jsonb_build_object('xp', gained_xp, 'coins', gained_coins, 'duplicate', false);
end;
$$;

-- Racha diaria: una vez por día; seguidos suben el premio, si se rompe vuelve al día 1 sin perder nada.
create function public.claim_daily()
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  uid uuid := public._require_user();
  today date := public._economy_day();
  w public.wallets;
  streak integer;
  rewards integer[] := array[10, 15, 20, 30, 40, 60, 100];
  reward integer;
begin
  perform public._ensure_wallet(uid);
  select * into w from public.wallets where user_id = uid for update;
  if w.last_daily = today then
    raise exception 'already_claimed' using errcode = 'P0001';
  end if;
  streak := case when w.last_daily = today - 1 then w.streak_day + 1 else 1 end;
  reward := rewards[least(streak, array_length(rewards, 1))];
  update public.wallets set streak_day = streak, last_daily = today, updated_at = now() where user_id = uid;
  perform public._move_coins(uid, reward, 'daily', today::text);
  if streak >= 7 then
    perform public._unlock(uid, 'streak_7');
  end if;
  return jsonb_build_object('coins', reward, 'streak_day', streak);
end;
$$;

-- Paquete de partida: lo paga el dueño y da un comodín más a todos los jugadores. Uno por partida.
create function public.buy_match_pack(p_game_key text)
returns integer
language plpgsql
security definer
set search_path = ''
as $$
declare
  uid uuid := public._require_user();
begin
  if p_game_key is null or char_length(p_game_key) not between 8 and 64 then
    raise exception 'invalid_game_key' using errcode = '22023';
  end if;
  if not public._move_coins(uid, -60, 'match_pack', p_game_key) then
    raise exception 'already_bought' using errcode = 'P0001';
  end if;
  return (select coins from public.wallets where user_id = uid);
end;
$$;

create function public.buy_cosmetic(p_id text)
returns integer
language plpgsql
security definer
set search_path = ''
as $$
declare
  uid uuid := public._require_user();
  item public.cosmetics;
begin
  select * into item from public.cosmetics where id = p_id;
  if item.id is null or item.price is null then
    raise exception 'not_for_sale' using errcode = '22023';
  end if;
  if exists (select 1 from public.owned_cosmetics where user_id = uid and cosmetic_id = p_id) then
    raise exception 'already_owned' using errcode = 'P0001';
  end if;
  perform public._move_coins(uid, -item.price, 'cosmetic', p_id);
  insert into public.owned_cosmetics (user_id, cosmetic_id) values (uid, p_id);
  return (select coins from public.wallets where user_id = uid);
end;
$$;

-- p_id null quita lo equipado de ese tipo.
create function public.equip_cosmetic(p_kind text, p_id text)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  uid uuid := public._require_user();
  item public.cosmetics;
  lvl integer;
begin
  if p_id is null then
    delete from public.equipped_cosmetics where user_id = uid and kind = p_kind;
    return;
  end if;
  select * into item from public.cosmetics where id = p_id;
  if item.id is null or item.kind <> p_kind then
    raise exception 'invalid_cosmetic' using errcode = '22023';
  end if;
  select public._level_for(s.xp + s.local_xp) into lvl from public.player_stats s where s.user_id = uid;
  if not (coalesce(item.unlock_level <= lvl, false)
          or exists (select 1 from public.owned_cosmetics where user_id = uid and cosmetic_id = p_id)) then
    raise exception 'not_owned' using errcode = 'P0001';
  end if;
  insert into public.equipped_cosmetics (user_id, kind, cosmetic_id) values (uid, p_kind, p_id)
  on conflict (user_id, kind) do update set cosmetic_id = excluded.cosmetic_id;
end;
$$;

-- ------------------------------------------------------------------ Solo para el servidor

-- La llama la función admob-ssv tras verificar la firma de AdMob. Como mucho 5 videos pagados al
-- día; cada transacción se registra una vez.
create function public.grant_rewarded_view(p_user uuid, p_transaction text)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
declare
  today date := public._economy_day();
  credit boolean;
  inserted integer;
begin
  if not exists (select 1 from public.profiles where id = p_user) then
    return false;
  end if;
  credit := (select count(*) from public.rewarded_views where user_id = p_user and day = today and credited) < 5;
  insert into public.rewarded_views (transaction_id, user_id, day, credited)
  values (p_transaction, p_user, today, credit)
  on conflict do nothing;
  get diagnostics inserted = row_count;
  if inserted = 0 or not credit then
    return false;
  end if;
  perform public._move_coins(p_user, 20, 'video', p_transaction);
  return true;
end;
$$;

-- La llama la función verify-purchase tras comprobar la compra con la API de Google Play.
-- Idempotente: la misma compra del mismo usuario devuelve ok sin acreditar dos veces.
create function public.record_purchase(p_user uuid, p_product text, p_token text, p_order text)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  product public.store_products;
  owner uuid;
begin
  select * into product from public.store_products where id = p_product;
  if product.id is null then
    raise exception 'unknown_product' using errcode = '22023';
  end if;
  select user_id into owner from public.purchases where purchase_token = p_token;
  if owner is not null then
    if owner <> p_user then
      raise exception 'purchase_belongs_to_other_user' using errcode = 'P0001';
    end if;
    return jsonb_build_object('ok', true, 'duplicate', true);
  end if;
  insert into public.purchases (purchase_token, user_id, product_id, order_id) values (p_token, p_user, p_product, p_order);
  if product.kind = 'coins' then
    perform public._move_coins(p_user, product.coins, 'purchase', p_token);
  else
    perform public._ensure_wallet(p_user);
    update public.wallets set remove_ads = true, updated_at = now() where user_id = p_user;
  end if;
  return jsonb_build_object('ok', true, 'duplicate', false, 'coins', product.coins);
end;
$$;

-- ------------------------------------------------------------------ Permisos

revoke all on function public._economy_day() from public, anon, authenticated;
revoke all on function public._level_for(integer) from public, anon, authenticated;
revoke all on function public._ensure_wallet(uuid) from public, anon, authenticated;
revoke all on function public._move_coins(uuid, integer, text, text) from public, anon, authenticated;
revoke all on function public._daily_capped(uuid, text, integer, integer, integer) from public, anon, authenticated;
revoke all on function public._unlock(uuid, text) from public, anon, authenticated;
revoke all on function public._on_stats_change() from public, anon, authenticated;

revoke all on function public.economy_state() from public, anon;
revoke all on function public.record_local_game(text, boolean, boolean) from public, anon;
revoke all on function public.claim_daily() from public, anon;
revoke all on function public.buy_match_pack(text) from public, anon;
revoke all on function public.buy_cosmetic(text) from public, anon;
revoke all on function public.equip_cosmetic(text, text) from public, anon;
grant execute on function public.economy_state() to authenticated;
grant execute on function public.record_local_game(text, boolean, boolean) to authenticated;
grant execute on function public.claim_daily() to authenticated;
grant execute on function public.buy_match_pack(text) to authenticated;
grant execute on function public.buy_cosmetic(text) to authenticated;
grant execute on function public.equip_cosmetic(text, text) to authenticated;

-- Solo la clave de servicio (funciones del servidor), nunca la app.
revoke all on function public.grant_rewarded_view(uuid, text) from public, anon, authenticated;
revoke all on function public.record_purchase(uuid, text, text, text) from public, anon, authenticated;
grant execute on function public.grant_rewarded_view(uuid, text) to service_role;
grant execute on function public.record_purchase(uuid, text, text, text) to service_role;

-- Monedero para los usuarios que ya existían.
insert into public.wallets (user_id) select id from public.profiles on conflict do nothing;
