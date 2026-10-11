-- 每日（UTC）的請求數和用了的 neurons 合計，用來設每日上限和看成本。
-- 沒有原文、結果、IP 或任何識別碼（ADR 0005、0006）。

CREATE TABLE daily_usage (
  day TEXT PRIMARY KEY,
  requests INTEGER NOT NULL,
  neurons REAL NOT NULL
);
