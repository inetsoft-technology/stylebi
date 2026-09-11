# Chart Palette Revert — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make a chart's categorical colour palette revert with its assembly, so pressing Revert on a dashboard restores classic chart colours instead of leaving the modern palette in place.

**Architecture:** `VSChartPaletteDefaults.applyModernPalette` is a *forward-only* mutator: it writes the modern palette onto a `CategoricalColorFrame` when the context is modern and does nothing otherwise. Nothing ever writes the legacy palette back, so clearing an assembly's mark does not undo it. The fix restores the palette at the phase's own enumeration point — `ChartVSAssemblyInfo.seedChromeDefaults`, beside `barCornerRadius` and `smoothLines` — rather than making the render-time applier total.

**Tech Stack:** Java 21 (core), JUnit 5. No frontend change.

**Spec:** No design document prescribes this; it is a gap found by manual verification of M-P6. The governing decisions are `docs/superpowers/specs/lookfeel/seeded-value-reversibility-decisions.md` decision 13 (Revert is the mirror of Modernize and they cannot drift apart) and `docs/superpowers/specs/2026-08-14-seed-mark-forward-half-design.md` §5's P6. The binding contract is stated in the code itself, in `VizModernizeUtil.revert`'s doc comment (`:88-90`):

    No reverser is written: with the mark cleared, seedChromeDefaults writes the legacy branch of
    every ternary, which is the identical call a gate-off creation makes. A property added to what
    Modernize does is therefore reverted by the same edit to the same method, or it is not added.

The palette was added to what Modernize does **outside** that method. That is the root cause, and it is why the fix goes back into `seedChromeDefaults` rather than into the applier.

**Found:** 2026-08-24, by the human partner's manual pass over M-P6. Every other manual check passed; this was the only failure.

**Verified against:** community `viz-updates` at `27ea5fdd5` (M-P6 committed). Every line cited below was read at that state.

## Revision history

**Revised 2026-08-24, after plan review.** The first draft got three things wrong and the review settled them. Read this section before the tasks — the original Tasks 1-3 are gone, replaced by what the review decided.

1. **Task 1's empirical question is already answered, and its grep would have answered it wrong.** The first draft claimed `defaultColors` "is referenced in only three files repo-wide … and none of them is an XML writer" and proposed a grep filtered on `writexml|parsexml|tostring`. Both are wrong. `CategoricalColorFrameWrapper.writeContents()` (`:182-201`) writes a `<colors>` element from `frame.getDefaultColor(i)`, and `parseContents()` (`:265+`) parses it back. The filtered grep misses those because the methods are `writeContents`/`parseContents`. **The palette is persisted.** The original Task 1 is deleted — there is nothing left to establish.
2. **"The trap" as first written does not happen; a different one does.** `defaultColors` is not user-authorable through the UI. The frontend only *reads* it (`categorical-color-pane.component.ts:201,212`, `b-categorical-color-pane.component.ts:100`); a user's colour pick goes to `colors[]` → `CategoricalColorFrameWrapper.setColor` → `CategoricalColorFrame.setUserColor` → the separately-persisted `userColors` map. `ChartPropertyService.updateCategoricalColor` (`:860-875`) writes `defaultColors` back verbatim from what the model read, so the dialog is a pure round-trip and never collides with the modern seed. The residual write path is a customer chart script (`setDefaultColors`/`setDefaultColor` are `@TernMethod`), which the modern branch already clobbers today — the exposure is symmetric, not new. **A discriminator is therefore not needed and the original Task 3 is deleted.** The real leak the first draft missed is recorded in Task 2 below.
3. **Making the applier total would activate a dead call site.** `CSSProcessor.java:474` passes `VizContext.LEGACY`, so it is a guaranteed no-op today. A total applier turns it into a writer that stamps the legacy 40-colour palette onto every report chart's frame on every CSS pass, and does the same at the other two sites in every gate-off org — a behaviour change across paths the bug never touched. **Ruled: seed at the enumeration point instead.** `applyModernPalette` stays forward-only and all three of its call sites keep their current behaviour exactly.

## Global Constraints

- **This is a separate commit from M-P6.** M-P6 is committed at `27ea5fdd5` and manually verified apart from this issue. Do not amend it.
- **`applyModernPalette` keeps its current signature, name and forward-only behaviour.** Its three call sites are not to be touched. Any change that makes one of them start writing where it does not write today is out of scope and a defect.
- **No design-doc, decision-record, ticket or plan-phase references in source comments.** State rules directly. Documentation files are exempt.
- Java conventions: 3-space indent, `if(cond)` with no space after `if`, brace on the same line, short-clause comments.
- **Never run the full TL suite** — it exceeds the foreground window and orphans multi-GB workers. No TL run is needed here; there is no frontend change.
- `./mvnw` is slow (10-40 min). Allow up to 600000 ms per invocation.
- Branch: `viz-updates`.

---

## The defect

`core/src/main/java/inetsoft/uql/viewsheet/internal/VSChartPaletteDefaults.java:70-74`:

```java
   public static void applyModernPalette(CategoricalColorFrame frame, VizContext ctx) {
      if(frame != null && ctx.modern) {
         frame.setDefaultColors(activePalette(ctx));
      }
   }
```

Forward-only. Once a frame has been given the modern palette, nothing restores the legacy one when the mark is cleared.

Three call sites, all read/edit-time, **all unchanged by this plan**:

| Site | Context passed | Behaviour after this fix |
|---|---|---|
| `report/composition/graph/VGraphPair.java:1300` | mark-derived, per assembly | unchanged: fires for a marked chart, no-ops otherwise |
| `report/internal/graph/ChangeChartProcessor.java:1893` | `VizContext.of(info)` | unchanged: same |
| `report/css/CSSProcessor.java:474` | `VizContext.LEGACY` | unchanged: still inert |

`VizModernizeUtil.revert()` re-seeds through `VSAssemblyInfo.seedChromeDefaults`, which touches formats and plot values and **never touches colour frames**. `ChartVSAssemblyInfo.seedChromeDefaults` (`:100-119`) already writes both branches for `barCornerRadius` and `smoothLines`; the palette is the one gate-dependent chart value missing from it.

## Why M-P6 missed this

The design's premise is that `seedChromeDefaults` is the single enumeration point for every gate-dependent value, and it reasoned about two categories:

1. **Read-time resolvers** — recompute from the context on every read, so clearing the mark reverts them for free.
2. **Persisted DEFAULT-tier seeds** — written once at creation, and `seedChromeDefaults` owns un-writing them.

The palette is a third category the design never named: **a read-time applier with a memory.** It runs at render time with the correct mark-derived context, but it *mutates* long-lived state, so "do not apply modern" is not the same as "restore legacy". Anything else of that shape has the same bug, which is why Task 2 sweeps for it.

## Why the fix goes in `seedChromeDefaults`

Three properties the alternative (a total applier) does not have:

- It fires on revert and creation only, not on every render of every chart in every org.
- It leaves the report path and all three applier call sites exactly as they are — including `CSSProcessor:474`, which is inert today and must stay inert.
- It is the contract `VizModernizeUtil.revert` already documents, and it sits beside the two plot values that already do this correctly, so the next gate-dependent chart value has one obvious place to go.

The cost is that the enumeration is slightly wider than a one-line ternary: the palette lives on each colour aesthetic's visual frame, so the seed has to walk them. `AbstractChartInfo.getAestheticRefs(boolean)` (`:240-259`) already resolves the multi-aesthetic case to per-aggregate refs, so walking `false` and `true` matches what `ChangeChartProcessor:1888-1896` walks on the forward path. That parity is what keeps Modernize and Revert from drifting.

---

## Task 1: Seed the palette at the enumeration point

**Files:**
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/VSChartPaletteDefaults.java` — add a total palette *resolver* (not an applier), and make `pickerPalette` delegate to it so the legacy expression is spelled once
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/ChartVSAssemblyInfo.java:100-119` — seed the palette in `seedChromeDefaults`, on both branches
- Test: `core/src/test/java/inetsoft/uql/viewsheet/internal/VSChartPaletteDefaultsTest.java` (exists)
- Test: a revert-path test for `ChartVSAssemblyInfo.seedChromeDefaults`. Look for an existing M-P6 test class covering `seedChromeDefaults`/`VizModernizeUtil` and add to it rather than creating a new one; only create a class if none exists.

**Interfaces:**
- Add to `VSChartPaletteDefaults`:
  - `public static Color[] seedPalette(VizContext ctx)` — total: `ctx.modern ? activePalette(ctx) : legacyPalette()`. Returns a palette on both branches. This is a resolver, so it leaves no state behind and cannot clobber anything by itself.
  - `static Color[] legacyPalette()` — the expression currently inlined in `pickerPalette` (`:64-67`): `fromFrame(getPaletteSafely(DEFAULT_NAME), CategoricalColorFrame.COLOR_PALETTE)`. Note this honours a customer's `format.css` `Default` ChartPalette rule, which the raw `COLOR_PALETTE` constant does not — that is deliberate and is the correct legacy value to restore.
  - `pickerPalette(ctx)` becomes `return seedPalette(ctx);` — its two branches are already exactly `seedPalette`'s. Its existing tests must keep passing unchanged.
- `applyModernPalette` is **not** modified. Nothing about its three call sites changes.
- `ChartVSAssemblyInfo` gains one private helper that walks the colour aesthetics and writes `seedPalette(ctx)` onto each `CategoricalColorFrame`.

- [ ] **Step 1: Write the failing test**

Two tests, both required.

The resolver test, in `VSChartPaletteDefaultsTest`:

```java
   @Test
   void seedPaletteIsTotalAcrossTheMark() {
      assertEquals(VSChartPaletteDefaults.modernPalette()[0],
                   VSChartPaletteDefaults.seedPalette(VizContext.of(VizMark.MODERN_LIGHT))[0],
                   "a marked context seeds the modern palette");
      assertNotEquals(VSChartPaletteDefaults.modernPalette()[0],
                      VSChartPaletteDefaults.seedPalette(VizContext.of((VizMark) null))[0],
                      "an unmarked context seeds the legacy palette rather than nothing");
   }
```

The revert test, on `ChartVSAssemblyInfo`: build a chart assembly info with a colour aesthetic bound, apply the modern palette to its frame the way a render does, then call `seedChromeDefaults` with an unmarked context and assert the frame's colour at index 0 is no longer the modern one. `VizContext.of((VizMark) null)` is the unmarked context `VizModernizeUtil.revert` itself uses (`:100`) — the cast picks the `VizMark` overload; match that call exactly.

Verified accessors: `CategoricalColorFrame.getDefaultColor(int)` at `:442`. There is **also** a no-arg `getDefaultColor()` on sibling frame classes returning something different — call the indexed overload. `VizMark` has exactly two constants, `MODERN_LIGHT` and `MODERN_DARK` (`:24-26`); there is no `LEGACY` constant, and an unmarked context is `null`.

- [ ] **Step 2: Run them and confirm they fail**

`cd community && ./mvnw test -pl core -Dtest=VSChartPaletteDefaultsTest`

Expected: the resolver test fails to compile until `seedPalette` exists, and the revert test fails on its assertion because the frame still holds the modern palette. If the revert test passes before the fix, stop and report — the premise is wrong.

- [ ] **Step 3: Add the resolver**

Add `seedPalette` and `legacyPalette` to `VSChartPaletteDefaults` and make `pickerPalette` delegate. Do not touch `applyModernPalette`.

- [ ] **Step 4: Seed the palette in `ChartVSAssemblyInfo.seedChromeDefaults`**

Add the call in `seedChromeDefaults`, unconditionally — `seedPalette(ctx)` already carries the branch, so this does not need a fourth `if(ctx.modern)`. The helper it calls:

- gets `getVSChartInfo()` and returns early when it is null (`legacySmoothLines()` already guards this way at `:130-134`; match it)
- walks `for(boolean runtime : new boolean[]{ false, true })` over `info.getAestheticRefs(runtime)`, mirroring `ChangeChartProcessor:1888-1896`
- for each ref whose `getVisualFrame()` is a `CategoricalColorFrame`, calls `setDefaultColors(VSChartPaletteDefaults.seedPalette(ctx))`
- skips a null ref if `getAestheticRefs` can return one — check `AbstractChartInfo.getAestheticRefs(ChartBindable)` (`:283-289`), which adds fields unconditionally, and guard accordingly

Note `setDefaultColors` also calls `clearUsedColors()` (`:477-481`), which invalidates the derived caches — that is wanted here.

- [ ] **Step 5: Confirm green, then the guarding classes**

```bash
cd community && ./mvnw test -pl core -Dtest=VSChartPaletteDefaultsTest,VGraphPairModernPaletteTest,VSChartPaletteCssOverrideTest,ChartVSAssemblyInfoTest
```

`VGraphPairModernPaletteTest` and `VSChartPaletteCssOverrideTest` cover the render path and the CSS override; they are what would catch an over-eager write or a broken `pickerPalette` delegation. Drop `ChartVSAssemblyInfoTest` from the list if no such class exists, and substitute whichever class you added the revert test to.

- [ ] **Step 6: Full core suite**

`cd community && ./mvnw test -pl core`

Report the actual run/failure/error/skip numbers and reconcile the delta against the tests you added. Do not claim a baseline you did not measure: the plan's first draft cited 4909 and the roadmap cites 4904 for the same tree, so treat any pre-existing number as unverified and report what you observe.

- [ ] **Step 7: Commit**

```bash
cd community && git add core/src/main/java/inetsoft/uql/viewsheet/internal/VSChartPaletteDefaults.java core/src/main/java/inetsoft/uql/viewsheet/internal/ChartVSAssemblyInfo.java core/src/test/java/inetsoft/uql/viewsheet/internal/
git commit -m "fix(viewsheet): revert chart colours with the assembly

The modern categorical palette was written onto a chart's colour frames at
render time and never written back, so clearing an assembly's mark left the
modern colours in place. Revert re-seeds through seedChromeDefaults, which
touched formats and plot values but no colour frame, so nothing put the classic
palette back - and the palette is persisted, so the divergence outlived the
session.

The palette is now seeded in seedChromeDefaults beside the two plot values that
already write both branches, which is the method Revert and a gate-off creation
both call. The render-time applier is unchanged and stays forward-only: making
it total would have turned the CSS path's inert legacy call into a writer that
stamps the legacy palette onto every report chart on every pass.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: Sweep for the same defect shape, and correct the record

The value of the sweep is not the palette; it is finding whatever else is a read-time applier with a memory. That category was invisible to M-P6's design and this is the first evidence it exists. **Report findings; do not fix them here.**

**Files:**
- Read only for the sweep.
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/VSChartPaletteDefaults.java` class javadoc — it currently claims the modern palette is "applied to a render-time color frame only, never serialized". That is false: `CategoricalColorFrameWrapper.writeContents()` (`:182-201`) writes `<colors>` from `frame.getDefaultColor(i)` and `parseContents()` reads it back. Correct the sentence. No plan or ticket reference in the comment.
- Modify: `docs/superpowers/specs/2026-08-14-seed-mark-forward-half-design.md` — add the dated notes below.

- [ ] **Step 1: Find forward-only mutators keyed on the context**

```bash
cd community
grep -rn "ctx.modern\|ctx.dark" --include=*.java core/src/main | grep -vE "return |\? "
```

The filter is deliberate: a `return`/ternary is a resolver and reverts for free. What matters is a **statement** guarded by `if(ctx.modern)` that *writes* something outliving the call. `VSObjectChromeDefaults.applyDarkForeground` and `VSOutputChromeDefaults`'s two `if(!ctx.modern) return;` methods are the first to check — establish whether each returns a value or mutates its argument.

- [ ] **Step 2: Classify and report each hit**

Resolver (reverts for free) / persisted seed (owned by `seedChromeDefaults`) / **applier with a memory** (the bug shape). Report the third category with file:line and what state it leaves behind.

- [ ] **Step 3: Correct the javadoc**

Fix the "never serialized" sentence in `VSChartPaletteDefaults`. Keep it to the correction; do not rewrite the rest of the comment.

- [ ] **Step 4: Record three findings in the design doc**

Add a dated 2026-08-24 note to `docs/superpowers/specs/2026-08-14-seed-mark-forward-half-design.md` covering all three, so the design's enumeration-point premise no longer reads as exhaustive:

1. **The third category exists.** Name "read-time applier with a memory" and cite the palette as the instance, with whatever Step 2 found alongside it. The next phase in this track builds on that premise.
2. **The palette is persisted, and where.** `CategoricalColorFrameWrapper.writeContents()` writes `<colors>` from `frame.getDefaultColor(i)`; `parseContents()` reads it back. Record this against the first draft's contrary claim, so nobody re-derives the wrong answer from the same filtered grep.
3. **A second, unfixed leak of the same defect — the colour pane's Reset button.** `categorical-color-pane.component.ts:200-202` sets `colors[i] = cssColors[i] || defaultColors[i]`, i.e. Reset copies the frame's current default palette into the *user* colours. On a marked chart whose frame is holding the modern palette, pressing Reset promotes those modern colours into `userColors`, which persists separately (`<userColors>`) and which nothing in this plan reverts. Reverting the dashboard afterwards leaves modern colours in place. **Ruled out of scope for this plan** — the fix belongs with the colour pane, not the seed mark. Record it as an open item with enough detail to pick up cold.

- [ ] **Step 5: Commit the doc and javadoc changes**

Separate commit from Task 1.

---

## Self-Review

**Spec coverage.** No spec prescribed this; "The defect", "Why M-P6 missed this" and "Why the fix goes in `seedChromeDefaults`" are the specification, all cited to file:line at `27ea5fdd5`. Decision 13's "Revert undoes exactly what Modernize does" is the guarantee being restored, and `VizModernizeUtil.revert`'s own doc comment is the contract being honoured rather than worked around.

**No open decisions.** The first draft's Task 1 (settle persistence) is answered by static evidence; its Task 3 fork (discriminator vs. stateless redesign) is moot because `defaultColors` is not UI-authorable. The fix-shape choice, the Reset leak's scope and the sweep's disposition were all ruled on 2026-08-24 and are recorded in the revision history.

**Type consistency.** `applyModernPalette(CategoricalColorFrame, VizContext)` is unchanged. `seedPalette(VizContext)` and `legacyPalette()` are new and return `Color[]`, matching `activePalette`/`pickerPalette`. `pickerPalette`'s delegation is behaviour-preserving by construction — its body already *is* `seedPalette`'s two branches — and its existing tests are the check. `getDefaultColor(int)` and `getAestheticRefs(boolean)` are cited to their declarations rather than assumed.

**Sequencing.** Task 1 and Task 2 are independent: Task 2's sweep is read-only and its doc note describes a finding Task 1 does not change. Task 1 first so the javadoc correction lands against fixed code.
