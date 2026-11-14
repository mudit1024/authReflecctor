-- V7: how often each user signs in to each app, for the launcher (glowing = used, sorted by use).
-- One row per user, app and day: "sign-ins in the last 30 days" is a cheap, exact sum, and old
-- history can't dominate the order. Recorded by AuthReflector itself when an app receives tokens
-- for a sign-in (the apps report nothing).
create table user_app_daily_usage (
    user_id      uuid         not null references users (id) on delete cascade,
    client_id    varchar(100) not null references oauth2_registered_client (client_id) on delete cascade,
    day          date         not null,
    sign_ins     integer      not null default 0,
    last_sign_in timestamptz  not null,

    primary key (user_id, client_id, day)
);

create index user_app_daily_usage_user_idx on user_app_daily_usage (user_id, day);
