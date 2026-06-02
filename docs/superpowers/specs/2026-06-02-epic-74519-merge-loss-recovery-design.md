# Design: Epic-74519 Merge Loss Recovery

## Background

Merge commit `563ab05dd` ("Merge remote-tracking branch 'origin/main' into epic-74519", June 1 2026)
brought in the Angular standalone migration, Angular 17→21 upgrade chain, and Vitest migration from
`origin/main`. During conflict resolution, the merge chose `origin/main`'s version in a number of
files, silently discarding epic-74519-specific feature and bug-fix changes.

Known losses confirmed before this spec was written:

| File | Lost content |
|---|---|
| `tip-customize-dialog.component.html` | Tooltip Style fieldset (Default/Card radio buttons) |
| `tip-customize-dialog.component.ts` | `tooltipStyle` FormControl init + `saveChanges` assignment |
| `tip-customize-dialog-model.ts` | `tooltipStyle?: "DEFAULT"\|"CARD"` field |
| `chart-plot-options-pane.component.html` | `treeLayout` custom-select + `smoothLines` checkbox |
| `calculate-pane-dialog.component.html` | 9 of 10 custom-select replacements reverted to native selects |
| `task-action-pane.component.html` | `form-row-float-label` not replaced with BEM class |
| `action-accordion.component.html` | Multiple `form-row-float-label` → BEM class replacements lost |
| `task-options-pane.component.html` | 1 form-row-float-label + 2 custom-select regressions |
| `add-parameter-dialog.component.html` | 1 custom-select regression |
| `parameter-dialog.component.html` | 1 custom-select regression |
| `sql-query-join-dialog.component.html` | 2 custom-select regressions |
| `chart-plot-area.component.spec.ts` | 2 tests (Bug #75091 snap-state clearing) |
| `color-picker.spec.ts` | 1 test (focus restoration after color select) |

The full extent of losses across the 634-file conflict zone is unknown and requires a systematic audit.

## Reference Points

| Label | Commit | Role |
|---|---|---|
| BASE | `298d23f34` | Merge base — common ancestor of both sides |
| EPIC | `19387c26c` | Epic-74519 tip just before the merge |
| MAIN | `4e1a6d45e` | Origin/main tip brought into the merge |
| HEAD | `63d596a4a` | Current branch tip (2 commits after merge) |

Conflict zone: 634 files changed on both sides relative to BASE.
- 349 HTML files
- 215 TypeScript (non-spec) files
- 70 spec files
- 0 Java/SCSS files (Java was changed on only one side; SCSS on only one side)

## Goal

Re-apply every epic-74519-specific change that was dropped by the merge, adapted to Angular 21
requirements, such that the branch builds cleanly, lints without errors, and all tests pass.

## Angular 21 Requirements for All Fixes

Every change applied must conform to the following constraints:

- **Standalone components**: No NgModules. All component dependencies declared in
  `@Component({ standalone: true, imports: [...] })`.
- **Functional routes**: No routing modules.
- **Control flow**: `@if`, `@for`, `@switch` in templates — not `*ngIf`, `*ngFor`, `*ngSwitch`.
- **Vitest**: Test files use `vi.spyOn`, `vi.fn()`, `vi.mock()`. Import test globals from `vitest`
  (or rely on `vitest/globals` configured in `tsconfig`). No `jest.*` APIs.
- **Angular Material 21**: Use current API signatures.

## Architecture

### Phase 1 — Audit (parallel, read-only)

A workflow fans out one agent per file across all 634 conflict-zone files. Each agent:

1. Fetches four versions of the file via `git show <ref>:<path>`:
   - BASE, EPIC, MAIN, HEAD
2. Diffs EPIC vs BASE to identify what epic-74519 added or changed.
3. Checks whether each addition/change is present in HEAD.
4. Returns a structured result:

```
{
  file: string,
  status: "clean" | "losses_found",
  losses: [
    {
      description: string,       // human-readable summary
      category: "html_missing_element" | "ts_missing_field" | "spec_missing_test" | "other",
      epicContent: string,        // the content that should be present
      insertionContext: string    // surrounding lines to locate where to re-insert
    }
  ]
}
```

Files where EPIC == BASE are trivially clean and skipped. Files where HEAD == MAIN are highest
priority (epic's entire contribution dropped). Mixed resolutions receive the most careful line-by-line
analysis.

### Phase 2 — Fix (parallel with worktree isolation)

The audit output is grouped into **component fix units** — each unit collects all losses for a
single Angular component (e.g., `tip-customize-dialog.component.html` + `.ts` + model file).
A single fix agent handles each unit so that cross-file consistency is maintained within a
component (e.g., adding `<custom-select>` to the template and `CustomSelectComponent` to the
`imports[]` in the same agent run).

For each component fix unit, the fix agent:

1. Reads all affected files from disk.
2. Locates each insertion point using the `insertionContext` from the audit.
3. Inserts the missing content, adapting it to Angular 21:
   - Rewrites `*ngIf="x"` → `@if (x) { ... }`
   - Rewrites `*ngFor="let x of xs"` → `@for (x of xs; track x) { ... }`
   - Removes NgModule imports; adds component to `imports[]` in consuming `@Component`
   - Converts `jest.spyOn` → `vi.spyOn`, `jest.fn()` → `vi.fn()` in spec files
4. Cross-checks consistency:
   - If an HTML template references a new component (e.g., `<custom-select>`), verifies
     that component appears in the consuming TS file's `imports[]`.
   - If a TypeScript model adds a field, verifies the field is used consistently in TS.
5. Writes all modified files.

Fix agents run in parallel (each unit touches different files, so no worktree isolation is needed).

### Phase 3 — Verification

After all fixes are applied and merged back:

1. `npm run build` — must succeed with zero errors.
2. `npm run lint` — must succeed.
3. `npm run test` — all tests must pass.

Any failure triggers a targeted fix loop: identify the failing file, fix, re-verify.

## Data Flow

```
BASE / EPIC / MAIN / HEAD git refs
         │
         ▼
Phase 1: 634 audit agents (parallel)
         │
         ▼ losses[] per file
Phase 2: N fix agents (parallel, worktree isolation)
         │
         ▼ fixed files on disk
Phase 3: build → lint → test
         │
         ▼ all pass → done
           fail  → targeted fix loop
```

## Error Handling

- Audit agent returns `status: "clean"` with a note if it cannot determine losses (e.g., binary
  file, file deleted on one side). These are reviewed manually.
- Fix agent that cannot locate the insertion context raises the issue to a review step rather than
  making a blind edit.
- Phase 3 failures are diagnosed per-error before retrying, not retried blindly.

## Out of Scope

- Java backend changes: none were lost (Java files had no overlap in the conflict zone).
- SCSS files: none were in the conflict zone.
- Refactoring beyond what is needed to re-apply the lost content.
- Changes introduced after the merge (commits `d6b018bcc` and `63d596a4a`) are not affected.
