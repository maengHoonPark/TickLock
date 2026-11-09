// 시세 저장 방식 성능 비교: direct(틱마다 INSERT) vs batch(버퍼 + 1초 배치 INSERT)
//
// 실행:  node bench/tick-storage.mjs      (Docker로 postgres/redis가 떠 있어야 한다. 약 4분 걸린다)
//
// 같은 부하(생성기가 초당 약 1,000건 발행)에서 모드를 바꿔 가며 서버를 재시작하고,
// 일정 시간 동안 Micrometer 지표(/actuator/metrics)와 컨테이너 CPU를 재서 표로 정리한다.
import { execSync } from "node:child_process";
import { writeFileSync } from "node:fs";

const BASE_URL = process.env.BASE_URL ?? "http://localhost:8080";
const MODES = ["direct", "batch"];
const WARMUP_SECONDS = 10;
const MEASURE_SECONDS = 30;
const CPU_SAMPLE_INTERVAL_SECONDS = 3;

const sleep = (seconds) => new Promise((resolve) => setTimeout(resolve, seconds * 1000));

/** 셸 명령 실행. env로 넘긴 값은 docker-compose.yml의 ${...} 치환에 쓰인다. */
function run(command, env = {}) {
  return execSync(command, { env: { ...process.env, ...env }, stdio: ["ignore", "pipe", "pipe"] }).toString();
}

/** 지표 1개를 {COUNT: 123, TOTAL_TIME: 4.5, ...} 모양으로 읽는다. */
async function metric(name) {
  const response = await fetch(`${BASE_URL}/actuator/metrics/${name}`);
  if (!response.ok) throw new Error(`${name} 조회 실패: HTTP ${response.status}`);
  const body = await response.json();
  return Object.fromEntries(body.measurements.map((m) => [m.statistic, m.value]));
}

async function snapshot() {
  const [published, received, stored, dropped, storeFailed, outOfOrder, lag] = await Promise.all([
    metric("ticklock.ticks.published"),
    metric("ticklock.ticks.received"),
    metric("ticklock.ticks.stored"),
    metric("ticklock.ticks.dropped"),
    metric("ticklock.ticks.store_failed"),
    metric("ticklock.ticks.out_of_order"),
    metric("ticklock.ticks.lag"),
  ]);
  return {
    published: published.COUNT,
    received: received.COUNT,
    stored: stored.COUNT,
    dropped: dropped.COUNT,
    storeFailed: storeFailed.COUNT,
    outOfOrder: outOfOrder.COUNT,
    lagCount: lag.COUNT,
    lagTotalSeconds: lag.TOTAL_TIME,
    lagMaxSeconds: lag.MAX,
  };
}

/** docker stats로 postgres/backend 컨테이너의 현재 CPU 사용률(%)을 읽는다. */
function cpuPercent() {
  const lines = run('docker stats --no-stream --format "{{.Name}}|{{.CPUPerc}}"').trim().split("\n");
  const find = (keyword) => {
    const line = lines.find((l) => l.includes(keyword));
    return line ? parseFloat(line.split("|")[1]) : Number.NaN;
  };
  return { postgres: find("postgres"), backend: find("backend") };
}

async function waitUntilHealthy() {
  for (let attempt = 0; attempt < 90; attempt++) {
    try {
      const response = await fetch(`${BASE_URL}/actuator/health`);
      if (response.ok) return;
    } catch {
      // 아직 서버가 뜨는 중
    }
    await sleep(2);
  }
  throw new Error("백엔드가 3분 안에 뜨지 않았습니다.");
}

const average = (values) => values.reduce((sum, v) => sum + v, 0) / values.length;

async function measure(mode) {
  console.log(`\n▶ [${mode}] 서버 재시작...`);
  run("docker compose up -d backend", { TICK_STORAGE_MODE: mode });
  await waitUntilHealthy();

  console.log(`  워밍업 ${WARMUP_SECONDS}초...`);
  await sleep(WARMUP_SECONDS);

  console.log(`  측정 ${MEASURE_SECONDS}초...`);
  const before = await snapshot();
  const startedAt = performance.now();
  const cpuSamples = [];
  // 경과 시간을 "시계로 직접" 잰다. docker stats 호출 자체가 몇 초 걸려서, 반복 횟수로 시간을 가정하면
  // 실제 측정 시간이 훨씬 길어지고 "초당" 수치가 그만큼 부풀려진다. (처음 만든 버전의 실제 버그였다)
  while (performance.now() - startedAt < MEASURE_SECONDS * 1000) {
    cpuSamples.push(cpuPercent());
    await sleep(CPU_SAMPLE_INTERVAL_SECONDS);
  }
  const after = await snapshot();

  const seconds = (performance.now() - startedAt) / 1000;
  const lagCount = after.lagCount - before.lagCount;
  return {
    mode,
    publishedPerSec: (after.published - before.published) / seconds,
    receivedPerSec: (after.received - before.received) / seconds,
    storedPerSec: (after.stored - before.stored) / seconds,
    backlog: after.published - after.received, // 발행됐지만 아직 처리 못 한 양 = 밀린 양
    lagMeanMs: lagCount > 0 ? ((after.lagTotalSeconds - before.lagTotalSeconds) / lagCount) * 1000 : Number.NaN,
    lagMaxMs: after.lagMaxSeconds * 1000,
    dropped: after.dropped - before.dropped,
    storeFailed: after.storeFailed - before.storeFailed,
    outOfOrder: after.outOfOrder - before.outOfOrder,
    cpuPostgres: average(cpuSamples.map((s) => s.postgres)),
    cpuBackend: average(cpuSamples.map((s) => s.backend)),
  };
}

const fmt = (value, digits = 0) => (Number.isFinite(value) ? value.toLocaleString("ko-KR", { maximumFractionDigits: digits }) : "-");

function toMarkdown(results) {
  const rows = [
    ["초당 발행(건)", (r) => fmt(r.publishedPerSec)],
    ["초당 처리(건)", (r) => fmt(r.receivedPerSec)],
    ["초당 DB 저장(건)", (r) => fmt(r.storedPerSec)],
    ["밀린 양(건)", (r) => fmt(r.backlog)],
    ["지연 평균(ms)", (r) => fmt(r.lagMeanMs, 1)],
    ["지연 최대(ms)", (r) => fmt(r.lagMaxMs)],
    ["유실: 버퍼 초과(건)", (r) => fmt(r.dropped)],
    ["유실: 저장 실패(건)", (r) => fmt(r.storeFailed)],
    ["순서 역전(건)", (r) => fmt(r.outOfOrder)],
    ["CPU postgres(%)", (r) => fmt(r.cpuPostgres, 1)],
    ["CPU backend(%)", (r) => fmt(r.cpuBackend, 1)],
  ];
  const header = `| 지표 | ${results.map((r) => r.mode).join(" | ")} |`;
  const divider = `|---|${results.map(() => "---").join("|")}|`;
  const body = rows.map(([label, pick]) => `| ${label} | ${results.map(pick).join(" | ")} |`);
  return [header, divider, ...body].join("\n");
}

try {
  const results = [];
  for (const mode of MODES) {
    results.push(await measure(mode));
  }

  const table = toMarkdown(results);
  console.log(`\n${table}\n`);

  const report = `# 시세 저장 방식 성능 비교\n\n측정일: ${new Date().toISOString()}\n측정 시간: 약 ${MEASURE_SECONDS}초 이상 (워밍업 ${WARMUP_SECONDS}초 제외, 초당 수치는 실제 경과 시간으로 계산)\n\n${table}\n`;
  writeFileSync(new URL("./results-tick-storage.md", import.meta.url), report);
  console.log("결과를 bench/results-tick-storage.md 에 저장했습니다.");
} finally {
  // 어떤 경우든 서버를 기본 모드(batch)로 되돌려 둔다
  console.log("\n서버를 기본 모드(batch)로 되돌립니다...");
  run("docker compose up -d backend", { TICK_STORAGE_MODE: "batch" });
}
