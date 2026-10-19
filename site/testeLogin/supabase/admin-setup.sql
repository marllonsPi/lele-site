-- Execute este arquivo uma única vez no SQL Editor do Supabase.

create table if not exists public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  email text,
  full_name text,
  role text not null default 'client' check (role in ('client', 'admin')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

alter table public.profiles enable row level security;

create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  insert into public.profiles (id, email, full_name, role)
  values (
    new.id,
    new.email,
    coalesce(new.raw_user_meta_data ->> 'full_name', new.raw_user_meta_data ->> 'name', ''),
    'client'
  )
  on conflict (id) do update
    set email = excluded.email,
        full_name = case
          when public.profiles.full_name is null or public.profiles.full_name = ''
            then excluded.full_name
          else public.profiles.full_name
        end,
        updated_at = now();
  return new;
end;
$$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
  after insert or update of email, raw_user_meta_data on auth.users
  for each row execute procedure public.handle_new_user();

insert into public.profiles (id, email, full_name, role)
select
  id,
  email,
  coalesce(raw_user_meta_data ->> 'full_name', raw_user_meta_data ->> 'name', ''),
  'client'
from auth.users
on conflict (id) do update
  set email = excluded.email,
      full_name = case
        when public.profiles.full_name is null or public.profiles.full_name = ''
          then excluded.full_name
        else public.profiles.full_name
      end,
      updated_at = now();

create or replace function public.is_admin()
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1
    from public.profiles
    where id = (select auth.uid())
      and role = 'admin'
  );
$$;

revoke all on function public.is_admin() from public;
grant execute on function public.is_admin() to authenticated;

drop policy if exists "Usuário consulta o próprio perfil" on public.profiles;
drop policy if exists "Administradores consultam perfis" on public.profiles;
drop policy if exists "Administradores atualizam perfis" on public.profiles;

create policy "Usuário consulta o próprio perfil"
on public.profiles
for select
to authenticated
using (id = (select auth.uid()));

create policy "Administradores consultam perfis"
on public.profiles
for select
to authenticated
using ((select public.is_admin()));

create policy "Administradores atualizam perfis"
on public.profiles
for update
to authenticated
using ((select public.is_admin()))
with check ((select public.is_admin()));

drop policy if exists "Administradores consultam todos os casos" on public.cases;
drop policy if exists "Administradores criam casos" on public.cases;
drop policy if exists "Administradores atualizam casos" on public.cases;
drop policy if exists "Administradores excluem casos" on public.cases;

create policy "Administradores consultam todos os casos"
on public.cases
for select
to authenticated
using ((select public.is_admin()));

create policy "Administradores criam casos"
on public.cases
for insert
to authenticated
with check ((select public.is_admin()));

create policy "Administradores atualizam casos"
on public.cases
for update
to authenticated
using ((select public.is_admin()))
with check ((select public.is_admin()));

create policy "Administradores excluem casos"
on public.cases
for delete
to authenticated
using ((select public.is_admin()));

drop policy if exists "Administradores consultam todas as atualizações" on public.case_updates;
drop policy if exists "Administradores criam atualizações" on public.case_updates;
drop policy if exists "Administradores atualizam atualizações" on public.case_updates;
drop policy if exists "Administradores excluem atualizações" on public.case_updates;

create policy "Administradores consultam todas as atualizações"
on public.case_updates
for select
to authenticated
using ((select public.is_admin()));

create policy "Administradores criam atualizações"
on public.case_updates
for insert
to authenticated
with check ((select public.is_admin()));

create policy "Administradores atualizam atualizações"
on public.case_updates
for update
to authenticated
using ((select public.is_admin()))
with check ((select public.is_admin()));

create policy "Administradores excluem atualizações"
on public.case_updates
for delete
to authenticated
using ((select public.is_admin()));

grant select, update on public.profiles to authenticated;
grant select, insert, update, delete on public.cases to authenticated;
grant select, insert, update, delete on public.case_updates to authenticated;

create index if not exists profiles_role_idx on public.profiles(role);

-- Administradoras de teste
update public.profiles
set
  email = 'leticia@teste.com',
  full_name = 'Letícia Crispim Mello',
  role = 'admin',
  updated_at = now()
where id = 'ca6876bc-b8f1-4e39-ac27-6e2b28524b6c';

update public.profiles
set
  email = 'lorraynny@teste.com',
  full_name = 'Lorraynny Campos',
  role = 'admin',
  updated_at = now()
where id = '088582c1-7489-46d4-9572-1eb4ec41a82c';

select id, email, full_name, role
from public.profiles
where id in (
  'ca6876bc-b8f1-4e39-ac27-6e2b28524b6c',
  '088582c1-7489-46d4-9572-1eb4ec41a82c'
)
order by email;
