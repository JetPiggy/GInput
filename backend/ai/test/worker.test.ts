import assert from "node:assert/strict";
import { test } from "node:test";
import worker, { MODEL } from "../src/index.ts";

interface Day {
  requests: number;
  neurons: number;
}

interface Options {
  /** 當日已用的 neurons。 */
  neurons?: number;
  limited?: boolean;
  reply?: unknown;
  aiError?: boolean;
  dbError?: boolean;
}

/** 假的 Workers AI、D1 和 Rate Limiting，記下 Worker 怎樣用它們。 */
function fakeEnv(options: Options = {}) {
  const days = new Map<string, Day>();
  const calls: { model: string; input: any }[] = [];
  const pending: Promise<unknown>[] = [];
  const env = {
    DAILY_NEURONS: "100",
    AI: {
      async run(model: string, input: unknown) {
        calls.push({ model, input });
        if (options.aiError) throw new Error("AiError: you have used up your daily free allocation");
        return (
          options.reply ?? {
            choices: [{ index: 0, message: { role: "assistant", content: "I've eaten." }, finish_reason: "stop" }],
            usage: { prompt_tokens: 200, completion_tokens: 5, total_tokens: 205, neurons: 2.5 },
          }
        );
      },
    },
    RATE_LIMITER: {
      async limit() {
        return { success: !options.limited };
      },
    },
    DB: {
      prepare(sql: string) {
        return {
          bind(...params: unknown[]) {
            const [day, neurons] = params as [string, number];
            return {
              async first(column: string) {
                if (options.dbError) throw new Error("D1_ERROR");
                assert.match(sql, /^INSERT INTO daily_usage .* RETURNING neurons$/);
                const row = days.get(day) ?? { requests: 0, neurons: options.neurons ?? 0 };
                row.requests++;
                days.set(day, row);
                return row[column as keyof Day];
              },
              async run() {
                assert.match(sql, /^UPDATE daily_usage SET neurons = neurons \+ \?2 WHERE day = \?1$/);
                days.get(day)!.neurons += neurons;
              },
            };
          },
        };
      },
    },
  };
  const ctx = {
    waitUntil(promise: Promise<unknown>) {
      pending.push(promise);
    },
    passThroughOnException() {},
  };
  return { env, ctx, days, calls, settle: () => Promise.all(pending) };
}

function post(body: unknown, path = "/v1/text"): Request {
  return new Request(`https://ginput-ai.jetpiggy.com${path}`, {
    method: "POST",
    headers: { "Content-Type": "application/json", "CF-Connecting-IP": "203.0.113.7" },
    body: typeof body === "string" ? body : JSON.stringify(body),
  });
}

function call(request: Request, fake: ReturnType<typeof fakeEnv>): Promise<Response> {
  return worker.fetch(request as any, fake.env as any, fake.ctx as any);
}

test("a translation returns the model's result", async () => {
  const fake = fakeEnv();
  const response = await call(post({ task: "to-en", text: "我食咗飯" }), fake);
  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), { result: "I've eaten." });

  assert.equal(fake.calls.length, 1);
  const { model, input } = fake.calls[0];
  assert.equal(model, MODEL);
  assert.equal(input.messages[0].role, "system");
  assert.match(input.messages[0].content, /Task: translate the text into natural, idiomatic English/);
  assert.deepEqual(input.messages[1], { role: "user", content: "<text>\n我食咗飯\n</text>" });
  assert.deepEqual(input.chat_template_kwargs, { enable_thinking: false });

  await fake.settle();
  assert.deepEqual([...fake.days.values()], [{ requests: 1, neurons: 2.5 }]);
});

test("only POST /v1/text is served", async () => {
  const fake = fakeEnv();
  assert.equal((await call(post({ task: "to-en", text: "hi" }, "/"), fake)).status, 404);
  const response = await call(new Request("https://ginput-ai.jetpiggy.com/v1/text"), fake);
  assert.equal(response.status, 405);
  assert.equal(response.headers.get("Allow"), "POST");
  assert.equal(fake.calls.length, 0);
});

test("bad requests never reach the model", async () => {
  const fake = fakeEnv();
  assert.equal((await call(post("not json"), fake)).status, 400);
  assert.equal((await call(post({ task: "poem", text: "hi" }), fake)).status, 400);
  assert.equal((await call(post({ task: "to-en", text: "字".repeat(1001) }), fake)).status, 413);
  assert.equal((await call(post({ task: "to-en", text: "x".repeat(9000) }), fake)).status, 413);
  assert.equal(fake.calls.length, 0);
  assert.equal(fake.days.size, 0);
});

test("too many requests from one IP are refused", async () => {
  const fake = fakeEnv({ limited: true });
  assert.equal((await call(post({ task: "fix", text: "我食左飯" }), fake)).status, 429);
  assert.equal(fake.calls.length, 0);
});

test("the daily ceiling stops all requests", async () => {
  const fake = fakeEnv({ neurons: 100 });
  assert.equal((await call(post({ task: "fix", text: "我食左飯" }), fake)).status, 503);
  assert.equal(fake.calls.length, 0);
});

test("a database error counts as reaching the ceiling", async () => {
  const fake = fakeEnv({ dbError: true });
  assert.equal((await call(post({ task: "fix", text: "我食左飯" }), fake)).status, 503);
  assert.equal(fake.calls.length, 0);
});

test("model errors and unfinished results are reported", async () => {
  assert.equal((await call(post({ task: "fix", text: "我食左飯" }), fakeEnv({ aiError: true }))).status, 503);
  const cut = { choices: [{ index: 0, message: { role: "assistant", content: "I have" }, finish_reason: "length" }] };
  assert.equal((await call(post({ task: "to-en", text: "我食咗飯" }), fakeEnv({ reply: cut }))).status, 502);
});
