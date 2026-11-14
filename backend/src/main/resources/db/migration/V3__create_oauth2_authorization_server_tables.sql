-- V3: Spring Authorization Server storage (schemas shipped in spring-security-oauth2-authorization-server 7.1.1),
-- adapted for PostgreSQL as the shipped files instruct: blob -> text, timestamp -> timestamptz.
--
-- Registered client apps live here (not in a config file), so adding an app never needs a restart:
-- the authorization server reads this table on every request (owner decision, 14 Nov 2026).

-- Client apps that may use AuthReflector (SplitMate, CodeQuror, ...). Secrets are stored as {bcrypt} hashes.
create table oauth2_registered_client (
    id                            varchar(100)  not null,
    client_id                     varchar(100)  not null,
    client_id_issued_at           timestamptz   default current_timestamp not null,
    client_secret                 varchar(200)  default null,
    client_secret_expires_at      timestamptz   default null,
    client_name                   varchar(200)  not null,
    client_authentication_methods varchar(1000) not null,
    authorization_grant_types     varchar(1000) not null,
    redirect_uris                 varchar(1000) default null,
    post_logout_redirect_uris     varchar(1000) default null,
    scopes                        varchar(1000) not null,
    client_settings               varchar(2000) not null,
    token_settings                varchar(2000) not null,
    primary key (id)
);

-- client_id is what apps send; it must identify exactly one app
create unique index oauth2_registered_client_client_id_uk on oauth2_registered_client (client_id);

-- One row per sign-in of a user into an app: the authorization code, then the access/ID/refresh tokens
create table oauth2_authorization (
    id                            varchar(100)  not null,
    registered_client_id          varchar(100)  not null,
    principal_name                varchar(200)  not null,
    authorization_grant_type      varchar(100)  not null,
    authorized_scopes             varchar(1000) default null,
    attributes                    text          default null,
    state                         varchar(500)  default null,
    authorization_code_value      text          default null,
    authorization_code_issued_at  timestamptz   default null,
    authorization_code_expires_at timestamptz   default null,
    authorization_code_metadata   text          default null,
    access_token_value            text          default null,
    access_token_issued_at        timestamptz   default null,
    access_token_expires_at       timestamptz   default null,
    access_token_metadata         text          default null,
    access_token_type             varchar(100)  default null,
    access_token_scopes           varchar(1000) default null,
    oidc_id_token_value           text          default null,
    oidc_id_token_issued_at       timestamptz   default null,
    oidc_id_token_expires_at      timestamptz   default null,
    oidc_id_token_metadata        text          default null,
    refresh_token_value           text          default null,
    refresh_token_issued_at       timestamptz   default null,
    refresh_token_expires_at      timestamptz   default null,
    refresh_token_metadata        text          default null,
    user_code_value               text          default null,
    user_code_issued_at           timestamptz   default null,
    user_code_expires_at          timestamptz   default null,
    user_code_metadata            text          default null,
    device_code_value             text          default null,
    device_code_issued_at         timestamptz   default null,
    device_code_expires_at        timestamptz   default null,
    device_code_metadata          text          default null,
    primary key (id)
);

-- Lookups by token value (token endpoint, refresh, revoke, userinfo) must not scan the table
create index oauth2_authorization_code_idx    on oauth2_authorization using hash (authorization_code_value);
create index oauth2_authorization_access_idx  on oauth2_authorization using hash (access_token_value);
create index oauth2_authorization_refresh_idx on oauth2_authorization using hash (refresh_token_value);
create index oauth2_authorization_id_token_idx on oauth2_authorization using hash (oidc_id_token_value);
create index oauth2_authorization_state_idx   on oauth2_authorization (state);

-- "User X allowed app Y to access scopes Z". Our own apps skip the consent screen, so mostly empty.
create table oauth2_authorization_consent (
    registered_client_id varchar(100)  not null,
    principal_name       varchar(200)  not null,
    authorities          varchar(1000) not null,
    primary key (registered_client_id, principal_name)
);
