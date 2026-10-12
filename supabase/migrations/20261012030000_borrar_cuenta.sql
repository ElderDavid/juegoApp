-- Borrado de cuenta desde la app (exigido por Google Play para apps que crean cuentas).
-- Borra el usuario de auth.users; el perfil, las estadísticas y las salas terminadas que creó se
-- borran en cascada. Las respuestas que dio en salas de otros jugadores también (on delete cascade).

create or replace function public.delete_my_account()
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  uid uuid := auth.uid();
begin
  if uid is null then
    raise exception 'not_authenticated';
  end if;
  -- No se borra en medio de una partida: rompería la sala de los demás jugadores.
  if exists (
    select 1
      from public.room_players rp
      join public.rooms r on r.id = rp.room_id
     where rp.user_id = uid
       and rp.left_at is null
       and r.status in ('waiting', 'ready', 'playing')
  ) then
    raise exception 'in_active_room';
  end if;
  delete from auth.users where id = uid;
end;
$$;

revoke all on function public.delete_my_account() from public, anon;
grant execute on function public.delete_my_account() to authenticated;
