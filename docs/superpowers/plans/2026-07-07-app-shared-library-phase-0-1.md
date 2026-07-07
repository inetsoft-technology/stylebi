# app-shared / common Library Extraction — Phase 0 & 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `projects/shared` a clean, UI-toolkit-neutral leaf — zero imports into `portal/` or `em/`, zero `@ng-bootstrap`/`@angular/material`/`bootstrap` dependencies — while the workspace still builds and all existing tests pass. No library packaging yet.

**Architecture:** This is Phase 0 (baseline capture) + Phase 1 of the larger refactor specified in `docs/superpowers/specs/2026-07-07-app-shared-library-design.md`. Phase 1 only *rearranges source within the existing project tree* so `projects/shared` becomes a dependency leaf. It introduces no `ng-packagr` machinery and does not move the `app/` tree — that is Phases 2–5, planned separately after this lands. Every back-edge is resolved by one of: **pull the target down** into `projects/shared`, **push app-coupled code up** into `projects/portal/src/app` (the future `app-shared`) or `projects/em/src/app`, or **extract/split** a mixed file.

**Tech Stack:** Angular 21, TypeScript 5.9, esbuild (`@angular/build:application`), Vitest 4, gulp; build orchestrated by Maven but runnable via `npm` from `community/web`.

## Global Constraints

- Work only inside `community/web`. Branch: `feature-app-shared-library` (community submodule).
- **Invariant 1 (no back-edges):** `grep -rE 'from "(\.\./)+(portal|em)/' projects/shared` (excluding spec files handled in their own task) must end empty.
- **Invariant 2 (UI-neutral):** `grep -rE '@ng-bootstrap|@angular/material|from "bootstrap' projects/shared` must end empty.
- No change to any app's runtime behavior or output artifact contents — Phase 1 ends with a byte/structural diff against the Phase 0 baseline showing no meaningful change.
- Preserve existing code style (license header block at top of every new `.ts`, 3-space indent, `scss` component styles).
- Commit after every task with a descriptive message; end each commit message with the `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>` trailer.
- Run all `npm`/`ng` commands from `community/web`.

---

## File Structure (Phase 1 target for `projects/shared`)

New/relocated files inside `projects/shared` (the `common` library-to-be):

- `data/identity/identity-id.ts` — pulled down from `em` (pure identity model + `convertToKey`).
- `data/identity/idenity-id-with-label.ts` — pulled down from `em`.
- `data/model/` — pulled-down portal model types (`data-ref`, `chart-ref`, `xschema`, `time-instant`, `common-kv-model`, `tree-node-model`, `server-path-info-model`, `dynamic-value-model`, plus verified em setting-models).
- `util/local-storage.util.ts` — pulled down from portal.

Files moved *out* of `projects/shared`:

- `util/localized-mat-paginator.ts` → `projects/em/src/app/common/util/` (Material, em-only).
- `util/guard/global-parameter-guard.service.ts` → `projects/portal/src/app/common/guard/` (Bootstrap + app-coupled, portal-only).

New files in the future-`app-shared` tree (`projects/portal/src/app`):

- `common/util/binding-tool.ts` — three functions extracted from `shared/util/tool.ts`.
- `binding/services/assistant/ai-assistant-binding.service.ts` — app-coupled methods extracted from `shared/ai-assistant/ai-assistant.service.ts`.

---

## PHASE 0 — Baseline

### Task 1: Capture the baseline build + a verification helper

**Files:**
- Create: `community/web/scripts/verify-common-leaf.sh`
- Create (git-ignored): `community/web/.baseline/` (reference build outputs + hashes)

**Interfaces:**
- Produces: `scripts/verify-common-leaf.sh` — a script other tasks run to assert both invariants and diff artifacts against the baseline.

- [ ] **Step 1: Produce a clean baseline production build**

```bash
cd community/web
npm ci
npm run build:prod
```

Expected: build completes; outputs exist under `target/generated-resources/ng/inetsoft/web/resources/{app,em,elements,viewer-element}` and the gulp bundles `.../app/elements.js`, `.../app/viewer-element.js`.

- [ ] **Step 2: Snapshot the baseline artifact hashes**

```bash
cd community/web
mkdir -p .baseline
( cd target/generated-resources && find ng gulp -type f -printf '%P\t%s\n' | sort ) > .baseline/artifact-manifest.txt
wc -l .baseline/artifact-manifest.txt
echo ".baseline/" >> .gitignore
```

Expected: `artifact-manifest.txt` has hundreds of lines (path + byte size per emitted file).

- [ ] **Step 3: Write the verification helper**

```bash
cat > community/web/scripts/verify-common-leaf.sh <<'EOF'
#!/usr/bin/env bash
# Asserts the two Phase-1 invariants for projects/shared (the `common` lib-to-be).
set -euo pipefail
cd "$(dirname "$0")/.."

echo "== Invariant 1: no imports from portal/ or em/ (excluding *.spec.ts) =="
back=$(grep -rlE 'from "(\.\./)+(portal|em)/' projects/shared --include='*.ts' 2>/dev/null | grep -v '\.spec\.ts' || true)
if [ -n "$back" ]; then echo "FAIL — back-edges remain:"; echo "$back"; exit 1; fi
echo "OK"

echo "== Invariant 2: no UI-toolkit imports =="
ui=$(grep -rlE '@ng-bootstrap/ng-bootstrap|@angular/material|from "bootstrap' projects/shared --include='*.ts' 2>/dev/null || true)
if [ -n "$ui" ]; then echo "FAIL — UI-toolkit deps remain:"; echo "$ui"; exit 1; fi
echo "OK"
EOF
chmod +x community/web/scripts/verify-common-leaf.sh
```

- [ ] **Step 4: Run it (expected to FAIL now — proves it detects the current violations)**

Run: `cd community/web && ./scripts/verify-common-leaf.sh`
Expected: prints `FAIL — back-edges remain:` and lists ~23 files, then exits non-zero. (This is the intended starting state.)

- [ ] **Step 5: Commit**

```bash
cd community/web
git add scripts/verify-common-leaf.sh .gitignore
git commit -m "Add Phase-1 common-leaf verification script and baseline gitignore

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## PHASE 1 — `projects/shared` becomes a clean UI-neutral leaf

> Each pull-down/move task uses this **codemod recipe** to rewrite imports across the workspace. Run it, then let the TypeScript build be the test.
>
> ```bash
> # Rewrite every import of an OLD module specifier to a NEW one, workspace-wide.
> # $1 = regex of the old path tail (no quotes), $2 = new relative-agnostic module path
> # Uses node so we can rewrite the relative "../" depth per file correctly.
> ```
>
> Because relative-path depth differs per importer, the reliable rewrite is: **move the file with `git mv`, then run the Angular build and fix each unresolved import to the file's new location.** For high-count targets a scripted rewrite is given inline per task.

### Task 2: Pull `identity-id` + `idenity-id-with-label` down into `common`

`identity-id.ts` has zero imports (pure); `idenity-id-with-label.ts` imports only `identity-id`. 45 `em` files reference them (75 lines workspace-wide). They are the foundational identity model and belong in `common`.

**Files:**
- Move: `projects/em/src/app/settings/security/users/identity-id.ts` → `projects/shared/data/identity/identity-id.ts`
- Move: `projects/em/src/app/settings/security/users/idenity-id-with-label.ts` → `projects/shared/data/identity/idenity-id-with-label.ts`
- Modify: every file importing either (codemod below).

**Interfaces:**
- Produces: `IdentityId`, `convertToKey`, `equalsIdentity` (whatever `identity-id.ts` currently exports) now importable from `projects/shared/data/identity/identity-id`; `IdentityIdWithLabel` from `.../idenity-id-with-label`.

- [ ] **Step 1: Move the two files**

```bash
cd community/web
mkdir -p projects/shared/data/identity
git mv projects/em/src/app/settings/security/users/identity-id.ts projects/shared/data/identity/identity-id.ts
git mv projects/em/src/app/settings/security/users/idenity-id-with-label.ts projects/shared/data/identity/idenity-id-with-label.ts
```

- [ ] **Step 2: Rewrite all importers with a scripted codemod**

```bash
cd community/web
node - <<'EOF'
const fs = require('fs'), path = require('path');
const roots = ['projects'];
const targets = {
  'settings/security/users/identity-id': 'projects/shared/data/identity/identity-id',
  'settings/security/users/idenity-id-with-label': 'projects/shared/data/identity/idenity-id-with-label',
};
function walk(d){for(const e of fs.readdirSync(d,{withFileTypes:true})){const p=path.join(d,e.name);
  if(e.isDirectory())walk(p); else if(e.name.endsWith('.ts'))rewrite(p);}}
function rewrite(file){let s=fs.readFileSync(file,'utf8');let changed=false;
  s=s.replace(/from\s+"([^"]+)"/g,(m,spec)=>{
    for(const [tail,abs] of Object.entries(targets)){
      if(spec.endsWith(tail)){
        const rel=path.relative(path.dirname(file),path.join(process.cwd(),abs)).replace(/\\/g,'/');
        const fixed=rel.startsWith('.')?rel:'./'+rel; changed=true; return `from "${fixed}"`;
      }
    }
    return m;
  });
  if(changed)fs.writeFileSync(file,s);}
roots.forEach(walk);
console.log('codemod done');
EOF
```

- [ ] **Step 3: Verify no stale references to the old em path remain**

Run: `cd community/web && grep -rn "settings/security/users/identity-id\"\|settings/security/users/idenity-id-with-label\"" projects --include=*.ts`
Expected: no output.

- [ ] **Step 4: Type-check the affected projects**

Run: `cd community/web && npx ng build em --configuration development && npx ng build portal --configuration development`
Expected: both builds succeed (all identity imports resolve to the new location).

- [ ] **Step 5: Commit**

```bash
cd community/web
git add -A
git commit -m "Pull identity-id/idenity-id-with-label down into common (projects/shared/data/identity)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

### Task 3: Pull the pre-verified clean portal model targets down into `common`

These targets were verified UI-clean and are simple model/type or leaf-service files: `time-instant`, `data-ref`, `chart-ref`, `xschema`, `common-kv-model` (from `portal/common/data`), `local-storage.util` (from `portal/common/util`), `tree-node-model` (from `portal/widget/tree`), `server-path-info-model` and `dynamic-value-model` (from `portal/vsobjects/model`), `repository-tree-action.enum` (from `portal/widget/repository-tree`), and the two services `stomp-client.service` depends on: `base-href.service` and `heartbeat-worker.service` (from `portal/common/services`).

**Files:**
- Move model/type targets into `projects/shared/data/model/` (create dir).
- Move `local-storage.util.ts`, `base-href.service.ts`, `heartbeat-worker.service.ts` → `projects/shared/util/`.
- Modify: all importers (codemod).

**Interfaces:**
- Produces: each type importable from its new `projects/shared/...` location. Exports are unchanged (move only).

- [ ] **Step 1: Confirm each target is still dep-clean before moving**

```bash
cd community/web
for t in \
  projects/portal/src/app/common/data/time-instant \
  projects/portal/src/app/common/data/data-ref \
  projects/portal/src/app/common/data/chart-ref \
  projects/portal/src/app/common/data/xschema \
  projects/portal/src/app/common/data/common-kv-model \
  projects/portal/src/app/common/util/local-storage.util \
  projects/portal/src/app/widget/tree/tree-node-model \
  projects/portal/src/app/vsobjects/model/server-path-info-model \
  projects/portal/src/app/vsobjects/model/dynamic-value-model \
  projects/portal/src/app/widget/repository-tree/repository-tree-action.enum \
  projects/portal/src/app/common/services/base-href.service \
  projects/portal/src/app/common/services/heartbeat-worker.service ; do
    echo "== $t =="
    grep -E '^import' "$t.ts" | grep -vE 'from "\./|from "@angular|rxjs' || echo "  (no cross-module imports)"
done
```

Expected: for each, either no imports or imports that resolve within the same folder / Angular / rxjs only. **If any target imports another `portal/`/`em/` module, do NOT move it in this task — record it and handle it in Task 4** (it needs its dep pulled first or an inversion).

- [ ] **Step 2: Move the confirmed-clean targets**

```bash
cd community/web
mkdir -p projects/shared/data/model
for f in common/data/time-instant common/data/data-ref common/data/chart-ref \
         common/data/xschema common/data/common-kv-model \
         vsobjects/model/server-path-info-model vsobjects/model/dynamic-value-model \
         widget/tree/tree-node-model widget/repository-tree/repository-tree-action.enum ; do
    git mv "projects/portal/src/app/$f.ts" "projects/shared/data/model/$(basename $f).ts"
done
git mv projects/portal/src/app/common/util/local-storage.util.ts projects/shared/util/local-storage.util.ts
git mv projects/portal/src/app/common/services/base-href.service.ts projects/shared/util/base-href.service.ts
git mv projects/portal/src/app/common/services/heartbeat-worker.service.ts projects/shared/util/heartbeat-worker.service.ts
```

- [ ] **Step 3: Rewrite importers (reuse the codemod from Task 2, Step 2, with this `targets` map)**

```js
const targets = {
  'common/data/time-instant': 'projects/shared/data/model/time-instant',
  'common/data/data-ref': 'projects/shared/data/model/data-ref',
  'common/data/chart-ref': 'projects/shared/data/model/chart-ref',
  'common/data/xschema': 'projects/shared/data/model/xschema',
  'common/data/common-kv-model': 'projects/shared/data/model/common-kv-model',
  'vsobjects/model/server-path-info-model': 'projects/shared/data/model/server-path-info-model',
  'vsobjects/model/dynamic-value-model': 'projects/shared/data/model/dynamic-value-model',
  'widget/tree/tree-node-model': 'projects/shared/data/model/tree-node-model',
  'widget/repository-tree/repository-tree-action.enum': 'projects/shared/data/model/repository-tree-action.enum',
  'common/util/local-storage.util': 'projects/shared/util/local-storage.util',
  'common/services/base-href.service': 'projects/shared/util/base-href.service',
  'common/services/heartbeat-worker.service': 'projects/shared/util/heartbeat-worker.service',
};
```

Run the same node script with this map substituted.

- [ ] **Step 4: Build portal + em to confirm all imports resolve**

Run: `cd community/web && npx ng build portal --configuration development && npx ng build em --configuration development`
Expected: both succeed.

- [ ] **Step 5: Commit**

```bash
cd community/web && git add -A
git commit -m "Pull clean portal model types down into common (data/model, util)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

### Task 4: Pull down the remaining em setting-model targets (with per-file gate)

Remaining back-edge targets in `em`: `common/util/table/table-model`, `settings/security/resource-permission/resource-permission-model`, `settings/security/security-provider/security-provider-model/connection-status`, `settings/content/repository/import-export/selected-asset-model`, `settings/content/repository/repository-tree-node`. Plus any portal target deferred from Task 3, Step 1.

**Files:** move each verified-clean target into `projects/shared/data/model/`; modify importers.

**Interfaces:** each type importable from `projects/shared/data/model/<name>`.

- [ ] **Step 1: Gate each target — must be UI-clean AND not import another `portal/`/`em/` module**

```bash
cd community/web
for t in \
  projects/em/src/app/common/util/table/table-model \
  projects/em/src/app/settings/security/resource-permission/resource-permission-model \
  projects/em/src/app/settings/security/security-provider/security-provider-model/connection-status \
  projects/em/src/app/settings/content/repository/import-export/selected-asset-model \
  projects/em/src/app/settings/content/repository/repository-tree-node ; do
    echo "== $t =="
    grep -lE '@ng-bootstrap|@angular/material|from "bootstrap' "$t.ts" && echo "  UI-COUPLED" || echo "  ui-clean"
    grep -E '^import' "$t.ts" | grep -E 'from "(\.\./)+(portal|em)/' || echo "  no app back-imports"
done
```

Expected output per target: `ui-clean` and `no app back-imports` → safe to pull down. **For any target that is `UI-COUPLED` or has app back-imports:** it is not a pull-down. Instead, move the *offending `shared` file's usage* up — i.e., relocate the specific `shared` model that references it into `projects/portal/src/app` or `projects/em/src/app` (whichever is its sole non-`common` consumer), following the same move+codemod pattern. Record the decision in the commit message.

- [ ] **Step 2: Move the confirmed-clean targets** (adjust the list to those that passed Step 1)

```bash
cd community/web
for f in common/util/table/table-model \
         settings/security/resource-permission/resource-permission-model \
         settings/security/security-provider/security-provider-model/connection-status \
         settings/content/repository/import-export/selected-asset-model \
         settings/content/repository/repository-tree-node ; do
    git mv "projects/em/src/app/$f.ts" "projects/shared/data/model/$(basename $f).ts"
done
```

- [ ] **Step 3: Rewrite importers** using the Task 2 codemod with the matching `targets` map (old em tail → `projects/shared/data/model/<basename>`).

- [ ] **Step 4: Build em + portal**

Run: `cd community/web && npx ng build em --configuration development && npx ng build portal --configuration development`
Expected: both succeed.

- [ ] **Step 5: Commit**

```bash
cd community/web && git add -A
git commit -m "Pull clean em setting-model types down into common/data/model

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

### Task 5: Extract binding functions out of `shared/util/tool.ts`

`tool.ts` (imported 478×) uses app types only in three functions: `clearBindingData(objectType, bmodel: BindingModel)`, `getCurrentSourceLabel(model: BindingModel)`, and `getSortedSelectedHeaderCell(model: VSCrosstabModel, ...): BaseTableCellModel[]`. These are viewsheet-binding operations with portal-only callers. Move them into a new `app-shared`-side helper.

**Files:**
- Create: `projects/portal/src/app/common/util/binding-tool.ts`
- Modify: `projects/shared/util/tool.ts` (remove the 3 functions + their 9 back-edge imports)
- Modify: call sites of those 3 functions (repoint to `binding-tool`).

**Interfaces:**
- Produces: `binding-tool.ts` exporting `clearBindingData`, `getCurrentSourceLabel`, `getSortedSelectedHeaderCell` with identical signatures.

- [ ] **Step 1: Find the callers of the three functions**

```bash
cd community/web
grep -rnE "Tool\.(clearBindingData|getCurrentSourceLabel|getSortedSelectedHeaderCell)\b" projects --include=*.ts
```

Expected: a list of call sites (verify none are under `projects/em/` — if any are, stop and re-evaluate; the design assumes portal-only callers).

- [ ] **Step 2: Create `binding-tool.ts` with the three functions moved verbatim**

Copy the three function bodies (lines ~237–290 and ~864+ of `tool.ts`) and the 9 imports (`BAggregateRef`, `BDimensionRef`, `BindingModel`, `ChartBindingModel`, `SourceInfo`, `CrosstabBindingModel`, `TableBindingModel`, `BaseTableCellModel`, `VSCrosstabModel`) into a new namespace, adjusting import paths to be relative from `common/util/binding-tool.ts`. Structure:

```ts
// license header block (copy from tool.ts)
import { BAggregateRef } from "../../binding/data/b-aggregate-ref";
import { BDimensionRef } from "../../binding/data/b-dimension-ref";
import { BindingModel } from "../../binding/data/binding-model";
import { ChartBindingModel } from "../../binding/data/chart/chart-binding-model";
import { SourceInfo } from "../../binding/data/source-info";
import { CrosstabBindingModel } from "../../binding/data/table/crosstab-binding-model";
import { TableBindingModel } from "../../binding/data/table/table-binding-model";
import { BaseTableCellModel } from "../../vsobjects/model/base-table-cell-model";
import { VSCrosstabModel } from "../../vsobjects/model/vs-crosstab-model";
// plus any intra-tool helpers these three functions call — import them from "../../../../shared/util/tool" (Tool namespace)

export namespace BindingTool {
   export function clearBindingData(objectType: string, bmodel: BindingModel): void { /* moved body */ }
   export function getCurrentSourceLabel(model: BindingModel): string { /* moved body */ }
   export function getSortedSelectedHeaderCell(model: VSCrosstabModel, /* ...same params */): BaseTableCellModel[] { /* moved body */ }
}
```

- [ ] **Step 3: Delete the three functions and their 9 imports from `tool.ts`**

Remove lines 21–33 imports listed above and the three `export function` bodies. Leave the rest of `Tool` untouched.

- [ ] **Step 4: Repoint call sites** from `Tool.clearBindingData(...)` etc. to `BindingTool.clearBindingData(...)`, adding `import { BindingTool } from ".../common/util/binding-tool";` in each caller (path relative per file).

- [ ] **Step 5: Verify tool.ts is clean of these back-edges and build**

```bash
cd community/web
grep -nE "portal/src/app/(binding|vsobjects)" projects/shared/util/tool.ts || echo "tool.ts clean"
npx ng build portal --configuration development
```

Expected: `tool.ts clean`; portal build succeeds.

- [ ] **Step 6: Commit**

```bash
cd community/web && git add -A
git commit -m "Extract binding functions from common tool.ts into app-shared binding-tool

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

### Task 6: Split `shared/ai-assistant/ai-assistant.service.ts`

`em` consumes `AiAssistantService` (2×) but not its viewsheet-binding context methods. Keep the UI-neutral core in `common`; move the app-coupled methods (`setBindingContext`, `setDataContext`, `setCalcTableBindingContext`, `setCalcTableRetrievalScriptContext`, `setCalcTableScriptContext`, `setDateComparisonContext`, `setScriptContext`) and their imports (`BindingModel`, `ChartBindingModel`, `CellBindingInfo`, `CrosstabBindingModel`, `getAvailableFields`, `getChartBindingContext`, `getCrosstabBindingContext`, `CalcTableLayout`, `VSObjectModel`) into an `app-shared` subclass.

**Files:**
- Create: `projects/portal/src/app/binding/services/assistant/ai-assistant-binding.service.ts`
- Modify: `projects/shared/ai-assistant/ai-assistant.service.ts` (remove app-coupled methods/imports; expose the generic string-based context setters the subclass calls; keep `convertToKey` import now pointing at the pulled-down `common/data/identity/identity-id` from Task 2).
- Modify: portal call sites that use the binding-context methods → inject `AiAssistantBindingService`.

**Interfaces:**
- Consumes: `AiAssistantService` (common core) generic setters — confirm/keep public methods the subclass needs (e.g. a low-level `setContext(key, value)` accumulator). If such a generic setter does not exist, add one to the core and have the moved methods call it.
- Produces: `AiAssistantBindingService extends AiAssistantService` in `app-shared` exposing the 7 moved methods with identical signatures.

- [ ] **Step 1: Read the service and identify the generic setter(s) the 7 methods rely on**

```bash
cd community/web && sed -n '250,376p' projects/shared/ai-assistant/ai-assistant.service.ts
```

Determine which private/public fields (`bindingContext`, `calcTableCellBindings`, etc.) the 7 methods mutate. Any field they mutate must remain on the core class (or be exposed via a `protected` accessor) so the subclass can set it.

- [ ] **Step 2: In the core (`common`) service, make the shared state `protected` and remove the 7 methods + their 9 app imports**

Change the mutated fields (e.g. `calcTableCellBindings`, the `bindingContext` subject) to `protected` visibility so the subclass can write them. Delete the 7 method bodies and the imports on lines 24–36 that reference `portal/`. Repoint the `convertToKey` import (line 23) to `../data/identity/identity-id`.

- [ ] **Step 3: Create the subclass in `app-shared`**

```ts
// license header block
import { Injectable } from "@angular/core";
import { AiAssistantService } from "../../../../../shared/ai-assistant/ai-assistant.service";
import { BindingModel } from "../../data/binding-model";
import { ChartBindingModel } from "../../data/chart/chart-binding-model";
import { CellBindingInfo } from "../../data/table/cell-binding-info";
import { CrosstabBindingModel } from "../../data/table/crosstab-binding-model";
import { getAvailableFields } from "./available-fields-helper";
import { getChartBindingContext } from "./chart-context-helper";
import { getCrosstabBindingContext } from "./crosstab-context-helper";
import { CalcTableLayout } from "../../../common/data/tablelayout/calc-table-layout";
import { VSObjectModel } from "../../../vsobjects/model/vs-object-model";

@Injectable()
export class AiAssistantBindingService extends AiAssistantService {
   // the 7 methods moved verbatim, now compiling against the protected core state
}
```

(Adjust relative import depths to the real file location.)

- [ ] **Step 4: Repoint providers and portal call sites**

Wherever portal provided/injected `AiAssistantService` *and used a binding-context method*, provide `AiAssistantBindingService` instead (and inject that type). Where `em` (and any portal code using only the core) injects `AiAssistantService`, leave it. Update `app.config.ts` / relevant module providers.

- [ ] **Step 5: Verify the file is clean and both apps build**

```bash
cd community/web
grep -nE "portal/src/app|em/src/app" projects/shared/ai-assistant/ai-assistant.service.ts || echo "ai-assistant core clean"
npx ng build portal --configuration development && npx ng build em --configuration development
```

Expected: `ai-assistant core clean`; both builds succeed.

- [ ] **Step 6: Commit**

```bash
cd community/web && git add -A
git commit -m "Split ai-assistant.service: UI-neutral core in common, binding subclass in app-shared

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

### Task 7: Relocate the two UI-toolkit leaks

- [ ] **Step 1: Move the Material paginator into `em`**

```bash
cd community/web
git mv projects/shared/util/localized-mat-paginator.ts projects/em/src/app/common/util/localized-mat-paginator.ts
grep -rn "shared/util/localized-mat-paginator" projects --include=*.ts
```

Repoint each importer (all should be under `projects/em/`) with the Task 2 codemod (`{'util/localized-mat-paginator':'projects/em/src/app/common/util/localized-mat-paginator'}`).

- [ ] **Step 2: Move the parameter guard into portal**

```bash
cd community/web
mkdir -p projects/portal/src/app/common/guard
git mv projects/shared/util/guard/global-parameter-guard.service.ts projects/portal/src/app/common/guard/global-parameter-guard.service.ts
grep -rn "shared/util/guard/global-parameter-guard.service" projects --include=*.ts
```

Repoint its single importer (portal). Its own imports of `component-tool`, `show-hyperlink.service`, `parameter-dialog.component`, `parameter-page-model`, `replet-parameter-model` now resolve as normal intra-portal relative imports (fix the relative depth).

- [ ] **Step 3: Build both apps**

Run: `cd community/web && npx ng build em --configuration development && npx ng build portal --configuration development`
Expected: both succeed.

- [ ] **Step 4: Commit**

```bash
cd community/web && git add -A
git commit -m "Relocate UI-toolkit leaks out of common: mat-paginator to em, param guard to portal

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

### Task 8: Handle back-edges in `shared` **spec** files + final Phase-1 gate

The earlier `grep` excluded specs, but `schedule/schedule-users.service.tl.spec.ts` imported an `em` identity path. After Task 2 that import target moved into `common`; confirm and fix any remaining spec references.

- [ ] **Step 1: Rewrite spec back-edges**

```bash
cd community/web
grep -rlE 'from "(\.\./)+(portal|em)/' projects/shared --include='*.spec.ts'
```

For each hit, repoint to the new `common` location (identity types now under `data/identity`). Fix relative depth.

- [ ] **Step 2: Run BOTH invariant greps (must be empty)**

Run: `cd community/web && ./scripts/verify-common-leaf.sh`
Expected: prints `OK` for both invariants and exits 0.

- [ ] **Step 3: Run the full test suites**

```bash
cd community/web
npm run test:portal && npm run test:em && npm run test:em:tl
```

Expected: all suites pass (matching pre-refactor pass/fail baseline — note: `main` may have pre-existing unstable tests per CLAUDE.md; compare against Phase 0 results, not an assumption of all-green).

- [ ] **Step 4: Full production build + artifact diff against baseline**

```bash
cd community/web
npm run build:prod
( cd target/generated-resources && find ng gulp -type f -printf '%P\t%s\n' | sort ) > .baseline/artifact-manifest-after.txt
diff <(cut -f1 .baseline/artifact-manifest.txt) <(cut -f1 .baseline/artifact-manifest-after.txt) || true
```

Expected: the set of emitted file *paths* is unchanged (hashed bundle names may differ in hash but not in count/structure); no project is missing outputs. Investigate any path that appears/disappears.

- [ ] **Step 5: Commit**

```bash
cd community/web && git add -A
git commit -m "Fix shared spec back-edges; Phase 1 complete — common is a clean UI-neutral leaf

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Phases 2–5 (planned separately after Phase 1 lands)

These are intentionally **not** enumerated here. Their concrete steps depend on the exact post-Phase-1 tree and on iterative `ng-packagr` output that cannot be pre-specified without guessing. Each will be its own plan document:

- **Phase 2 — Promote `common` to a buildable library.** Rename `projects/shared` → `projects/common`; add `package.json`/`ng-package.json`/`tsconfig.lib(.prod).json`; add ~11 per-folder secondary entry points (`common/util`, `common/data`, …); codemod ~1900 `shared/*` → `common/<entrypoint>` imports; register the library and build order in `angular.json`; resolve barrel symbol-collision errors as they surface. Gate: portal + em build consuming `common` dist; em tests pass.
- **Phase 3 — Factor `app-shared` library.** Move the `app/` tree + shared top-level files into `projects/app-shared`; author the flat `public-api.ts` (~50 shell-facing symbols); portal becomes a shell; resolve partial-compilation errors. Gate: portal builds on both libs.
- **Phase 4 — Split `elements` + `viewer-element` into own app projects.** New `projects/elements` and `projects/viewer-element`; fix the two gulp scss source paths; preserve `outputPath` bases. Gate: all four outputs + gulp bundles diff-identical to baseline.
- **Phase 5 — Cleanup.** Dev/prod dual-resolution tsconfig `paths`; update `package.json` scripts (`build`, `build:prod`, `build:watch`, `test*`, `lint*`); update docs.

---

## Self-Review Notes

- **Spec coverage (Phase 0–1 portion):** Invariant 1 (Tasks 2–8), Invariant 2 (Task 7), the two named UI leaks (Task 7), `tool.ts` push-up (Task 5), `ai-assistant.service` inversion (Task 6), baseline + artifact-diff verification (Tasks 1, 8). The spec's remaining sections (public API, build model, gulp paths, elements/viewer split) are Phases 2–5, scoped above.
- **No unresolved back-edge left unaddressed:** all 23 offending files map to a task — identity/model pull-downs (2,3,4), `tool.ts` (5), `ai-assistant.service` (6), `global-parameter-guard` (7), `stomp-client.service` (its targets `base-href.service`/`heartbeat-worker.service` are verified clean and are pulled down in Task 3), and specs (8).
- **Type consistency:** moved files keep their existing exports verbatim; extracted namespaces (`BindingTool`) and the subclass (`AiAssistantBindingService`) preserve original signatures.
