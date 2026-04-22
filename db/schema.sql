CREATE TABLE IF NOT EXISTS "schema_migrations" (version varchar(128) primary key);
CREATE TABLE bracket_pair (
    id         INTEGER  PRIMARY KEY,
    open_char  CHAR(1)  NOT NULL,
    close_char CHAR(1)  NOT NULL,
    enabled    BOOLEAN  NOT NULL DEFAULT TRUE
);
-- Dbmate schema migrations
INSERT INTO "schema_migrations" (version) VALUES
  ('20260421000000');
