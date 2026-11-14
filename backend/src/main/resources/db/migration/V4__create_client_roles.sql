-- V4: roles per app. Being ADMIN in SplitMate must not make you ADMIN in CodeQuror.
-- Every signed-in user is USER in every app without a row here; rows grant extra roles.
-- Granted only by a database script for now (SplitMate decision D42); admin UI later.
create table client_roles (
    user_id    uuid         not null references users (id) on delete cascade,
    -- oauth2_registered_client.client_id (e.g. 'splitmate-web')
    client_id  varchar(100) not null,
    role       varchar(50)  not null,
    granted_at timestamptz  not null default now(),

    primary key (user_id, client_id, role),
    constraint client_roles_role_format check (role ~ '^[A-Z][A-Z0-9_]*$')
);
