# Geometric Strip Suppression (L″) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Decide whether an anchored mini-toolbar is drawn by measuring the assembly's actual title lane against the strip's height, instead of approximating that fit from the org's density setting.

**Architecture:** Two pure predicates in `mini-toolbar.service.ts` stop calling `GuiTool.isVizDensityAtLeastCompact()` and start taking a lane height. One new exported helper converts a model to a lane (`titleVisible ? titleFormat.height : 0`), so a hidden title yields 0 and fails the same comparison — one rule covers both the too-short-lane and the hidden-title cases. Three call sites pass the lane through. The density helper and the two right-edge reserve members retire.

**Tech Stack:** Angular 21 / TypeScript 5.9, Vitest.

**Spec:** `docs/superpowers/specs/2026-08-25-geometric-strip-suppression-design.md` — read it before starting. Its §2 explains why this could not ship before L′, §3.4 warns about a comment that will mislead you, and §5.1 names the one risk worth designing a test around.

## Global Constraints

- **Branch:** `viz-updates`.
- **One commit per task.**
- **This change is browser-only.** Everything lives under `web/projects/portal/src`. No Java, no server, no export pass, no persisted state, no migration. If you find yourself opening a `.java` file, stop and report.
- **The threshold is 24** — the strip's own height, so a lane of 24 holds it exactly. **Corrected
  2026-08-25: this plan shipped it at 26 and that was wrong.** Decision 2's 1px of clearance existed only to
  keep the pill's border off the lane edge, and §10.2 deletes that border; meanwhile lanes of 24 and 25 were
  losing their toolbar while the strip fitted. The density tiers are unaffected — dense 20 still suppressed,
  compact 26 and comfortable 30 still anchored. Boundary tests are 23 / 24 / 25. The strip is 24px at every density — it binds `--inet-control-height-sm`, which `mini-toolbar.component.scss:82` already does. **Do not use 30px or `--inet-control-height-md`.** `Chart Card Spec v3.dc.html` §02 says 30px; that is overruled by strip-and-lane decisions 1 and 2, and at 30 the threshold would be 32 and compact's 26px lane could never hold the strip.
- **Do NOT delete `ANCHORED_ASSEMBLY_TYPES`, `AbstractVSActions.resident`, or `VSObjectContainerComponent.isKebabResident`**, despite in-code comments calling them `TEMPORARY` and saying they are "deleted together with this predicate." Those comments refer to rollout slices 4 and 5 (the container and the calendar joining the anchored set), a separate roadmap item. See spec §3.4.
- **Do not change resting semantics.** Whether the strip is drawn *at rest* versus revealed on hover is §10.1, a separate roadmap item scoped by pointer capability. This plan only changes whether the lane holds the strip *at all*.
- **Do not change the strip's appearance.** No surface, border, fill or glyph-tone changes — that is §10.2, also separately tracked.
- **Never run the full frontend TL suite.** Scope every run with `--include` naming a single spec file. An unfiltered TL run exceeds the window and orphans multi-gigabyte worker processes.
- TypeScript conventions in this codebase: 3-space indent, `if(cond)` with no space after `if`, brace on the same line, `else {` on its own line, comments kept to a short clause.
- No design-doc, decision-record, ticket or plan-phase references in source comments. State rules directly.

---

## File Structure

| File | Responsibility | Task |
|---|---|---|
| `web/projects/portal/src/app/vsobjects/objects/mini-toolbar/mini-toolbar.service.ts` | the lane helper and the two predicates | 1 |
| `web/projects/portal/src/app/vsobjects/objects/mini-toolbar/mini-toolbar.service.spec.ts` | predicate behaviour, including the zero-margin boundary | 1 |
| `web/projects/portal/src/app/vsobjects/action/abstract-vs-actions.ts:143,222` | two call sites | 2 |
| `web/projects/portal/src/app/vsobjects/objects/vs-object-container.component.ts:486` | one call site | 2 |
| `web/projects/portal/src/app/common/util/gui-tool.ts:96` | delete the now-unused density helper | 3 |
| `web/projects/portal/src/app/vsobjects/objects/vs-object-container.component.ts:570,588-603` | delete the right-edge reserve | 3 |

Task 1 is the rule and its tests. Task 2 wires it, and is the task that changes behaviour. Task 3 is pure deletion, separated because a reviewer could reasonably approve the behaviour change and reject a deletion, or vice versa. Task 4 is verification.

---

## Task 1: The lane helper and the geometric predicates

**Files:**
- Modify: `web/projects/portal/src/app/vsobjects/objects/mini-toolbar/mini-toolbar.service.ts` — `isAnchoredResident` at `:74-77`, `isAnchoredChromeSuppressed` at `:90-93`, and their doc comments above each
- Test: `web/projects/portal/src/app/vsobjects/objects/mini-toolbar/mini-toolbar.service.spec.ts` — the existing `describe("isAnchoredResident")` at `:63` and `describe("isAnchoredChromeSuppressed")` at `:95`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces, all exported from `mini-toolbar.service.ts`:
  - `anchoredLaneHeight(model: VSObjectModel): number` — the assembly's usable lane: `titleVisible ? titleFormat.height : 0`. Returns 0 for any model without a title lane.
  - `isAnchoredResident(objectType: string, vizModern: boolean, laneHeight: number): boolean`
  - `isAnchoredChromeSuppressed(objectType: string, vizModern: boolean, laneHeight: number): boolean`
  - `ANCHORED_LANE_MIN` — exported const, `26`.

  Task 2 calls all four.

**Nothing consumes the new signatures yet**, so this task compiles but changes no behaviour until Task 2. The existing three call sites still pass two arguments; TypeScript will flag them. That is expected and Task 2 fixes it — see Step 4.

- [ ] **Step 1: Rewrite the two existing describes as failing tests**

The existing tests stub density by adding a class to `document.body` and clean up in `afterEach`. That mechanism disappears here — the new tests pass a number instead, so no `afterEach` is needed.

Replace the whole of `describe("isAnchoredResident", ...)` (currently `:63-90`) and `describe("isAnchoredChromeSuppressed", ...)` (currently `:95` to the end of that describe) with:

```typescript
describe("isAnchoredResident", () => {
   it("is false when the modern gate is off, regardless of lane", () => {
      expect(isAnchoredResident("VSChart", false, 30)).toBe(false);
   });

   it("is false for a type outside the anchored set, however tall its lane", () => {
      expect(isAnchoredResident("VSCalendar", true, 30)).toBe(false);
   });

   it("is false at the dense lane, which cannot hold the strip", () => {
      expect(isAnchoredResident("VSChart", true, 20)).toBe(false);
   });

   it("is true at the compact and comfortable lanes", () => {
      expect(isAnchoredResident("VSChart", true, 26)).toBe(true);
      expect(isAnchoredResident("VSChart", true, 30)).toBe(true);
   });

   // the compact lane IS the threshold, so a pixel either side decides it and nothing else does
   it("switches on exactly at the threshold", () => {
      expect(isAnchoredResident("VSChart", true, 25)).toBe(false);
      expect(isAnchoredResident("VSChart", true, 26)).toBe(true);
      expect(isAnchoredResident("VSChart", true, 27)).toBe(true);
   });

   it("is false at a zero lane, which is what a hidden title resolves to", () => {
      expect(isAnchoredResident("VSChart", true, 0)).toBe(false);
   });
});

// The other half of the split. Deliberately not the negation of isAnchoredResident: negation would be
// true for every non-anchored type and every gate-off assembly, stripping toolbars that ship today.
describe("isAnchoredChromeSuppressed", () => {
   it("is true for an anchored type whose lane cannot hold the strip", () => {
      expect(isAnchoredChromeSuppressed("VSChart", true, 20)).toBe(true);
      expect(isAnchoredChromeSuppressed("VSChart", true, 25)).toBe(true);
   });

   it("is true at a zero lane, which is what a hidden title resolves to", () => {
      expect(isAnchoredChromeSuppressed("VSChart", true, 0)).toBe(true);
   });

   it("is false once the lane holds the strip", () => {
      expect(isAnchoredChromeSuppressed("VSChart", true, 26)).toBe(false);
      expect(isAnchoredChromeSuppressed("VSChart", true, 30)).toBe(false);
   });

   it("is false with the gate off, so a legacy assembly keeps its toolbar", () => {
      expect(isAnchoredChromeSuppressed("VSChart", false, 20)).toBe(false);
   });

   it("is false for a type outside the anchored set, so its toolbar is untouched", () => {
      expect(isAnchoredChromeSuppressed("VSCalendar", true, 20)).toBe(false);
   });
});

describe("anchoredLaneHeight", () => {
   it("is the title format height when the title is visible", () => {
      expect(anchoredLaneHeight(<any> {titleVisible: true, titleFormat: {height: 26}})).toBe(26);
   });

   it("is zero when the title is hidden, whatever the format says", () => {
      expect(anchoredLaneHeight(<any> {titleVisible: false, titleFormat: {height: 30}})).toBe(0);
   });

   it("is zero for a model with no title lane at all", () => {
      expect(anchoredLaneHeight(<any> {})).toBe(0);
   });
});
```

Add `anchoredLaneHeight` to the existing import on `:18` alongside `isAnchoredAssemblyType`, `isAnchoredChromeSuppressed`, `isAnchoredResident` and `MiniToolbarService`.

- [ ] **Step 2: Run the tests to verify they fail**

Run:
```bash
cd community/web && npx ng test portal --include='**/mini-toolbar.service.spec.ts'
```

Expected: FAIL. `anchoredLaneHeight` is not exported, and the three-argument calls do not match the two-argument signatures.

- [ ] **Step 3: Implement the helper and the predicates**

In `mini-toolbar.service.ts`, add the constant and the helper immediately above `isAnchoredResident`:

```typescript
/** The shortest lane that can hold the 24px strip with 1px of clearance above and below. */
export const ANCHORED_LANE_MIN = 26;

/**
 * The lane available to the strip: the title's height when it has one, zero otherwise. A hidden
 * title has no lane, so it resolves to zero and fails the same comparison a too-short lane does.
 */
export function anchoredLaneHeight(model: VSObjectModel): number {
   const titled = <any> model;
   return titled?.titleVisible === false || !titled?.titleFormat ? 0 : (titled.titleFormat.height || 0);
}
```

Replace `isAnchoredResident`'s body:

```typescript
export function isAnchoredResident(objectType: string, vizModern: boolean,
                                   laneHeight: number): boolean
{
   return vizModern && laneHeight >= ANCHORED_LANE_MIN && isAnchoredAssemblyType(objectType);
}
```

Replace `isAnchoredChromeSuppressed`'s body:

```typescript
export function isAnchoredChromeSuppressed(objectType: string, vizModern: boolean,
                                           laneHeight: number): boolean
{
   return vizModern && laneHeight < ANCHORED_LANE_MIN && isAnchoredAssemblyType(objectType);
}
```

**No import is needed** — `mini-toolbar.service.ts:25` already imports `VSObjectModel`. Do not add a duplicate.

**Update both doc comments.** Each currently says the fit is "approximated by density for now" and references `GuiTool.isVizDensityAtLeastCompact`. Both statements become false. Rewrite them to say the fit is measured against the lane, and keep whatever each comment says about the two predicates not being negations of each other — that reasoning is still true and still load-bearing. **Leave every mention of `ANCHORED_ASSEMBLY_TYPES` being `TEMPORARY` exactly as it is**; see Global Constraints.

- [ ] **Step 4: Run the tests to verify they pass**

Run:
```bash
cd community/web && npx ng test portal --include='**/mini-toolbar.service.spec.ts'
```

Expected: PASS, including the file's pre-existing `isAnchoredAssemblyType` tests.

The three call sites elsewhere now fail type-checking because they pass two arguments. **That is expected at this task and Task 2 fixes it.** The scoped Vitest run above compiles only what that spec imports, so it passes. Do not "fix" the call sites here — they are Task 2's, and splitting them is what lets a reviewer judge the rule separately from its wiring.

- [ ] **Step 5: Commit**

```bash
cd community && git add web/projects/portal/src/app/vsobjects/objects/mini-toolbar/mini-toolbar.service.ts web/projects/portal/src/app/vsobjects/objects/mini-toolbar/mini-toolbar.service.spec.ts
git commit -m "feat(vsobjects): measure the title lane instead of guessing it from density"
```

---

## Task 2: Wire the three call sites

This is where behaviour changes.

**Files:**
- Modify: `web/projects/portal/src/app/vsobjects/action/abstract-vs-actions.ts` — `:143` and `:222`, plus the import on `:27`
- Modify: `web/projects/portal/src/app/vsobjects/objects/vs-object-container.component.ts` — `:486`, plus the import on `:57`

**Interfaces:**
- Consumes: `anchoredLaneHeight`, `isAnchoredResident`, `isAnchoredChromeSuppressed` from Task 1.
- Produces: nothing later tasks depend on.

- [ ] **Step 1: Wire `abstract-vs-actions.ts`**

At `:143`, inside the `resident` getter, replace:

```typescript
      return isAnchoredResident(this.model.objectType, this.model.vizModern);
```

with:

```typescript
      return isAnchoredResident(this.model.objectType, this.model.vizModern,
                                anchoredLaneHeight(this.model));
```

At `:222`, replace:

```typescript
      if(isAnchoredChromeSuppressed(this.model.objectType, this.model.vizModern) ||
```

with:

```typescript
      if(isAnchoredChromeSuppressed(this.model.objectType, this.model.vizModern,
                                    anchoredLaneHeight(this.model)) ||
```

Keep the `|| (modern && this.model.objectFormat.height < AbstractVSActions.ACTION_FLOOR)` clause that follows exactly as it is. **`ACTION_FLOOR` is 32 and measures the card's height; the lane threshold is 26 and measures the title lane.** They are different rules about different things and both stay.

Add `anchoredLaneHeight` to the existing import from the mini-toolbar service on `:27`.

- [ ] **Step 2: Wire `vs-object-container.component.ts`**

At `:486`, inside `isKebabResident`, replace:

```typescript
      return isAnchoredResident(object.objectType, object.vizModern);
```

with:

```typescript
      return isAnchoredResident(object.objectType, object.vizModern, anchoredLaneHeight(object));
```

Add `anchoredLaneHeight` to the existing import on `:57`.

`:473` (`isAnchoredKebab`) and the template's `[residentKebab]="isKebabResident(vsObject)"` at `:358` reach the predicate through `:486` and need no edit.

- [ ] **Step 3: Confirm no call site was missed**

Run:
```bash
cd community && grep -rn "isAnchoredResident(\|isAnchoredChromeSuppressed(" web/projects/portal/src --include=*.ts | grep -v "export function"
```

Expected: the three call sites above plus the spec file's calls. Every one must pass three arguments. A two-argument call left anywhere is a compile error waiting for the next build.

- [ ] **Step 4: Run the affected specs**

Run each separately — never unfiltered:
```bash
cd community/web && npx ng test portal --include='**/mini-toolbar.service.spec.ts'
cd community/web && npx ng test portal --include='**/abstract-vs-actions.spec.ts'
cd community/web && npx ng test portal --include='**/chart-actions.spec.ts'
cd community/web && npx ng test portal --include='**/table-actions.spec.ts'
```

Expected: PASS.

**If a snapshot fails, do not update it blindly.** `abstract-vs-actions.ts:222` reaches `ToolbarActionsHandler.copyActions([], this.showing)`, which empties the action list, and the `*-actions.spec.ts.snap` files capture action lists. A moved snapshot means that fixture's suppression state changed under the new rule. Read the fixture: if its model has no `titleFormat`, `anchoredLaneHeight` now returns 0 where density previously returned compact, and the fixture needs a lane rather than the snapshot needing an update. Report which fixtures moved and why before accepting any snapshot change.

- [ ] **Step 5: Commit**

```bash
cd community && git add web/projects/portal/src/app/vsobjects/action/abstract-vs-actions.ts web/projects/portal/src/app/vsobjects/objects/vs-object-container.component.ts
git commit -m "feat(vsobjects): suppress the anchored strip by the lane it actually has"
```

---

## Task 3: Retire the density helper and the right-edge reserve

Pure deletion. Separated from Task 2 so a reviewer can judge the behaviour change and the cleanup independently.

**Files:**
- Modify: `web/projects/portal/src/app/common/util/gui-tool.ts:96` — delete `isVizDensityAtLeastCompact`
- Modify: `web/projects/portal/src/app/vsobjects/objects/vs-object-container.component.ts` — delete `rightEdgeReserve` and `SORT_CONTROL_RESERVE` at `:588-603`, and its use at `:570`

**Interfaces:**
- Consumes: nothing.
- Produces: nothing.

- [ ] **Step 1: Confirm the density helper has no other consumers**

Run:
```bash
cd community && grep -rn "isVizDensityAtLeastCompact" web/projects/portal/src --include=*.ts --include=*.html
```

Expected: only its own declaration in `gui-tool.ts`. Task 1 removed the two predicate uses. **If anything else appears, stop and report** — the helper stays and only the reserve is deleted in this task.

- [ ] **Step 2: Delete the density helper**

Remove `isVizDensityAtLeastCompact` from `gui-tool.ts` (declared at `:96`), including its doc comment.

- [ ] **Step 3: Delete the right-edge reserve**

In `vs-object-container.component.ts`, delete the whole `rightEdgeReserve` method and the `SORT_CONTROL_RESERVE` constant (`:588-603`, including the doc comment above the method).

Then at `:570`, inside `getAnchoredToolbarWidth`, replace:

```typescript
      return object.objectFormat.width - (chart.paddingLeft || 0) - (chart.paddingRight || 0)
         - VSObjectContainer.rightEdgeReserve(object);
```

with:

```typescript
      return object.objectFormat.width - (chart.paddingLeft || 0) - (chart.paddingRight || 0);
```

The reserve existed so an overlaid kebab on a title-hidden table would miss the last column header's sort control. A title-hidden assembly now draws no kebab, so there is nothing to miss and title-hidden tables get those 22px of plot back.

**Check the doc comment above `getAnchoredToolbarWidth`** (it starts around `:560`) — if it mentions the reserve or the 22px, update it. Leave the rest of that comment alone; it explains the auto-margin right-alignment, which is unchanged.

- [ ] **Step 4: Confirm both are gone and nothing references them**

Run:
```bash
cd community && grep -rn "isVizDensityAtLeastCompact\|rightEdgeReserve\|SORT_CONTROL_RESERVE" web/projects/portal/src --include=*.ts --include=*.html
```

Expected: zero hits.

- [ ] **Step 5: Run the affected specs**

Run:
```bash
cd community/web && npx ng test portal --include='**/mini-toolbar.service.spec.ts'
```

**`vs-object-container.component.ts` has no spec file** — verified, it does not exist. So this task's deletions are covered by the type-checker and by Task 4's full suite and production build, not by a unit test. Do not create one; that is beyond this task.

- [ ] **Step 6: Commit**

```bash
cd community && git add web/projects/portal/src/app/common/util/gui-tool.ts web/projects/portal/src/app/vsobjects/objects/vs-object-container.component.ts
git commit -m "refactor(vsobjects): retire the density approximation and the reserve it needed"
```

---

## Task 4: Verification pass

No code.

- [ ] **Step 1: Full portal suite**

Run:
```bash
cd community/web && npx ng test portal
```

Expected: green. Baseline before this work was **1345/1345**. Record the number and account for any difference — Task 1 replaces 7 tests with 14, so a modest increase is expected and a decrease is a finding.

Do NOT run the TL suites.

- [ ] **Step 2: Production build**

Run:
```bash
cd community/web && npx ng build portal --configuration production
```

Expected: exit 0. This is the only check that the AOT compile is happy with the changed template bindings; the Vitest runs use the JIT path.

- [ ] **Step 3: The manual checks, in a browser**

Reference: lane heights are **20 / 26 / 30** for dense / compact / comfortable on a marked assembly.

1. **A marked chart at each density** — dense: no strip, right-click only. Compact and comfortable: strip present.
2. **Compact specifically, on all six anchored types** — chart, table, crosstab, calc table, selection list, selection tree. Compact's lane is 26 and the threshold is 26, so there is **zero margin**: if any type is a pixel short the strip vanishes there and only there. This is the check most likely to find a real defect.
3. **A marked chart with its title hidden** — no strip, and right-click reaches the same actions the strip offered.
4. **A title-hidden table** — no strip, and the last column header's sort control sits correctly now that the 22px reserve is gone.
5. **An unmarked assembly at every density** — unchanged from today, strip present as before.
6. **A non-anchored type** (gauge, text, range slider) — unchanged at every density.
7. **A marked chart in a gate-off org** — its lane still resolves from its mark, so the strip follows the lane, not the org.

- [ ] **Step 4: Record the result in the roadmap**

Add L″ to the roadmap's Done table and re-derive "What to pick up next" from the dependency picture rather than editing it in place, per that file's own instruction. Note that L″ retires the interim: `f5f568f12` approximated a 26px lane by keying off density, and the lane is now measured.

**Leave §10.1 and §10.2 on the "Ready now" table.** Both remain unbuilt, and §10.1's entry should still say that after L″ the predicate derives resting from geometry rather than pointer capability — that is now the accurate description.

- [ ] **Step 5: Commit the documentation update**

```bash
cd community && git add docs/superpowers/specs/lookfeel/chart-card-roadmap.md
git commit -m "docs(chart-card): record the geometric suppression shipped"
```

---

## Self-Review

**Spec coverage.** §1's rule → Task 1 Step 3. §1's hidden-title-needs-no-special-case → `anchoredLaneHeight` in Task 1 Step 3, tested in Step 1. §2's "why possible now" → context only, no task needed; the six-type coverage claim is exercised by Task 4 Step 3 item 2. §3.1's signatures → Task 1's Produces block. §3.2's three call sites → Task 2 Steps 1–2, with Step 3 as the completeness grep. §3.3's retirements → Task 3. §3.4's do-not-delete warning → Global Constraints and Task 1 Step 3's closing sentence. §4's title-hidden reversal → Task 4 Step 3 items 3 and 4. §5.1's zero-margin risk → Task 1 Step 1's threshold test and Task 4 Step 3 item 2. §5.2's table → Task 1 Step 1, all six rows. §5.3's snapshot risk → Task 2 Step 4's warning. §5.4's manual pass → Task 4 Step 3. §6's out-of-scope items → Global Constraints. §7's open questions → the clearance judgement lands in Task 4 Step 3 item 2; the two-clearance-values note lands in Task 2 Step 1.

**Placeholder scan.** No TBD, TODO, "similar to Task N", or "handle edge cases". Every code step carries the actual code. Two steps deliberately instruct the implementer to read before acting rather than follow the plan blindly — Task 2 Step 4 (read a moved fixture before updating a snapshot) and Task 3 Step 3 (check the doc comment above `getAnchoredToolbarWidth`). Both are cross-checks against a named location, not open questions. Both facts I initially left conditional are now verified and stated flatly: `mini-toolbar.service.ts:25` already imports `VSObjectModel`, and `vs-object-container.component.ts` has no spec file, so Task 3's deletions rest on the type-checker plus Task 4's suite and build.

**Type consistency.** `anchoredLaneHeight(model: VSObjectModel): number` is defined in Task 1 Step 3 and called in Task 2 Steps 1–2 under that exact name with a model argument. `isAnchoredResident` and `isAnchoredChromeSuppressed` are defined with `(objectType: string, vizModern: boolean, laneHeight: number)` in Task 1 Step 3 and called with three arguments in that order in Task 1 Step 1's tests and Task 2 Steps 1–2. `ANCHORED_LANE_MIN` is defined in Task 1 Step 3 and used only inside the two predicates. `ACTION_FLOOR` is pre-existing, is not redefined anywhere, and Task 2 Step 1 explicitly preserves it.

**Ordering constraint.** Task 1 leaves three call sites failing type-check; Task 2 fixes them. That is deliberate — it splits the rule from its wiring so a reviewer can reject one and keep the other — but it means **Task 1 must not be shipped alone**, and Task 3's first grep depends on Task 1 having already removed the two predicate uses of the density helper.
