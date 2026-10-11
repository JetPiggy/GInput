import assert from "node:assert/strict";
import { test } from "node:test";
import { SYSTEM, TASKS, isTask, systemPrompt, userMessage } from "../src/prompts.ts";
import { MAX_TEXT_LENGTH, parseRequest, readOutput } from "../src/text.ts";

test("the app's tasks are all known", () => {
  // 和 App 的 TextTask 一致。
  for (const task of ["to-en", "to-yue", "to-zh", "written", "colloquial", "polite", "casual", "fix"]) {
    assert.ok(isTask(task), task);
  }
  assert.equal(Object.keys(TASKS).length, 8);
  assert.equal(isTask("toString"), false);
  assert.equal(isTask("summarise"), false);
});

test("a request has a task and some text", () => {
  assert.deepEqual(parseRequest(JSON.stringify({ task: "to-en", text: "我食咗飯" })), { task: "to-en", text: "我食咗飯" });
  const rejected = [
    "not json",
    "[]",
    "null",
    JSON.stringify({ task: "to-en" }),
    JSON.stringify({ task: "to-en", text: "   \n" }),
    JSON.stringify({ task: "to-en", text: 12 }),
    JSON.stringify({ task: "summarise", text: "hello" }),
    JSON.stringify({ text: "hello" }),
  ];
  for (const body of rejected) assert.equal(parseRequest(body), null, body);
});

test("text longer than the limit is too long", () => {
  assert.notEqual(parseRequest(JSON.stringify({ task: "fix", text: "字".repeat(MAX_TEXT_LENGTH) })), "too-long");
  assert.equal(parseRequest(JSON.stringify({ task: "fix", text: "字".repeat(MAX_TEXT_LENGTH + 1) })), "too-long");
});

test("English text is kept in English when rewriting it", () => {
  const hint = "The text is in English, so write the result in English.";
  assert.ok(systemPrompt("polite", "Send me the file").endsWith(hint));
  assert.ok(systemPrompt("fix", "See you tmr").endsWith(hint));
  assert.ok(!systemPrompt("polite", "send 份 file 畀我").includes(hint));
  assert.ok(!systemPrompt("to-yue", "Send me the file").includes(hint));
  assert.ok(systemPrompt("to-en", "你好").startsWith(SYSTEM + "\n" + TASKS["to-en"]));
});

test("the text goes between tags", () => {
  assert.equal(userMessage("你好\n再見"), "<text>\n你好\n再見\n</text>");
});

function output(content: unknown, finishReason = "stop") {
  return {
    choices: [{ index: 0, message: { role: "assistant", content }, finish_reason: finishReason }],
    usage: { neurons: 2 },
  };
}

test("the result is the model's answer without extra tags", () => {
  assert.equal(readOutput(output("  Hello\nBye  ")), "Hello\nBye");
  assert.equal(readOutput(output("<think>hmm</think>\nHello")), "Hello");
  assert.equal(readOutput(output("<text>\nHello\n</text>")), "Hello");
});

test("unfinished or empty results are rejected", () => {
  assert.equal(readOutput(output("Hello, this is a very long", "length")), null);
  assert.equal(readOutput(output("  ")), null);
  assert.equal(readOutput(output("<text></text>")), null);
  assert.equal(readOutput(output(null)), null);
  assert.equal(readOutput({ response: "Hello" }), null);
  assert.equal(readOutput(null), null);
});
