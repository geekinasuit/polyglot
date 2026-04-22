-- migrate:up
CREATE TABLE bracket_pair (
    id         INTEGER  PRIMARY KEY,
    open_char  CHAR(1)  NOT NULL,
    close_char CHAR(1)  NOT NULL,
    enabled    BOOLEAN  NOT NULL DEFAULT TRUE
);

INSERT INTO bracket_pair (id, open_char, close_char) VALUES (1, '(', ')');
INSERT INTO bracket_pair (id, open_char, close_char) VALUES (2, '[', ']');
INSERT INTO bracket_pair (id, open_char, close_char) VALUES (3, '{', '}');

-- migrate:down
