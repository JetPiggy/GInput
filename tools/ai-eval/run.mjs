// 用測試句比較各 AI 模型的翻譯和改寫。結果寫入 build/ai-eval/results/，已有結果的句子不會重做。
// 用法見 README.md。
import { getPlatformProxy } from "wrangler";
import { appendFile, mkdir, readFile, writeFile } from "node:fs/promises";
import { join } from "node:path";
import { parseArgs } from "node:util";
import { MODELS, OUT, here, loadCases, loadResults, systemPrompt, temperature, userMessage } from "./common.mjs";

function request(model, task, text) {
  const system = systemPrompt(task, text) + (model.noThink ? "\n/no_think" : "");
  const messages = [
    { role: "system", content: system },
    { role: "user", content: userMessage(text) },
  ];
  if (model.responses) return { input: messages, ...model.options };
  return { messages, max_tokens: model.maxTokens ?? 1000, temperature: temperature(task), ...model.options };
}

function extractText(result) {
  if (typeof result?.response === "string") return result.response;
  const choice = result?.choices?.[0];
  if (typeof choice?.message?.content === "string") return choice.message.content;
  if (typeof choice?.text === "string") return choice.text;
  if (Array.isArray(result?.output)) {
    return result.output
      .filter((item) => item.type === "message")
      .flatMap((item) => item.content ?? [])
      .map((part) => part.text ?? "")
      .join("");
  }
  throw new Error("未知的回應格式：" + JSON.stringify(result).slice(0, 300));
}

const clean = (text) => text.replace(/<think>[\s\S]*?<\/think>/g, "").trim();

const { values } = parseArgs({
  options: {
    models: {
      type: "string",
      default: Object.keys(MODELS).filter((key) => !MODELS[key].paid).join(","),
    },
    cases: { type: "string" }, // 逗號分隔的 id，或以「-」結尾的 id 前綴，例如 yue-01,fix-
    budget: { type: "string", default: "9000" },
    concurrency: { type: "string", default: "4" },
    force: { type: "boolean", default: false },
  },
});
const budget = Number(values.budget);
const filters = values.cases?.split(",").map((s) => s.trim()).filter(Boolean);
const cases = (await loadCases()).filter(
  (c) => !filters || filters.some((f) => c.id === f || (f.endsWith("-") && c.id.startsWith(f))),
);
await mkdir(join(OUT, "results"), { recursive: true });

// Workers AI 每日（UTC）有 10,000 neurons 免費額度；按回應的 usage.neurons 累計，到了上限就停。
const ledgerFile = join(OUT, "neurons.json");
const ledger = JSON.parse(await readFile(ledgerFile, "utf8").catch(() => "{}"));
const today = new Date().toISOString().slice(0, 10);
let used = ledger[today] ?? 0;
const saveLedger = () => writeFile(ledgerFile, JSON.stringify({ ...ledger, [today]: Math.round(used * 100) / 100 }, null, 2) + "\n");

const { env, dispose } = await getPlatformProxy({ configPath: join(here, "wrangler.jsonc") });
let stopAll = null;
try {
  for (const key of values.models.split(",").map((s) => s.trim())) {
    const model = MODELS[key];
    if (!model) throw new Error(`沒有這個模型：${key}`);
    const done = await loadResults(key);
    const queue = cases.filter((c) => values.force || !done.get(c.id) || done.get(c.id).error);
    console.log(`${key}：${queue.length} 句（今日已用 ${used.toFixed(0)} neurons）`);
    let failures = 0; // 連續失敗；同一個模型連續失敗 3 次就跳過它，例如要付費方案的模型。
    const worker = async () => {
      while (queue.length && !stopAll && failures < 3) {
        if (used >= budget) {
          stopAll = `已用 ${used.toFixed(0)} neurons，到了上限 ${budget}`;
          return;
        }
        const c = queue.shift();
        const started = Date.now();
        const row = { id: c.id, model: model.id, at: new Date().toISOString() };
        try {
          const result = await env.AI.run(model.id, request(model, c.task, c.text));
          row.ms = Date.now() - started;
          row.output = clean(extractText(result));
          row.usage = result.usage ?? null;
          used += result.usage?.neurons ?? 0;
          failures = 0;
        } catch (error) {
          row.ms = Date.now() - started;
          row.error = String(error?.message ?? error);
          failures++;
          if (/daily free allocation/i.test(row.error)) stopAll = row.error;
        }
        await appendFile(join(OUT, "results", `${key}.jsonl`), JSON.stringify(row) + "\n");
        process.stdout.write(row.error ? "x" : ".");
      }
    };
    await Promise.all(Array.from({ length: Number(values.concurrency) }, worker));
    process.stdout.write("\n");
    if (failures >= 3) console.log(`${key} 連續失敗，跳過。最後的錯誤見結果檔。`);
    await saveLedger();
    if (stopAll) break;
  }
} finally {
  await saveLedger();
  await dispose();
}
console.log(`今日共用 ${used.toFixed(0)} neurons。`);
if (stopAll) console.log(`停止：${stopAll}`);
