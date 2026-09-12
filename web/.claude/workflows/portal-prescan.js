export const meta = {
  name: 'portal-prescan',
  description: 'Batch pre-scan Angular components: compute single-pass/multi-pass classification and generate pass plans',
  phases: [
    { title: 'Scan', detail: 'Read each component and compute logic_lines, dispatch_points, async_zones' },
    { title: 'Report', detail: 'Aggregate into Markdown pre-scan report' },
  ],
}

const COMPONENT_SCHEMA = {
  type: 'object',
  required: ['filePath', 'componentName', 'logicLines', 'dispatchPoints', 'asyncZones', 'classification', 'recommendation'],
  properties: {
    filePath: { type: 'string' },
    componentName: { type: 'string' },
    logicLines: { type: 'number' },
    dispatchPoints: { type: 'number' },
    asyncZones: { type: 'number' },
    classification: { type: 'string', enum: ['single-pass', 'multi-pass'] },
    recommendation: { type: 'string', enum: ['proceed', 'skip'] },
    skipReason: { type: 'string' },
    passPlan: {
      type: 'array',
      items: {
        type: 'object',
        required: ['pass', 'fileName', 'methodsInScope', 'reason'],
        properties: {
          pass: { type: 'number' },
          fileName: { type: 'string' },
          methodsInScope: { type: 'string' },
          reason: { type: 'string' },
        },
      },
    },
    hasExistingSpec: { type: 'boolean' },
    existingSpecPath: { type: 'string' },
    oldSpecNotes: { type: 'string' },
  },
}

phase('Scan')
const parsedArgs = typeof args === 'string' ? JSON.parse(args) : args
const candidates = Array.isArray(parsedArgs) ? parsedArgs : (parsedArgs && Array.isArray(parsedArgs.candidates) ? parsedArgs.candidates : [])
const results = await pipeline(
  candidates,
  async (filePath) => agent(
    `Pre-scan this Angular component file for test planning.

File to read: ${filePath}

Step 1 — Read the file completely.

Step 2 — Compute metrics:
- logicLines: total non-blank lines MINUS (import lines + comment-only lines + lines containing only HTML/template strings)
- dispatchPoints: count of methods where a single field (e.g. item.type, this.mode) drives 3+ distinct code paths via switch/if-else chains
- asyncZones: sum of (HttpClient method calls) + (this.*.subscribe() calls) + (WebSocket onmessage/subscribe handlers) + (setInterval/setTimeout used for polling)

Step 3 — Classify:
- single-pass: logicLines ≤ 500 AND dispatchPoints ≤ 2 AND asyncZones ≤ 5
- multi-pass: logicLines > 500 OR dispatchPoints ≥ 3 OR asyncZones > 5

Step 4 — Determine recommendation:
Set recommendation = "skip" if ANY of these conditions hold:
  a) logicLines < 50 AND asyncZones = 0 AND dispatchPoints = 0
  b) Classification is single-pass AND a *.spec.ts or *.tl.spec.ts exists in the same directory AND that file appears to cover core functionality (happy path + main error path)
Otherwise set recommendation = "proceed". If skipping, fill in skipReason.

Step 5 — If hasExistingSpec: briefly read the existing spec and note any test cases NOT easily derivable from the component source (e.g. specific bug regression tests). Put a one-line summary in oldSpecNotes, or "none" if nothing notable.

Step 6 — For multi-pass only: build passPlan by assigning EVERY method to exactly one pass.
  Pass 1 (interaction) — always required: router navigation, HTTP data loading, ngOnInit/ngOnDestroy lifecycle, user-triggered flows (click, search, drag)
  Pass 2 (risk) — ONLY if asyncZones ≥ 3: async race conditions, sync-overrides-async, batch ops, state inconsistency across await, destructive actions (delete/move/overwrite)
  Pass 3 (display) — ONLY if dispatchPoints ≥ 3: label computation, icon selection, pure conditional display with no side effects, boundary inputs
  For each pass: fileName = ComponentName.interaction.tl.spec.ts / .risk.tl.spec.ts / .display.tl.spec.ts

Step 7 — Check if ComponentName.spec.ts or ComponentName.tl.spec.ts exists in the same directory as the input file.
Set hasExistingSpec = true and existingSpecPath = that path if found.

Return the complete structured result.`,
    {
      label: `scan:${filePath.split(/[\\/]/).pop().replace(/\.ts$/, '')}`,
      phase: 'Scan',
      schema: COMPONENT_SCHEMA,
    }
  )
)

phase('Report')
const valid = results.filter(Boolean)
const routeName = (parsedArgs && !Array.isArray(parsedArgs) && parsedArgs.route) || 'unknown'

const tableRows = valid.map(r => {
  const passInfo = r.passPlan && r.passPlan.length > 0
    ? r.passPlan.map(p => `P${p.pass}: ${p.fileName}`).join('<br>')
    : r.classification === 'multi-pass' ? '—' : 'single pass'
  const status = r.recommendation === 'skip' ? '⏭ 跳过' : '✅ 推进'
  const oldSpec = r.hasExistingSpec ? `⚠️ ${r.existingSpecPath.split(/[\\/]/).pop()}` : ''
  const notes = r.oldSpecNotes && r.oldSpecNotes !== 'none' ? r.oldSpecNotes : r.oldSpecNotes === 'none' ? '无' : ''
  return `| 待审核 | ${r.componentName} | ${r.logicLines} | ${r.dispatchPoints} | ${r.asyncZones} | **${r.classification}** | ${status} | ${oldSpec} | ${notes} | ${passInfo} |`
}).join('\n')

const proceed = valid.filter(r => r.recommendation === 'proceed').length
const skipped = valid.filter(r => r.recommendation === 'skip').length
const multiPass = valid.filter(r => r.classification === 'multi-pass').length

const multiPassDetail = valid
  .filter(r => r.classification === 'multi-pass' && r.passPlan && r.passPlan.length > 0)
  .map(r =>
    `### ${r.componentName}\n\n` +
    r.passPlan.map(p =>
      `**Pass ${p.pass}** (\`${p.fileName}\`)\n- Methods: ${p.methodsInScope}\n- Reason: ${p.reason}`
    ).join('\n\n')
  ).join('\n\n')

const report = `# ${routeName} Route Pre-scan Report

**候选组件数**: ${valid.length} | **建议推进**: ${proceed} | **建议跳过**: ${skipped} | **多 pass 组件**: ${multiPass}

## 状态说明
- 第一列「状态」初始为「待审核」，人工审核后改为 ✅已测试 / ⏭已跳过
- ⚠️ 有旧spec — 新测试通过后在同 PR 内删除旧 .spec.ts

## 候选组件清单

| 状态 | 组件 | logic_lines | dispatch | async_zones | 分类 | 建议 | 旧 spec | 旧 spec 备注 | Pass 计划 |
|------|------|-------------|----------|-------------|------|------|---------|-------------|-----------|
${tableRows}

## 多 pass 组件详情

${multiPassDetail || '— 无多 pass 组件 —'}
`

return { report, results: valid }
