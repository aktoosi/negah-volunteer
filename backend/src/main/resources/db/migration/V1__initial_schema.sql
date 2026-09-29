CREATE TABLE teams (
 id UUID PRIMARY KEY, name VARCHAR(100) NOT NULL UNIQUE,
 coordinator_email VARCHAR(254) NOT NULL
);
CREATE TABLE app_users (
 id UUID PRIMARY KEY, username VARCHAR(80) NOT NULL UNIQUE,
 password_hash VARCHAR(100) NOT NULL, full_name VARCHAR(100) NOT NULL,
 personnel_code VARCHAR(40) NOT NULL UNIQUE,
 role VARCHAR(20) NOT NULL CHECK(role IN ('ADMIN','MANAGER','EMPLOYEE')),
 team_id UUID NOT NULL REFERENCES teams(id), active BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE TABLE attendance_weeks (
 id UUID PRIMARY KEY, team_id UUID NOT NULL REFERENCES teams(id), thursday DATE NOT NULL,
 status VARCHAR(20) NOT NULL CHECK(status IN ('OPEN','FINALIZED')),
 finalized_by UUID REFERENCES app_users(id), finalized_at TIMESTAMP WITH TIME ZONE,
 UNIQUE(team_id, thursday)
);
CREATE TABLE attendance_requests (
 id UUID PRIMARY KEY, week_id UUID NOT NULL REFERENCES attendance_weeks(id),
 user_id UUID NOT NULL REFERENCES app_users(id),
 thursday_status VARCHAR(20) NOT NULL CHECK(thursday_status IN ('PENDING','APPROVED','REJECTED','NOT_ATTENDING')),
 friday_status VARCHAR(20) NOT NULL CHECK(friday_status IN ('PENDING','APPROVED','REJECTED','NOT_ATTENDING')),
 note VARCHAR(500) NOT NULL DEFAULT '', review_note VARCHAR(500) NOT NULL DEFAULT '',
 reviewed_by UUID REFERENCES app_users(id), reviewed_at TIMESTAMP WITH TIME ZONE,
 updated_at TIMESTAMP WITH TIME ZONE NOT NULL, UNIQUE(week_id,user_id)
);
CREATE TABLE email_outbox (
 id UUID PRIMARY KEY, week_id UUID NOT NULL UNIQUE REFERENCES attendance_weeks(id),
 recipient VARCHAR(254) NOT NULL, subject VARCHAR(200) NOT NULL, body TEXT NOT NULL,
 status VARCHAR(20) NOT NULL CHECK(status IN ('QUEUED','SENT','FAILED')),
 attempts INTEGER NOT NULL DEFAULT 0, last_error VARCHAR(300), sent_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX requests_week_idx ON attendance_requests(week_id);
CREATE INDEX users_team_idx ON app_users(team_id);
CREATE INDEX mail_status_idx ON email_outbox(status);
