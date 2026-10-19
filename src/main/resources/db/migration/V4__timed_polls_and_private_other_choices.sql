ALTER TABLE reader_polls ADD COLUMN ends_at TIMESTAMP(6) WITH TIME ZONE;
ALTER TABLE reader_polls ADD COLUMN other_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE poll_votes ADD COLUMN is_other BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE poll_votes ADD COLUMN other_text VARCHAR(300);
CREATE INDEX idx_poll_other ON poll_votes(poll_id,is_other,id DESC);
