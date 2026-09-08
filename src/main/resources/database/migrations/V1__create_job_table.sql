create table job(
     id uuid primary key,
     type text not null,
     payload jsonb not null default '{}',
     status text not null default 'PENDING',
     current_attempts int not null default 0,
     max_attempts int not null default 5,
     run_after timestamptz not null default now(),
     created_at   timestamptz not null default now(),
     updated_at   timestamptz not null default now()
);

CREATE INDEX idx_job_runnable ON job (run_after) WHERE status = 'PENDING';