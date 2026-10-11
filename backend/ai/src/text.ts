// 雲端翻譯和改寫的請求格式和結果（規格見 docs/spec/v1.md 的「雲端翻譯和改寫」）。
// App 那邊是 app/src/main/java/com/jetpiggy/ginput/cloudtext/CloudTextClient.kt；改動要兩邊一起改。

import { isTask, type Task } from "./prompts.ts";

/** 原文最多幾個 UTF-16 字元，和 App 的上限一樣。 */
export const MAX_TEXT_LENGTH = 1000;

export interface TextRequest {
  task: Task;
  text: string;
}

function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

/** 請求是 {"task": "to-en", "text": "…"}。格式不對回傳 null；原文太長回傳 "too-long"。 */
export function parseRequest(body: string): TextRequest | "too-long" | null {
  let json: unknown;
  try {
    json = JSON.parse(body);
  } catch {
    return null;
  }
  if (!isObject(json)) return null;
  const { task, text } = json;
  if (!isTask(task) || typeof text !== "string" || text.trim() === "") return null;
  if (text.length > MAX_TEXT_LENGTH) return "too-long";
  return { task, text };
}

interface ChatOutput {
  choices?: { message?: { content?: unknown }; finish_reason?: unknown }[];
}

/**
 * 從 Workers AI 的回應取出結果，去掉推理過程和模型照抄的 <text> 標籤。
 * 結果不完整（寫到 max_tokens 就停了）或空白，就回傳 null。
 */
export function readOutput(output: unknown): string | null {
  const choice = (output as ChatOutput | null)?.choices?.[0];
  if (choice?.finish_reason === "length") return null;
  const content = choice?.message?.content;
  if (typeof content !== "string") return null;
  let result = content.replace(/<think>[\s\S]*?<\/think>/g, "").trim();
  const wrapped = /^<text>\s*([\s\S]*?)\s*<\/text>$/.exec(result);
  if (wrapped) result = wrapped[1];
  return result === "" ? null : result;
}
