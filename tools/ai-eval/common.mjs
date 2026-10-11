// 比較工具共用的設定：候選模型、提示和結果檔案。
import { readFile } from "node:fs/promises";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

export const here = dirname(fileURLToPath(import.meta.url));
export const OUT = join(here, "..", "..", "build", "ai-eval");

/** 候選模型。options 是關掉或減少推理的參數，各模型的寫法不同（見 `wrangler ai models schema`）。 */
export const MODELS = {
  "gemma-4-26b": { id: "@cf/google/gemma-4-26b-a4b-it", options: { chat_template_kwargs: { enable_thinking: false } } },
  "qwen3-30b": { id: "@cf/qwen/qwen3-30b-a3b-fp8", noThink: true },
  "qwen3.8-27b": { id: "@cf/qwen/qwen3.8-27b", options: { chat_template_kwargs: { enable_thinking: false } } },
  "glm-4.7-flash": { id: "@cf/zai-org/glm-4.7-flash", options: { chat_template_kwargs: { enable_thinking: false } } },
  // 不能關掉推理，只能調到最低。
  "gpt-oss-120b": { id: "@cf/openai/gpt-oss-120b", responses: true, options: { reasoning: { effort: "low" } } },
  // 以下要 Workers 付費方案，預設不跑。
  "deepseek-v4-flash": { id: "@cf/deepseek-ai/deepseek-v4-flash-0731", paid: true, options: { reasoning_effort: "none" } },
  "glm-5.3-flash": { id: "@cf/zai-org/glm-5.3-flash", paid: true, options: { reasoning_effort: "low" }, maxTokens: 2000 },
};

// 提示和 Worker 用的同一份（backend/ai/src/prompts.ts），比較的就是正式用的提示。
export { SYSTEM, TASKS, systemPrompt, temperature, userMessage } from "../../backend/ai/src/prompts.ts";

export async function loadCases() {
  return JSON.parse(await readFile(join(here, "cases.json"), "utf8"));
}

export async function loadResults(modelKey) {
  const lines = await readFile(join(OUT, "results", `${modelKey}.jsonl`), "utf8").catch(() => "");
  const results = new Map();
  for (const line of lines.split("\n")) {
    if (!line.trim()) continue;
    const row = JSON.parse(line);
    results.set(row.id, row); // 同一句重跑過的話，用最新的結果。
  }
  return results;
}
