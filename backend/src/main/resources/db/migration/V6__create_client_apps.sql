-- V6: what AuthReflector knows about each registered app beyond the OAuth settings:
-- its type, where to open it from the launcher, how it looks there, and whether it is enabled.
-- Kept apart from Spring's oauth2_registered_client table so framework upgrades never touch it.
create table client_apps (
    client_id        varchar(100) primary key
                     references oauth2_registered_client (client_id) on delete cascade,
    -- WEB = has a backend (confidential client + secret, Spring starter);
    -- SPA = frontend-only (public client, PKCE, JS SDK)
    app_type         varchar(10)  not null,
    home_url         varchar(500),
    description      varchar(300),
    color            varchar(7),
    show_in_launcher boolean      not null default true,
    -- Disabled apps can't sign anyone in (enforced on every authorization request)
    enabled          boolean      not null default true,
    created_at       timestamptz  not null default now(),
    updated_at       timestamptz  not null default now(),

    constraint client_apps_type_check  check (app_type in ('WEB', 'SPA')),
    constraint client_apps_color_check check (color is null or color ~ '^#[0-9a-fA-F]{6}$')
);
