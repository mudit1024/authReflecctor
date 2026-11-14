-- V5: refresh token reuse detection (SplitMate decision D37).
-- Every refresh issues a new refresh token. The replaced one is remembered here, only as a SHA-256 hash.
-- If a replaced token is ever presented again, a copy is being replayed (stolen token): the whole
-- authorization is revoked, so both the thief and the real user must sign in again.
create table rotated_refresh_tokens (
    token_hash       char(64)     primary key,  -- hex SHA-256 of the replaced refresh token
    authorization_id varchar(100) not null references oauth2_authorization (id) on delete cascade,
    rotated_at       timestamptz  not null default now()
);

create index rotated_refresh_tokens_authorization_idx on rotated_refresh_tokens (authorization_id);
