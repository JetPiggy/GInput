// 自動檢查 run.mjs 的結果，在終端機印出摘要，並把逐句對照寫入 build/ai-eval/report.md。
// 自動檢查只能找出明顯的問題（簡體字、語體不對、答了問題等），譯得自不自然要人看。
import { writeFile } from "node:fs/promises";
import { join } from "node:path";
import { MODELS, OUT, loadCases, loadResults } from "./common.mjs";

/** Workers AI 每 1,000 neurons 收 US$0.011（超出每日免費額度的部分）。 */
const USD_PER_NEURON = 0.011 / 1000;

/** 只在簡體中文出現的常用字；香港繁體也會用的字（例如 吓、晒、着、系、听、万、于、台、里、面、后）不列入。 */
const SIMPLIFIED = new Set(
  "们这说来时会个为么没吗对现还过发经动问题学开关点机车东门见话让认识请谢应该欢买卖钱银电视脑网线络号码计实际务业产场厂广华语读写书报纸处难简单复杂历区医药疗预约订饭馆鸡鱼饮热冻气风阳阴汤节乐爱亲戏剧观声响头脸尽总从众优伤价传体边带记讲给进远运达选递适帮张长间闻阅队阶陆陈随险岁与专两严丧临丽举义乌乔习乡乱争亏亚亩亿仅仑仓仪伞伟伦伪侠侣侦侧侨俭债倾偿储儿兑兰兴养兽军农冯净凤凭凯击创刚剂剑劝办励劲劳势勋卢卫厅压厌厕县参双变叹启员呜哑唤啰喷团园围图圆圣坏块坚坛坝坟垒垦执扩扫扬扰抚抢护担拟拥拦择挡挤挥捞损换摄摆携摊敌数断无旧旷显晓暂术杀权条杨极构枪树样桥检楼欧残毕汇汉沟沪泼泽洁浅测济浓涛润涨渐湾湿满滚灭灯灵灾炉炼烂焕爷牵犹独狭狮猎猪献玛环琐画畅疯盐监盖盘矿砖础硕确礼积称稳穷窃竞笔签类粮紧红纪级练组细终结绕绘绝统继绩续维综绿缓编缘缩罗罚罢职联聪肃肠肤胁胜脏艰艺芦苏苹荐获莱营萝虑虽虾蚀蛮补装规览觉誉讨训议讯许论设访证评诉词译试诗诚询详误诸课谁调谈贝负贡财责败货质贩贪购贯贴贵费资赛赞赵赶轨转轮软轻载较辆输辞迁连迟逻遗邓邮邻郑酱释针钟钢铁链销锁错键闪闭闲闷闹阵隐雾页顶项顺须顾顿领频颜额飞饥饰饱饼驱驾验骑骗鲜鸟鸭麦齐龙龟",
);

/** 粵語口語才用的字和詞。 */
const YUE_CHARS = new Set("嘅咗啲唔佢哋冇喺嚟嘢咁啱嗰噉畀攞睇諗搵瞓嬲冚㗎喎咩嘞啫咋囉嚿啩喇吖啦攰".split(""));
const YUE_WORDS = ["聽日", "琴日", "尋日", "而家", "點解", "邊個", "邊度", "返工", "收工", "屋企"];
/** 書面語或普通話的字詞；粵語口語通常不用。 */
const ZH_CHARS = new Set("們這那說沒很吃給他她".split(""));
const ZH_WORDS = ["什麼", "甚麼", "怎麼", "現在", "東西", "正在"];
const ZH_PARTICLES = [
  [/的/g, /的士|的確|目的|的話/g],
  [/了/g, /了解|為了|除了|了不起/g],
  [/是/g, /是但|但是|是否|於是|就是|只是|還是|是非/g],
];

const CJK = /[\u3400-\u9fff]/;
const count = (text, re) => (text.match(re) ?? []).length;

export function yueScore(text) {
  const t = text.replace(/關係|聯係|係數/g, "");
  let score = [...t].filter((ch) => YUE_CHARS.has(ch) || ch === "係").length;
  for (const word of YUE_WORDS) score += t.split(word).length - 1;
  return score;
}

export function zhScore(text) {
  let score = [...text].filter((ch) => ZH_CHARS.has(ch)).length;
  for (const word of ZH_WORDS) score += text.split(word).length - 1;
  for (const [particle, allowed] of ZH_PARTICLES) score += count(text.replace(allowed, ""), particle);
  return score;
}

function register(text) {
  if (!CJK.test(text)) return "en";
  return yueScore(text) > 0 ? "yue" : "zh";
}

function similarity(a, b) {
  const x = [...a];
  const y = [...b];
  let prev = Array.from({ length: y.length + 1 }, (_, j) => j);
  for (let i = 1; i <= x.length; i++) {
    const row = [i];
    for (let j = 1; j <= y.length; j++) {
      row[j] = Math.min(prev[j] + 1, row[j - 1] + 1, prev[j - 1] + (x[i - 1] === y[j - 1] ? 0 : 1));
    }
    prev = row;
  }
  return 1 - prev[y.length] / Math.max(x.length, y.length, 1);
}

const PREAMBLE = /^(translation|rewritten|corrected|here('s| is)|sure|output|翻譯|譯文|改寫|改正|以下|好的)\b|^(翻譯|譯文|改寫|改正|結果)[:：]/i;

/** 回傳這個結果的問題清單；空清單即通過自動檢查。 */
export function check(c, output) {
  const flags = [];
  if (!output) return ["空白"];
  if (PREAMBLE.test(output) || /<\/?text>/.test(output)) flags.push("多了前言或標籤");
  const quoted = /^["「『“].*["」』”]$/s;
  if (quoted.test(output) && !quoted.test(c.text)) flags.push("加了引號");
  // 中文譯成英文，字數本來就會多好幾倍。
  const limit = c.maxLength ?? (c.task === "to-en" ? c.text.length * 8 + 40 : Math.max(c.text.length * 3, c.text.length + 60));
  if (output.length > limit) flags.push("太長");
  if (c.text.includes("\n") && output.split("\n").length !== c.text.split("\n").length) flags.push("分行不同");
  if ([...output].some((ch) => SIMPLIFIED.has(ch))) flags.push("簡體字");
  // 不換行空格、不換行連字號和其他文字系統的符號，會原封不動寫進使用者的文字。
  if (/[\u00a0\u2011\u202f\u0400-\u04ff\u0600-\u06ff\u0900-\u097f\u0e00-\u0e7f]/.test(output)) flags.push("特殊字元");

  const source = register(c.text);
  switch (c.task) {
    case "to-en":
      if (CJK.test(output)) flags.push("英文夾中文");
      break;
    case "to-yue":
      // 「生日快樂」這類句子粵語和書面語一樣，所以沒有粵語字也可以，只要不是書面語的寫法。
      if (zhScore(output) > yueScore(output)) flags.push("不夠口語");
      break;
    case "colloquial":
      if (yueScore(output) === 0 || zhScore(output) > yueScore(output)) flags.push("不夠口語");
      break;
    case "to-zh":
    case "written":
      if (yueScore(output) > 0) flags.push("有粵語字");
      break;
    case "polite":
    case "fix":
      if (source === "en" && CJK.test(output)) flags.push("改了語言");
      if (source === "yue" && yueScore(output) === 0) flags.push("改成書面語");
      // 改錯字的原句把粵語字寫錯了，看起來不像粵語，所以只檢查客氣的改寫。
      if (c.task === "polite" && source === "zh" && yueScore(output) > 0) flags.push("改成口語");
      break;
    case "casual":
      if (source === "en" && CJK.test(output)) flags.push("改了語言");
      break;
  }
  if (c.task === "fix" && !c.same && similarity(c.text, output) < 0.7) flags.push("改動太多");
  if (["written", "colloquial", "polite", "casual"].includes(c.task) && output.trim() === c.text.trim()) flags.push("沒有改");
  if (c.same && output.trim() !== c.text.trim()) flags.push("不應改動");
  for (const pattern of c.expect ?? []) if (!new RegExp(pattern, "iu").test(output)) flags.push(`欠「${pattern}」`);
  for (const pattern of c.forbid ?? []) if (new RegExp(pattern, "iu").test(output)) flags.push(`有「${pattern}」`);
  return flags;
}

const percentile = (sorted, p) => sorted[Math.min(sorted.length - 1, Math.floor(p * sorted.length))];

const TASK_NAMES = {
  "to-en": "譯英文",
  "to-yue": "譯粵語",
  "to-zh": "譯書面語",
  written: "改書面語",
  colloquial: "改口語",
  polite: "改客氣",
  casual: "改輕鬆",
  fix: "改錯字",
};

const cases = await loadCases();
const models = [];
for (const key of Object.keys(MODELS)) {
  const results = await loadResults(key);
  if (results.size) models.push({ key, results });
}

const summary = [];
const lines = ["# AI 模型比較：逐句對照", ""];
for (const m of models) {
  const rows = cases.map((c) => ({ c, r: m.results.get(c.id) })).filter(({ r }) => r);
  const ok = rows.filter(({ r }) => !r.error);
  m.flags = new Map(ok.map(({ c, r }) => [c.id, check(c, r.output)]));
  const ms = ok.map(({ r }) => r.ms).sort((a, b) => a - b);
  const neurons = ok.reduce((sum, { r }) => sum + (r.usage?.neurons ?? 0), 0) / Math.max(ok.length, 1);
  const byTask = {};
  for (const { c } of ok) {
    const t = (byTask[c.task] ??= { pass: 0, total: 0 });
    t.total++;
    if (m.flags.get(c.id).length === 0) t.pass++;
  }
  const traps = ok.filter(({ c }) => c.trap);
  summary.push({
    model: m.key,
    done: `${ok.length}/${cases.length}`,
    errors: rows.length - ok.length,
    pass: ok.filter(({ c }) => m.flags.get(c.id).length === 0).length,
    traps: `${traps.filter(({ c }) => m.flags.get(c.id).length === 0).length}/${traps.length}`,
    simplified: ok.filter(({ c }) => m.flags.get(c.id).includes("簡體字")).length,
    median: percentile(ms, 0.5),
    p90: percentile(ms, 0.9),
    neurons: neurons.toFixed(2),
    usdPer1000: (neurons * USD_PER_NEURON * 1000).toFixed(3),
    byTask,
  });
}

console.log("模型\t完成\t錯誤\t通過\t陷阱句\t簡體\t中位延遲\tP90 延遲\tneurons/次\tUS$/千次");
for (const s of summary) {
  console.log(
    [s.model, s.done, s.errors, `${s.pass} (${Math.round((s.pass / Number(s.done.split("/")[0])) * 100)}%)`, s.traps, s.simplified, `${s.median}ms`, `${s.p90}ms`, s.neurons, s.usdPer1000].join("\t"),
  );
}
console.log("\n各項工作通過自動檢查的句數：");
console.log(["模型", ...Object.values(TASK_NAMES)].join("\t"));
for (const s of summary) {
  console.log([s.model, ...Object.keys(TASK_NAMES).map((t) => (s.byTask[t] ? `${s.byTask[t].pass}/${s.byTask[t].total}` : "-"))].join("\t"));
}

lines.push("| 模型 | 完成 | 通過自動檢查 | 陷阱句 | 簡體字 | 中位延遲 | P90 延遲 | neurons/次 | US$/千次 |");
lines.push("|---|---|---|---|---|---|---|---|---|");
for (const s of summary) {
  lines.push(`| ${s.model} | ${s.done} | ${s.pass} | ${s.traps} | ${s.simplified} | ${s.median}ms | ${s.p90}ms | ${s.neurons} | ${s.usdPer1000} |`);
}
lines.push("");
for (const c of cases) {
  lines.push(`## ${c.id}（${TASK_NAMES[c.task]}）`, "", "> " + c.text.replace(/\n/g, "\n> "), "");
  if (c.note) lines.push(`註：${c.note}`, "");
  for (const m of models) {
    const r = m.results.get(c.id);
    if (!r) continue;
    const flags = r.error ? ["錯誤"] : m.flags.get(c.id);
    const text = (r.error ?? r.output).replace(/\n/g, " ⏎ ");
    lines.push(`- **${m.key}**${flags.length ? `（${flags.join("、")}）` : ""}：${text}`);
  }
  lines.push("");
}
await writeFile(join(OUT, "report.md"), lines.join("\n"));
console.log(`\n逐句對照：${join(OUT, "report.md")}`);
