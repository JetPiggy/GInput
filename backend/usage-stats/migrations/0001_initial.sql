-- 使用統計只存每日的合計數字：沒有任何識別碼、不存 IP，也不保存報告本身（ADR 0001）。
-- reports 是報告數，即當日有多少部裝置送了報告。

CREATE TABLE daily_devices (
  day TEXT NOT NULL,
  app TEXT NOT NULL,
  sdk INTEGER NOT NULL,
  reports INTEGER NOT NULL,
  PRIMARY KEY (day, app, sdk)
);

CREATE TABLE daily_settings (
  day TEXT NOT NULL,
  setting TEXT NOT NULL,
  value TEXT NOT NULL,
  reports INTEGER NOT NULL,
  PRIMARY KEY (day, setting, value)
);

-- reports：當日用過這個功能的裝置數；total：當日的總次數。
CREATE TABLE daily_counts (
  day TEXT NOT NULL,
  name TEXT NOT NULL,
  reports INTEGER NOT NULL,
  total INTEGER NOT NULL,
  PRIMARY KEY (day, name)
);
