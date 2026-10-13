-- Tienda: dos temas de color, una animación de victoria y modos de juego que se compran con monedas
-- («Muy rápido» y el modo países). Los modos son filas de public.cosmetics con kind 'MODE': se
-- compran con buy_cosmetic como el resto y aparecen en «owned» de economy_state, pero no se equipan.
-- El catálogo debe coincidir con core/economy/Economy.kt (lo comprueba EconomyCatalogTest).

alter table public.cosmetics drop constraint cosmetics_kind_check;
alter table public.cosmetics add constraint cosmetics_kind_check
  check (kind in ('FRAME', 'TITLE', 'THEME', 'ROULETTE_SOUND', 'VICTORY', 'MODE'));

insert into public.cosmetics (id, kind, name, unlock_level, price) values
  ('theme_selva', 'THEME', 'Selva', null, 400),
  ('theme_galaxia', 'THEME', 'Galaxia', null, 400),
  ('victory_trumpets', 'VICTORY', 'Fanfarria de trompetas', null, 500),
  ('mode_muy_rapido', 'MODE', 'Muy rápido', null, 300),
  ('mode_pais_colombia', 'MODE', 'Países: Colombia', null, 250),
  ('mode_pais_mexico', 'MODE', 'Países: México', null, 250),
  ('mode_pais_argentina', 'MODE', 'Países: Argentina', null, 250),
  ('mode_pais_brasil', 'MODE', 'Países: Brasil', null, 250),
  ('mode_pais_estados_unidos', 'MODE', 'Países: Estados Unidos', null, 250),
  ('mode_pais_espana', 'MODE', 'Países: España', null, 250)
on conflict (id) do nothing;

-- Igual que antes, pero los modos no se equipan: con tenerlos basta.
create or replace function public.equip_cosmetic(p_kind text, p_id text)
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
  if p_kind = 'MODE' then
    raise exception 'invalid_cosmetic' using errcode = '22023';
  end if;
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
