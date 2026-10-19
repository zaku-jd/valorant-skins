CREATE TABLE rotations (
    account TEXT NOT NULL,
    rotation INTEGER NOT NULL,
    checked INTEGER NOT NULL,
    ends INTEGER NOT NULL,
    payload TEXT NOT NULL,
    PRIMARY KEY (account, rotation)
)
