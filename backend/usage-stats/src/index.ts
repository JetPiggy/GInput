// GInput 的使用統計 Worker：收 App 每日一份的匿名報告，只把合計數字加進 D1。
// 不記錄 IP、不寫日誌，也不保存報告本身（ADR 0001）。

import { parseReport, upserts } from "./report.ts";

interface Env {
  DB: D1Database;
}

const MAX_BODY_BYTES = 4096;

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
  async fetch(request: Request, env: Env): Promise<Response> {
    if (new URL(request.url).pathname !== "/v1/daily") return status(404);
    if (request.method !== "POST") return status(405, { Allow: "POST" });
    if (Number(request.headers.get("Content-Length") ?? 0) > MAX_BODY_BYTES) return status(413);
    const text = await readBody(request, MAX_BODY_BYTES);
    if (text === null) return status(413);

    let body: unknown;
    try {
      body = JSON.parse(text);
    } catch {
      return status(400);
    }
    const report = parseReport(body, new Date());
    if (report === null) return status(400);

    await env.DB.batch(upserts(report).map(({ sql, params }) => env.DB.prepare(sql).bind(...params)));
    return status(204);
  },
} satisfies ExportedHandler<Env>;
