import assert from "node:assert/strict";
import { test } from "node:test";
import { parseReport, upserts } from "../src/report.ts";

const now = new Date("2026-10-10T08:00:00Z");

function report(overrides: Record<string, unknown> = {}): Record<string, unknown> {
  return {
    schema: 1,
    day: "2026-10-09",
    app: "0.6.0",
    sdk: 34,
    settings: { schemes_enabled: "quick", space_mode: "english", number_row: false },
    counts: { chinese: 12, emoji: 0, englishSuggestion: 3 },
    ...overrides,
  };
}

test("a report from the app is accepted", () => {
  assert.deepEqual(parseReport(report(), now), {
    day: "2026-10-09",
    app: "0.6.0",
    sdk: 34,
    settings: { schemes_enabled: "quick", space_mode: "english", number_row: false },
    counts: { chinese: 12, englishSuggestion: 3 },
  });
});

test("any order of enabled schemes is accepted, each scheme at most once", () => {
  for (const schemes of ["jyutping", "cangjie,jyutping", "jyutping,quick,cangjie"]) {
    const parsed = parseReport(report({ settings: { schemes_enabled: schemes, loose_spelling: true } }), now);
    assert.deepEqual(parsed?.settings, { schemes_enabled: schemes, loose_spelling: true }, schemes);
  }
  for (const schemes of ["", "quick,quick", "jyutping,pinyin", "quick,cangjie,jyutping,quick"]) {
    assert.equal(parseReport(report({ settings: { schemes_enabled: schemes } }), now), null, schemes);
  }
});

test("the English-Chinese dictionary is a setting and its translations a count apart from cloud translation", () => {
  const parsed = parseReport(
    report({ settings: { english_chinese: false }, counts: { translationCandidate: 2, translate: 1 } }),
    now,
  );
  assert.deepEqual(parsed?.settings, { english_chinese: false });
  assert.deepEqual(parsed?.counts, { translationCandidate: 2, translate: 1 });
  assert.equal(parseReport(report({ settings: { english_chinese: "on" } }), now), null);
});

test("unknown settings and counts from newer apps are ignored", () => {
  const parsed = parseReport(
    report({ settings: { theme: "dark", space_mode: "chinese" }, counts: { voice: 2, chinese: 1 } }),
    now,
  );
  assert.deepEqual(parsed?.settings, { space_mode: "chinese" });
  assert.deepEqual(parsed?.counts, { chinese: 1 });
});

test("reports with wrong values are rejected", () => {
  const rejected = [
    report({ schema: 2 }),
    report({ day: "2026-09-30" }),
    report({ day: "2026-10-12" }),
    report({ day: "2026-02-30" }),
    report({ app: "0.6.0-debug" }),
    report({ sdk: 27 }),
    report({ sdk: 34.5 }),
    report({ settings: { space_mode: "sideways" } }),
    report({ settings: { number_row: "yes" } }),
    report({ settings: { key_vibration: "buzz" } }),
    report({ counts: { chinese: -1 } }),
    report({ counts: { chinese: 1.5 } }),
    report({ counts: { chinese: 2_000_000 } }),
    report({ counts: [] }),
    "not json object",
    null,
  ];
  for (const body of rejected) assert.equal(parseReport(body, now), null, JSON.stringify(body));
});

test("the phone's date may be one day ahead of UTC", () => {
  assert.notEqual(parseReport(report({ day: "2026-10-11" }), now), null);
  assert.notEqual(parseReport(report({ day: "2026-10-02" }), now), null);
});

test("only totals are written", () => {
  const parsed = parseReport(report(), now);
  assert.ok(parsed);
  const statements = upserts(parsed);
  assert.equal(statements.length, 1 + 3 + 2);
  assert.deepEqual(statements[0].params, ["2026-10-09", "0.6.0", 34]);
  assert.deepEqual(
    statements.slice(1, 4).map((statement) => statement.params),
    [
      ["2026-10-09", "schemes_enabled", "quick"],
      ["2026-10-09", "space_mode", "english"],
      ["2026-10-09", "number_row", "false"],
    ],
  );
  assert.deepEqual(statements[4].params, ["2026-10-09", "chinese", 12]);
  for (const statement of statements) assert.match(statement.sql, /^INSERT INTO daily_\w+ .* ON CONFLICT /);
});
