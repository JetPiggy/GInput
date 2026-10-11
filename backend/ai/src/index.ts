// GInput 的雲端翻譯和改寫 Worker（ADR 0005、0006）：收 App 送來的工作和原文，交 Workers AI 的 Gemma 4 26B 處理。
// 不記錄原文和結果，不寫日誌，也不經 AI Gateway。

import { systemPrompt, temperature, userMessage } from "./prompts.ts";
import { parseRequest, readOutput } from "./text.ts";

interface Env {
  AI: Ai;
  DB: D1Database;
  RATE_LIMITER: RateLimit;
  /** 全部請求每日（UTC）合共可以用的 neurons。 */
  DAILY_NEURONS: string;
}

export const MODEL = "@cf/google/gemma-4-26b-a4b-it";

/** 1,000 個中文字用 UTF-8 是 3,000 個位元組；加上 JSON 的跳脫，8 KB 足夠。 */
const MAX_BODY_BYTES = 8 * 1024;

/** 1,000 字的原文譯成英文，大約 1,000 個 token。 */
const MAX_TOKENS = 2048;

// 先記一次請求，並取得當日已用的 neurons；結果出來後再加上這次用的。
const COUNT_REQUEST =
  "INSERT INTO daily_usage (day, requests, neurons) VALUES (?1, 1, 0) " +
  "ON CONFLICT (day) DO UPDATE SET requests = requests + 1 RETURNING neurons";
const ADD_NEURONS = "UPDATE daily_usage SET neurons = neurons + ?2 WHERE day = ?1";

/** 讀取請求內容，超過 limit 個位元組就停下並回傳 null。 */
async function readBody(request: Request, limit: number): Promise<string | null> {
  if (request.body === null) return "";
  const reader = request.body.getReader();
  const chunks: Uint8Array[] = [];
  let size = 0;
  for (;;) {
    const { done, value } = await reader.read();
    if (done) break;
    size += value.byteLength;
    if (size > limit) {
      await reader.cancel();
      return null;
    }
    chunks.push(value);
  }
  const bytes = new Uint8Array(size);
  let offset = 0;
  for (const chunk of chunks) {
    bytes.set(chunk, offset);
    offset += chunk.byteLength;
  }
  return new TextDecoder().decode(bytes);
}

function status(code: number, headers?: HeadersInit): Response {
  return new Response(null, { status: code, headers });
}

export default {
  async fetch(request: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
    if (new URL(request.url).pathname !== "/v1/text") return status(404);
    if (request.method !== "POST") return status(405, { Allow: "POST" });
    if (Number(request.headers.get("Content-Length") ?? 0) > MAX_BODY_BYTES) return status(413);
    const body = await readBody(request, MAX_BODY_BYTES);
    if (body === null) return status(413);
    const parsed = parseRequest(body);
    if (parsed === "too-long") return status(413);
    if (parsed === null) return status(400);

    // 每個 IP 每分鐘的上限，只在 Cloudflare 的記憶體計算，不儲存 IP。
    const ip = request.headers.get("CF-Connecting-IP") ?? "";
    if (!(await env.RATE_LIMITER.limit({ key: ip })).success) return status(429);

    // 全部請求每日的上限，避免有人繞過 App 的每日次數，令成本失控。資料庫出錯也當作到了上限。
    const day = new Date().toISOString().slice(0, 10);
    let used: number;
    try {
      used = (await env.DB.prepare(COUNT_REQUEST).bind(day).first<number>("neurons")) ?? 0;
    } catch {
      return status(503);
    }
    if (used >= Number(env.DAILY_NEURONS)) return status(503);

    let output: unknown;
    try {
      output = await env.AI.run(MODEL, {
        messages: [
          { role: "system", content: systemPrompt(parsed.task, parsed.text) },
          { role: "user", content: userMessage(parsed.text) },
        ],
        max_tokens: MAX_TOKENS,
        temperature: temperature(parsed.task),
        chat_template_kwargs: { enable_thinking: false },
      });
    } catch {
      // 錯誤訊息可能帶有原文，所以不記錄。免費方案用完當日的 neurons 也是這裏。
      return status(503);
    }

    const neurons = (output as { usage?: { neurons?: unknown } } | null)?.usage?.neurons;
    if (typeof neurons === "number" && neurons > 0) {
      ctx.waitUntil(env.DB.prepare(ADD_NEURONS).bind(day, neurons).run().catch(() => undefined));
    }
    const result = readOutput(output);
    if (result === null) return status(502);
    return Response.json({ result });
  },
} satisfies ExportedHandler<Env>;
