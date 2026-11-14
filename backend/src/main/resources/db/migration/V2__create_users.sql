-- V2: user accounts (identity). Ported from SplitMate's users table, with database-level guarantees.
-- "user" is a reserved word in PostgreSQL, hence "users".
create table users (
    id            uuid         primary key,
    name          varchar(100) not null,
    -- 254 = practical maximum length of an email address (RFC 5321); always stored lowercased
    email         varchar(254) not null,
    -- Algorithm-prefixed hash, e.g. "{bcrypt}$2a$10$...". Never plaintext.
    password_hash varchar(255) not null,
    -- Platform role (decision D42). Per-app roles get their own table in A4.
    role          varchar(20)  not null default 'USER',
    created_at    timestamptz  not null default now(),
    updated_at    timestamptz  not null default now(),

    constraint users_role_check check (role in ('USER', 'ADMIN'))
);

-- Case-insensitive uniqueness enforced by the database itself, not only by the service:
-- even code that skips the normalisation can't create "a@x.com" next to "A@x.com".
create unique index users_email_lower_uk on users (lower(email));
