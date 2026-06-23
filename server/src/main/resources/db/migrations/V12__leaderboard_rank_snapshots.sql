CREATE TABLE leaderboard_rank_snapshots (
    snapshot_date DATE NOT NULL,
    user_id       UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    rank          INT  NOT NULL,
    spot_score    INT  NOT NULL,
    PRIMARY KEY (snapshot_date, user_id)
);

CREATE INDEX idx_rank_snapshots_user_date ON leaderboard_rank_snapshots (user_id, snapshot_date DESC);
