-- Who performed an action (an admin), separate from user_id (the account it is about).
-- Null for self-service events such as login.
alter table auth_events add column actor_id uuid references users (id);
