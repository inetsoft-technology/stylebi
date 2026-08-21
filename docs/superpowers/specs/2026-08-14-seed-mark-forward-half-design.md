# Seed Mark — Forward Half — Design

**Date:** 2026-08-14; amended 2026-08-19 (decision 13 — the revert sweep is overruled; this document gains a
sixth phase, P6, and its title is now half a misnomer, kept for continuity)
**Verified against:** community `viz-updates` @ `952614aa7` for the original; the 2026-08-19 amendments
against `8ef511e45`, which is `HEAD` and carries P4. Every file and line cited below was read at one of those
two commits.
**Covers:** item M in [chart-card-roadmap.md](./lookfeel/chart-card-roadmap.md) — the per-assembly provenance
mark, the re-keying of the modern read paths onto it, the per-assembly browser scope, and the Modernize
action. This is the half of M that unblocks the six items behind it.
**Governed by:** [seeded-value-reversibility-decisions.md](./lookfeel/seeded-value-reversibility-decisions.md).
That file holds the product decisions and is the authority on *what*; this file is the authority on *how*, and
records the two places the decisions needed refining to be implementable.
**Does not supersede:** `chart-card-design3/Visualization Widget Spec.dc.html` §03 — the decisions file
already does that. §03's tri-state mark, clone-based reversal and automatic forward re-seed are all absent
here by decision, not by oversight.

---

## Scope

**In scope — the forward half.** The mark exists, is written at creation, travels with copies and imports, and
every modern read path consults it instead of the org gate. Existing dashboards have one route in: a manual
Modernize action.

~~**Out of scope, deliberately, and still gating the release:** the org-wide revert sweep (decisions 6 and 7)
with its restore point, scheduler blocking and composer-session blocking; bookmark resolve-on-restore
(decision 10); deleting the four old reversibility mechanisms (decision 12); and the card radius 12px → 6px.
None of those gate the six downstream items. All of them gate release, because the decisions file's write-off
of the pre-mark cohort holds only while `viz-updates` is unreleased.~~

**Rewritten 2026-08-19. The sweep is overruled by decision 13**, and what replaced it is small enough to
belong in this document rather than in a reverse-half one that no longer needs to exist.

**Now in scope: P6 — Revert**, the per-dashboard mirror of Modernize, together with the deletion of the
`gate &&` term and of the two `PlotDescriptor` seed booleans that must go with it. See §5's P6.

**Still out of scope, and still gating the release:** bookmark resolve-on-restore (decision 10); the
remaining two of decision 12's four mechanisms (`resolveSeededCorner` and its tab carve-out); and the card
radius 12px → 6px, which needs P6 rather than the sweep. None of those gate the six downstream items. All of
them gate release, because the decisions file's write-off of the pre-mark cohort holds only while
`viz-updates` is unreleased.

**What stopped gating the release entirely:** the async sweep engine, its restore point and documented
restore procedure, scheduler cancellation and blocking, composer-session blocking with a force override, and
resumability across a partial run. Decision 7 is retained in the decisions file as the record of what that
would have cost.

**The premise this rests on, stated because it is easy to mistake for technical debt.** Older assets have no
mark and never will. Decision 2 makes *unmarked = never touched by any automatic behaviour, forever* — it is
the product rule, not a migration left undone. Retro-marking on load and a one-off migration task were both
considered and rejected in the decisions file. There is no backfill phase in this design because there is no
backfill phase in the product.

---

## The model in one page

1. A **new viewsheet** created while the gate is on is marked. A new assembly takes the **host viewsheet's**
   mark. Copies, pastes, embedded sheets and imported assets keep **their own** mark.
2. **Creation-time seeding reads the mark just stamped on the assembly**, never the gate. One consultation of
   the gate, at stamp time.
3. **Every read path resolves against the assembly's mark** — server resolvers, export painters, and the
   browser CSS scope alike.
4. **Unmarked content renders exactly as it does today**, under every gate setting.
5. **Modernize** is the only way an existing dashboard becomes modern: composer-only, per-dashboard,
   gate-on only, write permission required, one undo step, no bulk path.
6. **Until P6**, the read predicate carries an extra `gate &&` term, so an admin turning the gate off still
   gets legacy read-time chrome back. ~~The revert sweep deletes that term.~~ **P6 deletes it** — amended
   2026-08-19, when decision 13 overruled the sweep. After P6 the gate is read at creation and nowhere else.
7. **Revert** is Modernize's mirror: same shape, same authority, same undo, acting on marked assemblies
   instead of unmarked ones. Added 2026-08-19 by decision 13.

---

## 1 · The mark

### Shape

`VizMark` — an enum with two values, `MODERN_LIGHT` and `MODERN_DARK`, held in a nullable field on
`VSAssemblyInfo`. `null` is the third state, *unmarked*. Persisted through the existing
`writeAttributes` (`VSAssemblyInfo.java:849,877-879`) / `parseAttributes` (`:887,927`) pair as one
`vizMark` attribute, omitted entirely when null.

Two values rather than one boolean because the dark bit is load-bearing: decision 9 keys the dark resolvers
off the mark so that unchecking dark mode cannot strand a persisted dark card background under light
read-time chrome, and decision 3 requires a dark-marked assembly pasted into a light dashboard to stay
internally consistent. Density is **not** in the mark — see §2.

`clone(boolean shallow)` needs no change: `super.clone()` (`:1123`) copies the enum reference.

The mark stays out of every `equals()`. Provenance must never become a change-detection trigger — the same
reasoning that kept `userTitleHeight` out of `TitleInfo.equals()` until strip-and-lane decision 7 had a
specific reason to put it in.

### The parse trap, and the rule that closes it

`ViewsheetVSAssemblyInfo`'s **constructor** calls `initDefaultFormat()` (`:47`), and `new Viewsheet()` runs
inside `RuntimeViewsheet.loadXml(new Viewsheet(), …)` (`RuntimeViewsheet.java:157,167,177,267`). So the
page-background seed at `ViewsheetVSAssemblyInfo:238` already executes on **every load of every legacy
viewsheet**, before parse overwrites the format from XML.

Any stamp reachable from that path would therefore be written on load and, finding no `vizMark` attribute in
the file, survive parse — silently retro-marking every dashboard ever saved. That is exactly the failure
decision 2 exists to prevent, and it would be invisible until content nobody had opted in was reverted
underneath them. (Written when decision 6's gate-off sweep would have been what exposed it; under decision
13 the exposure is a press of Revert instead. The failure is identical either way, which is the point — the
rule below is what prevents it.)

**Rule: `parseAttributes` clears the mark when the attribute is absent, unconditionally.** Not a
derive-on-parse like `userTitleHeight` — the opposite. Absent means unmarked and there is nothing to infer it
from. This is defensive rather than merely correct: it holds no matter which constructor paths later start or
stop running `initDefaultFormat`.

Of the three `initDefaultFormat()` call sites that looked like they might defeat this rule, only one is a
constructor: `ViewsheetVSAssemblyInfo:41-48`, which the rule is written for. `Viewsheet.java:2397` is
`Viewsheet`'s own override delegating to its info, not a construction. `Viewsheet.getWarningTextAssembly`
builds the sheet's warning label outside any funnel, and the per-assembly constructor stamp below covers it
without a special case.

### Where the mark is written

| Site | Value written |
|---|---|
| Any new viewsheet — `ViewsheetVSAssemblyInfo`'s constructor (`:46`) | `VizMark.fromGate()` |
| Any new assembly — `AbstractVSAssembly(Viewsheet, String)`'s constructor (`:128-136`) | the host viewsheet's mark, `null` included |
| `copyInfo` / `copyViewInfo` (`VSAssemblyInfo.java:561,622-625`) | carried from the source info |
| A type conversion — `ComposerRangeSliderService:129`, `ComposerVSSelectionListService:249` | the converted object's **own** mark, overriding host inheritance |

**Assembly creation has no single funnel, and assuming it did was this design's most expensive error.**
`VSEventUtil.createVSAssembly` looked like one; it is not. Dozens of sites construct assemblies directly,
spread across `core/src/main/java/inetsoft/web/`, the export and report-conversion code, and
`VSEventUtil` itself — dragging a field onto the canvas (`VSTableService.createSelectionVSAssembly`),
accepting a wizard recommendation (`VSWizardBindingHandler`), grouping two objects into a tab
(`GroupingService`), and more. Stamping at a funnel reached only some of them. The constructor reaches
all of them, plus any added later, because every intermediate abstract class chains through
`super(vs, name)`.

**Not `initDefaultFormat()`, though it looks like the ideal chokepoint.** Every one of those sites calls it,
but it conflates creation with format *reset*: `ComposerObjectService.java:763-771` re-inits an existing
assembly's format after it leaves a container. A stamp there would overwrite that assembly's mark with its
host's, so a modern assembly pasted into a legacy dashboard would lose its provenance the first time a user
dragged it out of a selection container — precisely the loss decision 3 exists to prevent.

**The `copyViewInfo` row is not bookkeeping.** `VSObjectPropertyService.isTableModeChange` (`~:1832`) and
`ComposerVSTableService.convertToFreehandTable` build a replacement assembly and copy the old info onto it.
Because the copy carries the mark, both satisfy the type-conversion rule with no code of their own — the two
mechanisms agree rather than competing.

**Copy, paste and import need no code.** They go through the parse funnel
(`AbstractVSAssembly.createVSAssembly(Element, Viewsheet, boolean)`, `AbstractVSAssembly.java:60-83`), which
constructs via the **no-arg** constructor and attaches the viewsheet afterwards, so neither constructor stamp
can fire. Marks travel in the asset XML. Decision 3's copy row and decision 8 in full are satisfied by
construction.

### The ordering constraint

The stamp must be written **before** `initDefaultFormat()` runs, so the creation seeds resolve against the
freshly-stamped mark rather than against the gate. Both stamps satisfy this for free by living in
constructors: an assembly's mark is set in `AbstractVSAssembly`'s two-arg constructor, and a viewsheet's in
`ViewsheetVSAssemblyInfo`'s, each before any caller can reach `initDefaultFormat()`.

Once that holds, `setDefaultFormat` never consults the gate at all: all four persisted seeds —
`objectBorderColor` (`VSAssemblyInfo.java:1192-1193` → `:1223`), the card radius (`:1196-1197` → `:1224`),
`cardBackgroundCss` (`ChartVSAssemblyInfo.java:90`, `TableDataVSAssemblyInfo.java:1578-1579`) and
`pageBackgroundCss` (`ViewsheetVSAssemblyInfo.java:241-242`) — plus the four `PlotDescriptor` seeds
(`ChartVSAssemblyInfo.java:95-100`) go through one context. That is what lets creation and Modernize share a
single code path in §4. Line numbers refreshed 2026-08-18 against `119bfdaac`; §4's second correction turns
this enumeration into the definition of what the hook carries, and notes that the last of the five cannot be
reached through a `VSCompositeFormat` at all.

### The sheet stamps itself in its constructor

`ViewsheetVSAssemblyInfo`'s constructor already calls `initDefaultFormat()`. The stamp goes immediately
before that call, so the page-background seed resolves against the mark on the first and only init, and no
second init is needed. It covers every route to a new viewsheet without enumerating them. It is safe only
because the parse clears the field: `Viewsheet.parseXML(Element, boolean)` clears the mark unconditionally at
`:4211`, before its own conditional parse of the sheet's `assemblyInfo` node, so a loaded sheet ends up with
whatever its file says, never the constructor's stamp.

**Rejected — removing the constructor's `initDefaultFormat()` call and placing it only at creation sites.**
It changes what a parsed legacy sheet falls back to when its XML carries no background value, which is a
rendering change to every saved dashboard in exchange for tidiness.

---

## 2 · `VizContext` and the re-keying

### The problem

The mark lives on `VSAssemblyInfo`. Every modern value is resolved by a **static** method on one of eight
`VS*Defaults` classes that reads `SreeEnv` directly, across roughly 115 call sites. Decision 4 requires all of
them to ask the assembly instead.

### Shape

`VizContext` — immutable, three fields: `modern`, `dark`, `density`. Four factories:

| Factory | Use |
|---|---|
| `of(VSAssemblyInfo)` | the normal case; reads the mark |
| `of(VizMark)` | where only the mark is at hand |
| `ofGate()` | the genuinely org-level shell paths, and the transitional state in phase P2 |
| `LEGACY` | report charts, which have no viewsheet and no mark |

Every `VS*Defaults` method takes it as its first parameter; the `SreeEnv` reads move into `ofGate()`.

**Density is not in the mark, deliberately.** The mark stores the modern/dark tuple only (decision 1).
Density is a live org preference: changing it should reflow marked assemblies and leave unmarked ones alone.
So `of(info)` takes `modern` and `dark` from the mark and `density` from `SreeEnv`. Decision 4's "the density
heights key off the mark" means *whether density applies at all*, not which mode is in force — it reads
ambiguously and this is the reading.

### The interim term

**Read the 2026-08-19 amendment at the end of this subsection first.** The body below says the sweep deletes
this term and fixes the persisted colour seeds. Decision 13 overruled the sweep: **P6** deletes the term, and
nothing fixes the colour seeds because under decision 13 they are not broken. The body is retained because
its account of what the term does and does not buy is still exact.

`of(info)` computes `modern = gate && mark != null`.

That `gate &&` is the whole of the forward half's reversibility story, and deleting it is a one-line change
when the sweep lands. What it buys is narrow and worth stating exactly: its **only** effect is on a marked
assembly in a gate-off product, where it restores today's behaviour — read-time chrome goes legacy, so an
admin can still turn the feature off and mostly get the old look back. Without it and without the sweep,
flipping the gate off would leave everything modern with no route back at all.

What it does **not** buy: the three persisted colour seeds are stored values read straight off the DEFAULT
tier, and no read predicate touches them. A marked assembly in a gate-off product keeps its modern border and
card colour either way. That is already true on `viz-updates` today — it is the decisions file's "the gate
seeds four values and exactly one is reversible" — so the forward half neither fixes nor worsens it. The
sweep fixes it.

The end state after the term is deleted is coherent: **the gate becomes purely a creation-time switch plus
the sweep trigger**, which is already how decision 9 describes the dark toggle.

**Amended 2026-08-19 — the end state is now simpler than that sentence, and the term's owner changed.**
Decision 13 overruled the sweep, so there is no sweep trigger: the gate becomes purely a creation-time
switch, full stop, and matches decision 9's dark toggle exactly rather than approximately. **P6 deletes the
term**, and the phrase "during the forward half only" above should be read as "until P6."

Two things this amendment adds that the paragraph above could not have known, both verified at `8ef511e45`:

- **After P4, the term is no longer the only gate-keyed reversal.** `PlotDescriptor.isSmoothLines()`
  (`:635`) and `getBarCornerRadius()` (`:1319`) still test `VSDensityDefaults.isModern()` directly — P2 left
  them alone on purpose, as interim mechanisms, and P4 did not touch them. ~~They and this term are the whole
  of what reverses on gate-off today.~~ **Corrected 2026-08-20 by the P6 review: they are not.**
  `resolveSeededCorner` is a third, and the paragraph at the end of this subsection is where it was
  overlooked — it says the method "stays untouched" without noticing that the same argument this bullet makes
  applies to it verbatim. All three, plus this term, have to be deleted **in the same phase**: delete the term
  alone and a marked chart in a gate-off org keeps modern chrome everywhere except its bar corners and line
  smoothing; delete the booleans with it and it still loses its card radius. Either partial state is worse
  than either end. §5's P6 carries the full four-deletion set.
- **`ChartVSAssemblyInfo.seedChromeDefaults` is forward-only** (`:106-112`) because those two booleans did
  the reversing. P6 gives it the `else` branch, which is also what makes Revert complete for charts.

What the term still does not buy is unchanged: the three persisted colour seeds are read straight off the
DEFAULT tier with no predicate in front of them, so a marked assembly in a gate-off product keeps its modern
border and card colour either way. Under decisions 6 and 7 the sweep fixed that; **under decision 13 nothing
fixes it automatically and nothing needs to** — a marked assembly is *supposed* to stay modern when the gate
closes. The mismatch the paragraph above was worried about only existed because gate-off was meant to mean
legacy.

`resolveSeededCorner` (`VSObjectChromeDefaults.java:68-70` at `35ca4fce0`; the `:79-81` first recorded here
has since moved), its tab carve-out (`VSCompositeFormat.java:334-337`) and both `PlotDescriptor` seed
booleans stay untouched. Decision 12 forbids deleting them before the mark is verified, and they are what
keeps the radius reversible in the meantime. **`VSCompositeFormat` therefore needs no changes in this scope at
all** — which removes the granularity problem the external ticket spends a section on.

**Amended 2026-08-20 — true of the forward half, and P6 ends it.** "This scope" above means P1–P5, and
across those five phases the sentence held: `VSCompositeFormat` was never touched. P6 is where all three go,
together with the term, and it is therefore also where `VSCompositeFormat` finally changes —
`getRoundCornerValue()` (`:323-327`) loses its call to `resolveDefaultTierCorner`, and that private method
and its TAB carve-out are deleted with it. The condition decision 12 set is met by then: the mark is
verified, P4 and P5 read it, and Revert is the reversal path the retirement needed. See §5's P6, piece 4.

### The four sub-gate properties — deleted in P2

**Decided 2026-08-14.** Four properties currently switch modern facets independently of the master gate, and
P2 deletes all four. `VizContext` keeps the three fields specified above.

`VSObjectChromeDefaults.isModern()` is `VSDensityDefaults.isModern() && viewsheet.modernObjectChrome !=
"false"`, and three siblings have the same shape: `viewsheet.modernChartChrome` (`VSChartChromeDefaults`),
`viewsheet.modernChartPalette` (`VSChartPaletteDefaults`) and `viewsheet.modernTableStructure`
(`VSTableStructureDefaults`). `viewsheet.modernObjectChrome` itself is shared by three classes —
`VSObjectChromeDefaults`, `VSTitleChromeDefaults` and `VSOutputChromeDefaults` — so it is four properties
guarding six classes. `VSCalendarChromeDefaults` has no `isModern()` at all and guards on `isDark()` alone.

**Why they go.** They were rollout scaffolding: they de-risked building the facets one phase at a time. All
six classes have shipped, so the per-facet switches have served their purpose. They are also undocumented,
absent from EM's Look and Feel pane, unwritten by `LookAndFeelService`, referenced nowhere outside the six
classes that read them, and one of them — `modernTableStructure` — has no test at all.

**What the deletion buys, beyond a simpler type.** With the sub-gates gone, "marked but unseeded" becomes
unreachable: a marked assembly is always seeded, because master-on-facet-off no longer exists. P3's
Modernize, P4's read flip and P6's Revert can all rely on that, and several later-phase claims already did.
(This read "the revert sweep" until 2026-08-19; decision 13 changed which phase inherits the guarantee, not
the guarantee.)

**The mechanics, and why they cost P2 nothing extra.** All six `isModern()` methods are structurally
identical, differing only in which property they read, so stripping the sub-gate term makes all six literally
`return VSDensityDefaults.isModern();`. They are then deleted rather than left as delegates, and each of the
~115 call sites reads `ctx.modern` instead of `X.isModern()` — the same edit P2 was already making at that
site, not an additional one.

**Zero behaviour change, and that is checkable.** All four default *on* — the predicate is
`!"false".equals(...)`, so absent means enabled — and `viz-updates` has never shipped, so no deployment can
have set one to `false`. Deleting them cannot alter what any dashboard renders.

**Test fallout: two tests and four lines.** `VSOutputChromeDefaultsTest:85-86` (`"modernObjectChrome=false
opts out"`) and `VSChartPaletteDefaultsTest.subGateFalseOptsOutEvenWhenBaseGateOn` *are* the escape hatch and
are deleted with it. Four `setProperty(…, null)` reset lines lose their subject:
`VSOutputChromeDefaultsTest:50`, `VSChartChromeDefaultsTest:27`, `VSChartPaletteDefaultsTest:31`,
`VGraphPairModernPaletteTest:47`.

**Explicitly not deleted: `graph.svg.inline`.** `VSChartInteractionDefaults.isInlineSvg()` looks adjacent and
is a different mechanism — an explicit value wins in either direction and only its absence falls back to
`isModern()`, so it can enable inline SVG *without* the master gate, which no sub-gate can. Its own comment
records that this is deliberate. It is a real toggle set through EM Properties, not scaffolding.

### The surface, sized for slicing

| Class | Sites | Reach |
|---|---|---|
| `VSChartChromeDefaults` | 38 | five descriptors via the existing `initDefaultFormat(boolean vs)` slot; the largest cluster is `web/composer/model/vs/ChartLinePaneModel.java` (10), with `web/graph/model/dialog/LegendFormatDialogModel.java` and `AxisPropertyDialogModel.java` alongside; `VGraphPair` holds about 4, `GraphTarget` holds none |
| `VSTitleChromeDefaults` | 19 | model builders and exporters, info in hand |
| `VSTableStructureDefaults` | 14 | all in `DataVSAQuery` |
| `VSObjectChromeDefaults` | 14 | creation seeds, `VSSlider`, `VSSliderModel` |
| `VSDensityDefaults` | 14 | `VSTableLens` (has `tinfo`), `BaseTableService`, `SelectionBaseVSAssemblyInfo`, `AbstractChartInfo` |
| `VSOutputChromeDefaults` | 7 | painter and models |
| `VSChartPaletteDefaults` | 4 | `VGraphPair`, `ChangeChartProcessor`, `CSSProcessor` |
| `VSCalendarChromeDefaults` · `VSChartInteractionDefaults` | 5 | painter, model, `CoreLifecycleService` |

Three things this table is for.

**The chart descriptors already thread the parameter.** `AxisDescriptor:63`, `ChartRefImpl:56`,
`LegendDescriptor:69`, `LegendsDescriptor:95` and `TitleDescriptor:65` all take
`initDefaultFormat(boolean vs)`, where `vs` means "this is a viewsheet chart." It becomes
`initDefaultFormat(VizContext)`, with `LEGACY` where `false` is passed today. `VGraphPair` re-runs these on
roughly 30 lines every render (`VGraphPair.java:1026-1359`) and holds the chart assembly, so the chart
interior needs no plumbing invented for it.

**One genuine gap.** `CSSProcessor.applyCSS(ChartInfo, CSSDictionary, List<CSSParameter>)`
(`CSSProcessor.java:384`) has no route to an assembly. Its callers decide whether a context can be threaded
from above or whether it takes `ofGate()` with a comment. A plan-time question, not a design one.
`ChangeChartProcessor.updateColorFrameCSSParentParams(ChartVSAssemblyInfo, VSChartInfo)`
(`ChangeChartProcessor.java:1869`) and `CSSChartStyles.apply(…)` (`CSSChartStyles.java:89`) are both reached
with the assembly in hand.

**A related open question.** `VSChartChromeDefaults`'s biggest cluster is composer *dialog* models
(`ChartLinePaneModel`, `LegendFormatDialogModel`, `AxisPropertyDialogModel`), which may be reached without an
assembly in hand. Whether they can thread a context or need `ofGate()` too is open; the design does not
otherwise address it.

### Rejected alternatives

**A `ThreadLocal` ambient scope** — `VizContext.with(info, () -> …)` at model build, painter entry and
`VGraphPair.render`, leaving the call sites untouched. Roughly 15 edits instead of 115. Rejected on failure
mode: a path that forgets to open a scope reads the org gate, which is *precisely today's wrong behaviour*, so
a missed site renders plausibly and wrong instead of failing to compile. `VGraphPair` renders on parallel
threads and export runs in scheduler threads — the two places a leak bites hardest and reproduces least.
Bounding it would need an audit that costs back most of the saving.

**Resolving at stamp time and persisting the values** — write the density- and dark-derived values into the
info when the mark is written, so read paths need no context. This is the direction decision 4 moves away
from: it persists more, not less, and re-introduces exactly the version-blindness the mark was designed to
make irrelevant.

---

## 3 · The browser scope

> **CORRECTED 2026-08-19 AFTER P5 SHIPPED. Read this box before anything below it.** Parts of this
> section were edited *during* P5's implementation, on reasoning that implementation then disproved. The
> body text is retained per this tree's convention, but on three points it now describes something that
> was not built:
>
> 1. **"Three binding sites" is wrong.** The shipped surface is **eight templates**, and three further
>    render sites inside them that no enumeration by container or by tag name found: the chart-annotation
>    overlay (a deliberate sibling of the assembly stack), the composer's `VSLine` branch (guarded by
>    `*ngIf="vsObject.objectType != 'VSLine'"`, so the bound `.object-editor` never applies to it), and the
>    layout pane's group-container children. The reliable enumeration is not "where are the containers" or
>    "where is `<vs-chart>`" but **every element whose subtree contains a `:host-context(.viz-modern)` or
>    `.viz-modern <descendant>` rule**.
> 2. **The density paragraph is reversed.** It says the wrapper carries `viz-density-<mode>` and quotes the
>    compound selectors. What shipped: density stays on `body`, and `_viz-tokens.scss`'s three matrices were
>    rewritten to ancestor-descendant form (`.viz-density-compact .viz-modern`). The wrapper carries no
>    density class. **P6 must read this and not the paragraph below** — the recorded P6 action (drop the
>    `if(modern)` guard so the density class is always present) only makes sense under the shipped design.
> 3. **The stated reason for rejecting descendant selectors is false**, and that is the design that
>    shipped. It claims a descendant selector "inverts the specificity the file's source-order comment
>    depends on" because `.viz-dark` wins by coming later. The blocks are disjoint: the density matrices set
>    only size tokens, `.viz-dark` only colour tokens. They never compete, so there was no specificity to
>    invert.
>
> **Also shipped and not described below:** org-level surfaces need their own token scope. The
> `--inet-viz-*` blocks in `_viz-tokens.scss` now list `.viz-shell` beside `.viz-modern` (and
> `.viz-shell-dark` beside `.viz-dark`), because consumers outside any assembly wrapper — the combo-box
> dropdown appended to `document.body`, the worksheet details pane, the schedule list — otherwise fall back
> to the legacy `:root` values. ~~A body carrying `viz-shell` resolves the dense tier, which matches what a
> body carrying `viz-modern` + `viz-density-<mode>` resolved before the split.~~ **The first half is right and
> the second is wrong — corrected 2026-08-20 by the P6 review.** A body carrying `viz-shell` does resolve the
> dense tier, at every density, because `.viz-shell` appears only in the bare/dense group at `:115-117` and
> the compact and comfortable matrices are *descendant* selectors that cannot match the body's own classes.
> But the pre-split form was **compound** — `.viz-modern.viz-density-compact`, confirmed at
> `git show 4c237a7dd^:web/projects/portal/src/scss/_viz-tokens.scss:120` — which matched a body carrying both
> and resolved the org's chosen density. So org-level surfaces silently lost their density tier at P5. Two
> added compound selectors restore it, and the fix is folded into P6 because P6 is the phase that touches
> these lines; see §5's P6.
>
> What actually shipped, and the reasoning behind each departure, is recorded in
> [chart-card-roadmap.md](./lookfeel/chart-card-roadmap.md) under "What P5 left behind". P5 shipped as
> `4c237a7dd`; `git show` it for the change set.


Today `viz-modern`, `viz-dark` and `viz-density-*` are toggled on `document.body` by three shells
(`viewer-app.component.ts:2789-2798`, `portal/app.component.ts:264-279`,
`composer/app.component.ts:138-151`), and `GuiTool.isVizModern()` reads the class back off the body
(`gui-tool.ts:66`).

**Re-verified 2026-08-19 at `8ef511e45`: three, as first written.** A 2026-08-19 edit briefly recorded two
here, on a grep of `portal/src/app/app.component.ts` — the wrong file. The shell meant is
`portal/src/app/portal/app.component.ts`, whose `updateVisualizationMode()` toggles all three classes exactly
as the other two shells do. The line reference is `:264-279` rather than the `:271` first recorded.

**The CSS half is a rename, not a rewrite** — and the **zero selector edits** claim holds, because
`:host-context(X)` matches the host element itself as well as its ancestors. What does not hold is the
"one wrapper" premise. **Corrected 2026-08-19, verified at `8ef511e45`: there are three binding sites,
across the viewer and composer shells.**

~~`.vs-object-parent-container` (`vs-object-container.component.html:41`) is already a per-assembly wrapper
`<div>` enclosing every assembly component. Binding `viz-modern`/`viz-dark` there from the model's mark makes
every existing rule per-assembly.~~ True of the viewer, and only of what that wrapper encloses:

| # | Site | Shell | Why it is separate |
|---|---|---|---|
| 1 | `.vs-object-parent-container` — `vs-object-container.component.html:42` | viewer | the wrapper this section already named; encloses the assembly component itself |
| 2 | `.object-editor` — `editable-object-container.component.html:18` | composer | the composer does not use `.vs-object-parent-container` at all. `vsObject` is already in scope there (`:22` binds `[class.group-container]` off it), so this is one more binding, not a restructure |
| 3 | `<mini-toolbar>` — `vs-object-container.component.html:341` | viewer | **a DOM sibling of wrapper 1, not a descendant** |

**Site 3 is the one that would have been missed.** `<mini-toolbar>` sits outside the wrapper's closing `</div>`
at `:340`, and `_moving-resize.scss` addresses it with `+` adjacent-sibling combinators (`:24,27,44,47,53`).
The arrangement is deliberate, not incidental: `mini-toolbar.component.scss:141` records that the toolbar is a
sibling so that moving the pointer between object and toolbar behaves, and `mini-toolbar.component.ts:54`
depends on the wrapper's server-assigned `z-index` being a sibling's. So it must not be moved inside — it
takes the class directly. Its two live rules, `:host-context(.viz-modern)` at `mini-toolbar.component.scss:81`
and `:200`, then match on the host itself with no selector edit. `vsObject` is in scope at `:341` already
(`[dataTipName]="vsObject.absoluteName"`).

**Why missing site 3 would have been expensive rather than merely wrong:** the mini-toolbar *is* the anchored
toolbar rollout, item F's shipped slices 1–3 — the most visible work in the track. It would have gone quietly
legacy in every dashboard the moment the class left `body`.

What must change is the body class, renamed (`viz-shell` / `viz-shell-dark`) for the genuinely org-level
surfaces so it stops matching assembly-scoped rules. 35 occurrences across nine stylesheet and template
files — exact at `8ef511e45` — of which `_viz-tokens.scss` (14, not the 15 first recorded) and
`_themeable.scss` (9) are most of the count.

~~**Density stays on the body.** It is an org preference; the mark only decides whether an assembly honours
it.~~ **Corrected 2026-08-19, verified at `8ef511e45` — the second sentence is right and the first does not
follow from it.** The density *preference* stays exactly where it is: one org setting, read from `SreeEnv`,
shipped once on `SetViewsheetInfoCommand`'s infoMap. But the density **class** has to ride along on the
assembly wrapper beside `viz-modern`, because `_viz-tokens.scss` declares the token matrices as **compound**
selectors that require both classes on one element:

```scss
.viz-modern,
.viz-modern.viz-density-dense       { --inet-viz-row-height: 20px; ... }  // :109-117
.viz-modern.viz-density-compact     { --inet-viz-row-height: 24px; ... }  // :120
.viz-modern.viz-density-comfortable { --inet-viz-row-height: 28px; ... }  // :130
```

Leave density on `body` while `viz-modern` moves to the wrapper and no element carries both — and the failure
is **silent rather than loud**, because the bare `.viz-modern` in that first rule group still matches: every
modern assembly renders at **dense**, in every org, whatever the admin chose. Row height, cell padding,
toolbar height, control height and font size all snap to the 20px column. The block comment at `:140-142`
confirms the sheet was written assuming `viz-modern`, `viz-density-*` and `viz-dark` sit on one element
together. `.viz-dark` is a single class and is unaffected.

**So the wrapper carries `viz-density-<mode>` exactly when it carries `viz-modern`** — which is this
paragraph's own second sentence expressed in the DOM, and §"Refinements" already states it: *density stays a
live org preference; the mark decides whether an assembly honours it.* Zero selector edits, which is the
property this section trades on. No new model field either: the density string is already on the client
(`viewer-app.component.ts` `this.vizDensity`, `composer/app.component.ts` `model.vizDensity`); only the
*whether* comes from the assembly.

**Rejected — rewriting the three compounds as descendant selectors** (`.viz-density-compact .viz-modern`).
Three selector edits, and it inverts the specificity the file's source-order comment depends on: `.viz-dark`'s
single-class block currently wins by coming later, and a descendant selector would outrank it.

**The TS half is 17 call sites** of `GuiTool.isVizModern()` / `isVizDensityAtLeastCompact()`. **Re-counted
2026-08-19 at `8ef511e45`:** the total is unchanged but the distribution moved — `chart-actions.ts` (4),
`abstract-vs-actions.ts` (**2**, not 3), `mini-toolbar.service.ts` (2), `chart-tool.ts` (2), one each in
`table-actions.ts`, `crosstab-actions.ts`, `calc-table-actions.ts`, `date-tip-helper.ts`,
`highlight-pane.component.ts` and `tooltip-tail-placement.ts`, and **one inside `gui-tool.ts` itself**
(`getMiniToolbarHeight`, `:73`), which is not a consumer to re-key but the helper's own use of its sibling.
So sixteen sites to convert across ten files, all of which already hold the assembly model, plus one
internal call that follows whatever `isVizModern()` becomes.

**The mark reaches the browser on `VSObjectModel`** — one field on the base class (`:559` onward), populated
in the constructor that already receives the info (`VSObjectModel(T assembly, RuntimeViewsheet rvs)`), and
inherited by every assembly model. Data tips and tooltips are appended to `body` rather than inside the
wrapper, so they take the class explicitly from their source assembly's model; `date-tip-helper.ts` and
`tooltip-tail-placement.ts` are already among the 17.

**A serialization note for P5.** Jackson serializes `VizMark` by `name()` (`MODERN_LIGHT`), not `value()`
(`modern-light`); the browser needs to key off whichever one actually reaches it, or the enum needs
`@JsonValue` on `value()` — not added here, untested in P1.

**Amended 2026-08-19 — send the resolved context, not the raw mark, and the serialization note above stops
mattering.** Decision 13 leaves the `gate &&` term alive until P6, so between P5 and P6 a raw mark on the
model would have the browser call an assembly modern while the server calls it legacy, in exactly the
gate-off case an admin is most likely to try. Populating the field from `VizContext.of(info)` — the two
booleans the server has already resolved — is correct before P6 and after it, needs no edit when the term
dies, and keeps one predicate in one place instead of teaching the client what the org gate is. The
alternative, shipping the gate to the client and re-evaluating `gate && mark != null` there, is a second copy
of the rule in a second language; `SetViewsheetInfoCommand` already carries `modernVisualization` in its
infoMap, so it is available, which is precisely the trap. **P5 carries resolved `modern`/`dark`; the mark
itself never needs to leave the server.**

**A correction to the roadmap.** It records that "M does not gate F" — true, the toolbar rollout persists
nothing. But decision 4 turns the toolbar's *gate* test into a *mark* test, so shipped slices 1–3 are
**modified** when this lands. M does not block F; M changes F. Named here so it is not met as a regression.

---

## 4 · The enumeration point and Modernize

### Decision 11's virtual method

`setDefaultFormat` keeps building and installing the composite. The modern tier moves to a virtual hook that
both creation and Modernize call.

**Five corrections, 2026-08-18.** What follows replaces what this subsection said before. They were found by
reading the code the extraction lands in, at `119bfdaac`. The shape survives — one virtual method both paths
call — but its name, its signature, its contents and its contract all move. Line numbers below are current at
`119bfdaac`; the pre-P2 ones this subsection carried were one to two lines off.

**1 · The name must change: `applyModernDefaults` is taken.** Three classes already ship a *static,
read-time, non-mutating* method of that exact name and near-identical signature —
`VSTitleChromeDefaults:66,90`, `VSOutputChromeDefaults:89,111` and `VSCalendarChromeDefaults:45`, most with an
`…InPlace` variant — which resolve modern chrome onto a clone at model or export time and persist nothing. A
virtual, creation-time, *persisting* method sharing that name reads as one of that family and will be
maintained as one. **Settled 2026-08-18: `seedChromeDefaults(VizContext ctx)`** — accurate rather than
aspirational, because the hook carries the legacy branch of each ternary too (correction 3). Open item 6.

**2 · The signature is `protected void seedChromeDefaults(VizContext ctx)`, mutating `this`** — not
`(VSCompositeFormat fmt, VizContext ctx)`. The seeds are not all format-tier, so a composite-only parameter
cannot carry them. §1's ordering constraint already enumerates them, and the last row below is unreachable
through a `VSCompositeFormat` at all:

| Seed | Where | Tier |
|---|---|---|
| `objectBorderColor(ctx)` → all four border colours | computed `VSAssemblyInfo:1192-1193`, applied `:1223` | object format |
| card radius, gated by `isCornerSeedTarget()` | computed `:1196-1197`, applied `:1224` | object format |
| `cardBackgroundCss(ctx)` | `ChartVSAssemblyInfo:90`, `TableDataVSAssemblyInfo:1578-1579` | object format |
| `pageBackgroundCss(ctx)` | `ViewsheetVSAssemblyInfo:241-242` | object format |
| `barCornerRadius 0.3`, `modernCornerSeed`, `smoothLines`, `modernSmoothSeed` | `ChartVSAssemblyInfo:95-100` | **`PlotDescriptor`** |

The last row is the reason. Those four values are gate-conditional, they persist, and a freshly created gate-on
chart carries them — so a Modernize that cannot reach them produces a *marked* chart that is not a *modern*
chart, and P3's verification claim below fails on its own terms.

**3 · Only the gate-dependent values travel; the unconditional defaults stay in `setDefaultFormat`.** This
subsection previously moved "the default-tier computation", and P3 below said "move the three subclass
overrides' seeds into it". Each override mixes both kinds: `ChartVSAssemblyInfo` also sets
`setPadding(10,10,10,10)` at `:87` before `super` and `LegendsDescriptor.setRoundCorners(true)` at `:93` after
it, both unconditional; `TableDataVSAssemblyInfo:1585` also sets `setTableStyleValue(DEFAULT_STYLE)`; the base
also sets the object font (`:1225`) and the title background, alignment and font (`:1241-1243`). Those are
creation defaults, not modern ones, and a Modernize that re-ran them would reset an author's padding, table
style and fonts — the same destruction the contract guards against, one tier below the composite it names.
The five rows above are the whole of what moves.

Keep the `ctx.modern ? modern : legacy` ternaries inside the hook rather than lifting only the modern branch.
Creation then calls the hook with the assembly's own context and gets today's behaviour on both sides of the
gate, Modernize calls it with a marked context, and `setDefaultFormat` is left with no gate-dependent
expression at all — which is exactly what §1 says the ordering constraint buys. One consequence for P4: the
four creation-path `VizContext.ofGate()` calls (`VSAssemblyInfo:1191`, `ChartVSAssemblyInfo:89`,
`TableDataVSAssemblyInfo:1579`, `ViewsheetVSAssemblyInfo:240`) collapse into one at the hook's call site, so
P3 hands P4 a smaller surface than it would otherwise have. At creation the two factories agree — the stamp
precedes the seed, so `of(this)` and `ofGate()` resolve identically — which is why this is safe to do before
the flip rather than as part of it.

**4 · Three overrides carry modern seeds, and one of them is on a different overload.**
`ViewsheetVSAssemblyInfo:238` overrides the **one-argument** `setDefaultFormat(boolean)`, not the
three-argument form this subsection cited; `ChartVSAssemblyInfo:86` and `TableDataVSAssemblyInfo:1575`
override the three-argument form. The chain is 1-arg → 2-arg → 3-arg, so a single hook call in the
three-argument body reaches every creation path, and the sheet's override is left empty and deleted. For
scale, the hierarchy holds four overrides of the three-argument form (`ChartVSAssemblyInfo:86`,
`GaugeVSAssemblyInfo:234`, `TableDataVSAssemblyInfo:1575`, `TabVSAssemblyInfo:56`) and thirteen of the
one-argument form; only the three named touch a `VizContext`. `SelectionBaseVSAssemblyInfo:139`'s context read
is `getEffectiveCellHeight()`, a P4 read path rather than a seed — selections take their modern corner and
border from the base.

**Call the hook as the last statement of the three-argument body, after `setCSSDefaults()`.** Every subclass
seed today runs after `super.setDefaultFormat(…)` returns, which is after `setCSSDefaults()`, so an earlier
call site silently reorders them. `setCSSDefaults()` is also the second reason Modernize must call the hook and
never `setDefaultFormat`: it resizes the assembly from the stylesheet (`:1438-1448`) and can stamp
`setUserTitleHeight(true)` from a stylesheet title height (`:1471-1476`), and Modernize may do neither.

**5 · The contract is per path: mutate the composite already installed at each path the hook touches, and
never `fmtInfo.setFormat(path, new VSCompositeFormat())`.** The previous wording named OBJECTPATH alone —
`setDefaultFormat` does `new VSCompositeFormat()` (`:1185`) → `setFormat(format)` (`:1237`) →
`fmtInfo.setFormat(OBJECTPATH, fmt)`, leaving the new composite's USER tier empty: harmless at creation,
destructive on recompute. That is right, and incomplete. The same body installs a fresh `tformat` at TITLEPATH
for every `TitledVSAssemblyInfo` (`:1240-1251`); `ChartVSAssemblyInfo:103-107` installs a fresh TITLEPATH
composite of its own; `SelectionBaseVSAssemblyInfo:873-925` installs fresh composites at DETAIL (`:886`),
TITLE (`:901`) and fifteen measure paths (`:910`, `:918`, `:925`). Each of those drops an author's USER tier at
the path it touches, so stating the rule for OBJECTPATH only leaves the same defect reachable at every other
path the seeds write — TITLEPATH, DETAIL and fifteen measure paths, seventeen in all.

**Settled 2026-08-18: the hook does write at TITLEPATH.** The one gate-dependent title value is the border
colour, applied from `bcolors` at `:1210`, and the hook mutates the composite already installed at that path —
never installs one — so a Modernized assembly's title border matches a freshly created one and P3's
verification claim holds as written. DETAIL and the fifteen measure paths carry no gate-dependent value, so the
hook does not touch them; they are named above because the contract has to forbid re-installing them, not
because the hook writes there. Open item 7.

Two things travel with the extraction:

- **The `format.css` TableStyle branch** (`:1212-1218`) comes along, so Modernize picks up the customer's
  current table style rather than dropping it. Its real scope is narrow — tables only, and only when
  `border` is true. **It needs no `border` parameter on the hook:** the branch also tests
  `this instanceof TableDataVSAssemblyInfo`, and every subtype of that class creates with `border` true
  (`TableVSAssemblyInfo:75`, `CrosstabVSAssemblyInfo:61`, `CalcTableVSAssemblyInfo:66`,
  `CrossBaseVSAssemblyInfo:57`, and `EmbeddedTableVSAssemblyInfo`, which inherits `TableVSAssemblyInfo`'s), so
  inside the hook `table` implies `border`. The object border colour and radius are applied in the
  `if(setFormat)` block (`:1223-1224`) and never depended on `border` at all.
- **The title-border ordering oddity is preserved deliberately.** The title border colour is set at `:1209`,
  *before* the stylesheet override at `:1211-1217`, so a table's stylesheet colour reaches the object border
  and never the title border. This predates all of this work. A refactor is exactly when someone notices and
  "fixes" it, which would change rendering. Preserve unless someone confirms it is a bug.

**Rejected — a static helper alone, with no virtual method.** Seeding is already polymorphic across three
subclasses, so a static helper would need either an `instanceof` switch (a second copy of the type knowledge
`isCornerSeedTarget()` already holds at `VSAssemblyInfo.java:1261`) or a convention that each subclass calls
the helper and then adds its own values — which is the drift being prevented. The static helper still exists,
holding the constants and shared computation.

**Built 2026-08-18.** The hook shipped with exactly the name, signature and call site corrections 1, 2 and 4
specify: `protected void seedChromeDefaults(VizContext ctx)` on `VSAssemblyInfo`, called as the last statement
of the three-argument `setDefaultFormat` body (`VSAssemblyInfo.java:1234`), with overrides on
`ChartVSAssemblyInfo`, `TableDataVSAssemblyInfo` and `ViewsheetVSAssemblyInfo`. `ViewsheetVSAssemblyInfo`'s
one-argument `setDefaultFormat(boolean)` is deleted; nothing outside the class called it. `grep -rn
"VizContext.ofGate()"` across the four seed-site classes now returns exactly one hit — the call inside
`setDefaultFormat` — confirming correction 3's prediction that the four creation-path reads collapse into one.
That single site is what P4 flips to `of(this)`; P3 hands it a smaller surface than the original design
estimated.

**Built 2026-08-18, and three more ways a type opts out of the base chrome than the corrections above found —
all now expressed as two predicates the hook itself consults, rather than as a caller-side filter.**
Re-reading every override while extracting the hook surfaced a defect class the corrections had not covered:
types that never reach the base body at all, and types that discard what it writes.

Seven types override `setDefaultFormat` without calling `super`, building their own object format and
hardcoding `DEFAULT_BORDER_COLOR` rather than asking `VSObjectChromeDefaults`: `CheckBoxVSAssemblyInfo`,
`ComboBoxVSAssemblyInfo`, `RadioButtonVSAssemblyInfo`, `SpinnerVSAssemblyInfo`, `SubmitVSAssemblyInfo`,
`TextInputVSAssemblyInfo` and `TextVSAssemblyInfo`. `TextVSAssemblyInfo.java:73-92` is the clearest: its own
`setDefaultFormat(boolean border)` builds a fresh `VSCompositeFormat`, sets the border colours from
`DEFAULT_BORDER_COLOR` directly (`:84-87`) when `border` is true, and calls `setFormat`/`setCSSDefaults()`
without ever reaching `VSAssemblyInfo`'s body — `seedChromeDefaults` never runs for it at creation, gate on or
off. None of the seven has a subclass anywhere in `core`, `enterprise` or `connectors`, so the list is exact
rather than approximate. `CalendarVSAssemblyInfo` bypasses differently: its `initDefaultFormat` (`:88-92`)
calls `setFormatInfo`, `setCSSDefaults()` and sets a hardcoded title height directly, never calling
`setDefaultFormat` at all — the same reason `isCornerSeedTarget()` already excludes it. Left unguarded,
Modernize would give a modernized checkbox or text assembly a border colour no freshly created one of the same
type has ever taken, breaking the "same persisted values as a fresh assembly" claim P3 exists to hold.

`ChartVSAssemblyInfo` and `SelectionBaseVSAssemblyInfo` bypass a narrower slice the same way, at the title path
alone. Both replace the whole TITLEPATH composite after `super.setDefaultFormat` returns: `ChartVSAssemblyInfo:
93-97` installs a fresh composite with its own font and alignment, and `SelectionBaseVSAssemblyInfo:872-901`
installs fresh composites at DETAIL (`:886`), TITLE (`:889-901`) and fifteen measure paths, with the title one
carrying a bottom-only border and a hardcoded `new Color(0xc0c0c0)` (`:897-899`) rather than the base's border
colour. A title-border write the hook makes lands on a composite either constructor discards immediately
afterward at creation, so writing it there is inert — but the identical write reaches a *live* composite under
Modernize, where nothing runs afterward to discard it. Left unguarded, a modernized selection list's title
border would turn from `0xc0c0c0` to the modern border colour while a freshly created one keeps `0xc0c0c0` —
the same class of defect as the seven above, one path narrower.

Both findings are expressed as private predicates on `VSAssemblyInfo`: `bypassesBaseChrome()` (`:1311-1320`),
checked first in `seedChromeDefaults` and returning before any base seed runs, and `installsOwnTitleFormat()`
(`:1328-1331`), checked only around the title-border write at `:1262`. Both deliberately mirror
`isCornerSeedTarget()`'s existing shape (`:1289-1301`) — an explicit `instanceof` list with an explanatory
comment — over a shared-ancestor override, because no ancestor isolates the affected types without also
catching a sibling that must keep seeding normally: `ListInputVSAssemblyInfo` covers three of the seven
cleanly (`ComboBoxVSAssemblyInfo`, `CheckBoxVSAssemblyInfo` and `RadioButtonVSAssemblyInfo` are its only
subclasses), but `ClickableOutputVSAssemblyInfo` — the shared parent of `SubmitVSAssemblyInfo` and
`TextVSAssemblyInfo` — also parents `ImageVSAssemblyInfo`, which does not override `setDefaultFormat` and must
keep taking the normal seed; and `NumericRangeVSAssemblyInfo` — `SpinnerVSAssemblyInfo`'s parent — also parents
`SliderVSAssemblyInfo`, which likewise does not override and must keep seeding normally. Both predicates live
on `VSAssemblyInfo` and are consulted by `seedChromeDefaults` itself, not by `VizModernizeUtil`'s caller,
because the caller could not consult a private predicate without either exposing it past the package or
duplicating the type list in a second file — the exact drift `isCornerSeedTarget()`'s own comment already
warns against.

### Modernize

Per-dashboard, composer-only, gate-on only, write permission required, applying modern defaults wholesale to
**unmarked** assemblies and stamping them, as one `@Undoable` composer step (decision 5). Because it routes
through the §4 hook, it is close to: *for each unmarked assembly — stamp it, then run the
computation.*

**Author-provenance flags are not touched.** `userTitleHeight`, `userDataRowHeight`, `userHeaderRowHeight`
and `userCellHeight` record that a person set a value, which the mark never knows. The decisions file's
addendum is explicit that they survive the mark; "wholesale" invites the opposite reading, so it is stated
here too.

**Idempotent by construction** — it only touches unmarked assemblies, so a second run is a no-op.

**Built 2026-08-18: `Viewsheet.getAssemblies(true)` is not the enumeration this needed.** Its private
recursive collector (`Viewsheet.java:3239-3265`) adds a child to the flat list only `if(!(assembly instanceof
Viewsheet))`; when the child *is* an embedded viewsheet it recurses into that child's own children and never
adds the container itself. So the recursive call this subsection's "for each unmarked assembly" line implies
would silently skip every embedded-viewsheet container on the sheet. That matters rather than being academic:
a container's own `ViewsheetVSAssemblyInfo` carries the same page-background seed override as any other sheet
(§4), so a container created under an open gate is already modern, and a modernized dashboard that never
reached its own containers would differ from a freshly created one — the exact defect this phase exists to
close. The non-recursive `getAssemblies(false)` does return direct-child containers, because its filter
(`Viewsheet.java:3198-3220`) tests only wizard-temp and warning-text names, never the assembly's type.
`VizModernizeUtil.unmarked` (`VizModernizeUtil.java:97-101`) therefore enumerates in two passes: the recursive
call for ordinary assemblies, plus a second, non-recursive call collecting only the `Viewsheet`-typed direct
children. A container reached this way is stamped and seeded like any other assembly of this sheet — it *is*
this sheet's content, stored in this sheet's own XML — while its own children stay excluded by the
`isEmbedded()` test in the first pass, because they belong to the embedded asset, not this one.

**A trap for P4 and P5: `isEmbedded()` means two different things depending on the info type.**
`VSAssemblyInfo.isEmbedded()` answers "is my containing viewsheet itself embedded in something else" — the
ordinary meaning, and the one the first enumeration pass above relies on to exclude an embedded sheet's own
children. `ViewsheetVSAssemblyInfo.isEmbedded()` (`:64`) overrides it to `vs != null` instead — "do I have a
parent viewsheet," i.e. "am I a container" — which is true for exactly the containers the second pass exists
to reach, and false only for the top-level sheet. The two meanings coincide for every other info type and
diverge only here, so a read path that calls `isEmbedded()` on a `ViewsheetVSAssemblyInfo` expecting the base's
meaning gets the opposite answer. Nothing in P3 depends on both meanings at once, but P4 and P5's read paths
will consult this method, and neither should be the first place this is discovered.

**A trap of long standing, not new, but worth restating where Modernize meets it: a new assembly inherits its
host's *stored* mark, absence included — never the gate.** `AbstractVSAssembly`'s two-argument constructor
(`:135-136`) reads the host's `getVizMark()` and copies it, `null` included; it does not call
`VizMark.fromGate()`. So on a legacy dashboard, an author who adds a new assembly today gets an unmarked
assembly even with the gate wide open — the sheet is unmarked, so its children inherit unmarked — and the
dashboard stays wholly legacy until someone presses Modernize. Once Modernize stamps the sheet, later additions
inherit the modern mark for free, the same mechanism working in the other direction. This is coherent with
decision 3's per-assembly provenance and with the bar's visibility flag (adding to an unmodernized sheet keeps
`hasUnmarked` true, so the bar stays offered), but it means an author's newly drawn assembly can look legacy
on a dashboard the author believes they are actively editing under the gate — worth stating plainly rather
than leaving P4 and P5 to rediscover it.

### The affordance — a bar, dismissable per composer session

Decided 2026-08-14, refining decision 5, which specified the behaviour but not the surface.

**Composer only.** Never the viewer, and never composer preview, which is a viewer. This follows decision 5's
own rationale — modernizing changes chart geometry and row heights, so it always leaves resizing work behind,
and that is authoring — and it removes the viewer-side plumbing entirely.

**A bar, not a menu item, and dismissable for the composer session.** Visible when: the gate is on **and**
the sheet holds at least one unmarked assembly **and** the user has write permission **and** it has not been
dismissed for this open sheet.

**Dismissal state is client-side, on the composer's open-sheet object** (`ComposerMainComponent`'s `sheets[]`
entry) — never on the server, never in the asset. Three consequences, all intended:

- Switching composer tabs away and back keeps it dismissed. The composer hides inactive sheets with
  `display: none` rather than destroying them, so the sheet object survives.
- Closing and reopening the dashboard brings the bar back.
- Dismissing never dirties the sheet and never triggers autosave — which matters, because the dashboard being
  offered modernization is a legacy asset this design must not write to.

**Visibility is recomputed from the model, not latched**, so the bar disappears when Modernize completes and
reappears if the user undoes it, for free.

**A permanent menu entry accompanies the bar.** The bar is the discovery affordance; without a second route,
dismissing it costs the feature until the dashboard is closed and reopened. The action must be **absent, not
disabled**, where nothing is unmarked — otherwise every fully-modern dashboard carries a permanently greyed
menu item.

---

## 5 · Phases

Seven phases, P0 through P6 — six until 2026-08-19, when decision 13 collapsed the reverse half into P6 and
brought it here. P0 through P4 have landed. The ordering principle is that **the mechanical cost and the
behaviour change never land in the same commit** — that is what makes each phase reviewable and each phase's
verification a claim that can actually be checked. P6 is the one place that principle bends, and knowingly:
its three deletions are mechanical but must land together with the behaviour change, because separating them
strands charts halfway. See P6.

### P0 — discard the pre-mark cohort

Not code. Delete the dev and test dashboards created on `viz-updates` with the gate on. They carry seeded
modern values with no mark; under decision 2 they are never touched again, so leaving them in place means
every later phase is verified against content that is permanently half-and-half. The roadmap already argues
for doing this before the mark rather than after, and it is the cheapest it will ever be.

### P1 — the field

`VizMark`, the nullable field, `writeAttributes`/`parseAttributes` with the clear-on-absent rule, the
`copyViewInfo` carry, and the two constructor stamps — a sheet's own from the gate, an assembly's from its
host — plus the type-conversion exception that keeps a converted object's own mark. **Nothing reads it.**

*Verification:* unit-level — persistence round-trip; absent attribute clears a stamped field; `copyInfo`
carries; a new assembly inherits the host's mark; change-object-type and type conversion both preserve the
source's mark; a gate-off creation stamps nothing; a format reset on an existing assembly never clears its
mark. Plus one manual check that a legacy dashboard opens, saves and re-opens with no
`vizMark` attribute anywhere in its XML. Zero visual change. This is the shape `userTitleHeight` shipped in
at `07c91926e` — "nothing reads the flag yet."

### P2 — `VizContext`, threaded, behaviour-identical

Introduce the class. Widen all eight `VS*Defaults` classes. Every one of the ~115 call sites passes
`ofGate()`. The five chart descriptors' `initDefaultFormat(boolean vs)` becomes `(VizContext)`.

`ofGate()` reads exactly what the statics read today, so this is a no-op by construction.

*Verification:* the whole existing suite green — the portal action specs, unit specs and TL specs, plus the
`core` Java tests; the roadmap's counts at `55c3bad1a` were 255 / 83 / 60, so re-count rather than assume —
and a visual spot-check that a gate-on dashboard is unchanged. A weak claim about correctness and a strong one
about risk, which is why the 115 edits belong in a commit of their own.

### P3 — the enumeration point and Modernize

Extract the hook per §4, whose five 2026-08-18 corrections govern its name, its `this`-mutating signature,
the five gate-dependent seeds it carries, the overload it is called from and its per-path contract; move those
seeds out of the three overrides that hold them; route creation through it; build the action, the bar and the
menu entry. Reads are still `ofGate()`, so nobody's rendering changes unless they press the button.

*Verification:* the claim is sharp enough to assert directly, once stated against the right baseline —
**an assembly created with the gate off and then Modernized holds the same persisted values as one created with
the gate on**, checked by comparing the two infos in a test, per type, in light and dark. Corrected 2026-08-18:
the baseline is a *gate-off-created* assembly, not an arbitrary legacy one. Under §4's third correction the
hook writes only the gate-dependent values, so a real legacy assembly whose author changed its padding, table
style or fonts differs from a fresh one at those values *correctly*, and comparing against it would fail on
differences that are right. Three assertions the old signature could not have made: the four `PlotDescriptor`
seeds are in the compared set, so is the TITLEPATH border colour, and a user-tier title font set before
Modernize survives it (§4's fifth correction). Plus: idempotence; the bar's four visibility conditions; dismissal surviving a tab switch and not
surviving a reopen; one undo step restoring the unmarked state and the bar with it; a mixed dashboard stamping
only its unmarked assemblies (decision 3).

P3 comes before the flip rather than after because it is what makes the flip testable: without Modernize
there is no marked cohort to compare against, and every scenario needs a hand-built assembly.

**Built 2026-08-18, on `viz-updates`, uncommitted.** Every file this phase touches — the hook and its three
overrides, `VizModernizeUtil`, the composer endpoint and service, the `modernizable` flag, the bar and its
menu entry — sits in the working tree rather than in history. A commit-approval gate denied `git add` for
every one of this phase's tasks, including this document's own corrections above; the human partner commits
those directly. So this section names no commit range, because none of the phase's implementation exists in
history yet — only on disk. The unit-level half of the verification above held exactly as stated: a
gate-off-created assembly Modernized under the gate matches a gate-on-created one of the same type, per type,
in light and dark, including the four `PlotDescriptor` seeds, the TITLEPATH border colour, and a user-tier
title font surviving the pass. Two seeding gaps the corrections above had not found were closed during the
build rather than left for later — the seven-type-plus-Calendar bypass and the Chart/SelectionBase title
exclusion, both recorded earlier in this section — because leaving either open would have made this phase's
own headline claim false for exactly the types it missed.

**The eleven manual checks the plan calls for are outstanding. Nobody has run them, and nothing in this
document should be read as though they had.** They need a built and running server, a browser, and a legacy
dashboard, none of which this pass had. Numbered as the plan states them, plus a twelfth this build adds.

**Check 1 was attempted on 2026-08-18 and its original wording was wrong, in a way worth recording
because it makes a correct build look broken.** It read as though a gate-on legacy dashboard should look
like a gate-off one. It should not, and will not until P4. P3 changes no render-time read — no
`VS*Defaults` resolver, no chart pipeline, no export path, no table lens, and 35 files still call
`VizContext.ofGate()` on read paths — so the gate continues to decide appearance for every assembly
whatever its mark. A gate-off/gate-on comparison duly showed differences in title colour, the table
header and total-row bands, axis and gridline colour, and card border warmth, every one of them inherited
from 6A, phase 7, 9C and the table structure palette rather than from this phase. P4 is where an unmarked
dashboard starts rendering legacy. The corrected check 1 below fixes the baseline.

The same interim state has a second visible consequence, left in place deliberately: the bar's message
speaks to provenance rather than appearance, so in the P3-only state it offers to modernize a dashboard
that already looks modern. The copy becomes accurate at P4, so it was not reworded.

1. **Gate on throughout, and the baseline is this branch before P3 rather than the gate-off look.** A
   legacy dashboard renders exactly as it did with the gate on before this phase, and the bar is the only
   new thing on screen. Best run as an A/B: stash the P3 files, screenshot, restore, screenshot, compare.
   Use the same data state for both shots, since an active brush or date-comparison selection changes the
   marks.
2. Dismiss it. It stays gone across a tab switch away and back; the canvas's Modernize Dashboard menu entry
   is still there.
3. Close the dashboard and reopen it: the bar is back.
4. Press Modernize. Cards take the modern border, radius and background; charts take rounded bars and smooth
   lines. The bar disappears without a refresh of its own.
5. Ctrl+Z: everything reverts and the bar returns, as one undo step, not several.
6. Modernize again, then run it a second time: nothing changes on the second run.
7. Set a title height by hand on one assembly, then Modernize: that height survives. Same for a table style
   and a hand-picked cell font.
8. Save, close, reopen: the modernized state persisted, and the bar does not return.
9. Create a new dashboard: no bar, because new content is marked at creation.
10. Turn the gate off and reopen the legacy dashboard: no bar, and the canvas menu has no Modernize entry.
11. Open composer preview and the viewer on a legacy dashboard: no bar in either.
12. **Added here, per the accepted cost above.** Press Modernize on a dashboard where it finds nothing to do
    — gate on, already fully marked, or the second press in check 6 — then Ctrl+Z. Confirm what actually
    happens to the undo stack: an empty checkpoint is the accepted cost, but nobody has watched one land.

Until these run, P3's claim rests on the unit-level comparison alone. That comparison is real, and it is what
the tests above assert — but it cannot see a live composer session, a live undo stack, or a bar rendered in an
actual browser, which is exactly what checks 1–3, 5 and 9–12 exist to catch.

### P4 — flip the server reads to the mark

`ofGate()` → `of(info)` across the eight classes, plus the `gate &&` interim term. **This is the behaviour
reversal**, and it is where dev dashboards go legacy.

*Verification:* an unmarked dashboard under gate-on renders legacy; a Modernized copy of it renders modern;
export agrees with view for both; the gate off reverts read-time chrome on marked assemblies; dark off no
longer strands anything, which closes decision 9's reproducible defect. A manual export spot-check across
PDF, PNG and Excel — the chrome colours move even though no geometry does.

**Three things P4 will trip over, found by the whole-branch review of P3 on 2026-08-18.** None of them is
caused by P3; all three were latent and became visible once the seeds and the mark existed side by side.
They are recorded here rather than left to be rediscovered, because each one changes what P4 has to decide
before it writes any code.

**1 · The seeds and the mark already disagree at creation, so "unmarked" does not mean "looks legacy".**
`AbstractVSAssembly`'s two-arg constructor gives a new assembly the host's *stored* mark, absence included
(`:135-136`), while `setDefaultFormat` seeds from `VizContext.ofGate()`. An assembly added to a legacy,
unmarked dashboard under an open gate therefore persists the full modern set — border colour, card radius,
card background, and all four `PlotDescriptor` seeds — while carrying no mark at all. When P4 flips the
read to `of(info)`, that assembly reads legacy over modern persisted values: a chart that looks modern today
visibly un-modernizes on the flip, and the persisted seeds stay behind as orphans. Two consequences P4 must
choose between: either the flip accepts that cohort as collateral (they are dev-branch content, and the
pre-mark write-off already covers content created before the mark), or creation stops seeding what it cannot
mark, which means `setDefaultFormat`'s single remaining `ofGate()` call becomes `of(this)` and a gate-on
assembly on an unmarked host seeds nothing. **Decided 2026-08-18: creation flips too.** It is a one-line
change at the site P3 deliberately reduced to one, and it makes the seeds and the reads agree by
construction rather than by coincidence. New dashboards are untouched — the sheet stamps itself, its
assemblies inherit that mark, and they still seed modern; after Modernize the sheet is marked, so later
additions seed modern again. Only assemblies added to an unmarked host change, and they change to match
what they will render. It is still a behaviour change to creation, so it carries its own test rather than
riding on the read-flip tests.

**2 · One reader cannot be flipped at all.** `VSObjectChromeDefaults.resolveSeededCorner` (`:68-70`) reads
the master gate directly, and its own comment says why: it is called from a `VSFormat` getter with no
context to hand. The tab carve-out in `VSCompositeFormat` has the same shape. So after P4 these stay
org-scoped while every neighbouring reader is mark-scoped, and on a mixed dashboard a seeded 12px radius
resolves by the org gate while the border colour beside it resolves by the assembly's mark. **Decided
2026-08-18: document the carve-out, do not thread it.** Threading a context into the `VSFormat` getters
reaches every caller of those getters across render, export, model and dialog layers — a wider exercise than
P4's own 43 sites, inside the phase most likely to regress rendering. The population that renders
inconsistently is assemblies carrying a seeded radius without a mark, which is dev content the pre-mark
write-off already covers and which creation stops producing once the change above lands. So this stays
org-scoped, and is retired by the card-radius change from 12px to 6px that the roadmap sequences behind the
mark and, since 2026-08-19, behind P6 rather than the sweep — but it is not, as an earlier version of this
document claimed, *the last*
org-scoped reader. The whole-branch review that flipped P4's 43 read sites also surveyed the readers P4
never touched, and found four more still org-scoped, plus one that is a P5, not a P4, fix:

- `VSObjectChromeDefaults.java:70`, and `VSCompositeFormat.java:336` reaching it — the corner-radius
  carve-out just described.
- `PlotDescriptor.java:1319` (`getBarCornerRadius`) **and `:635` (`isSmoothLines`)** — the same shape, one
  getter this document already named and a second the phase's own text had left out.
- `AbstractChartInfo.java:3745` — `getTooltipStyle` resolving `AUTO` to `CARD` or `DEFAULT`.
- `VSChartInteractionDefaults.java:48` — whether the plot area is delivered as inline SVG.
- `CoreLifecycleService.java:304-311` — the client-facing flags (`inlineSvg`, `modernVisualization`,
  `vizDensity`, `darkMode`) the browser reads directly rather than through any per-assembly context.

Four of these retire on the schedule already named above: getters with no context to hand, carrying dev-only
inconsistency that the pre-mark write-off covers. `getTooltipStyle` does not, and for a different reason: it
is **deferred to P5 rather than fixed in P4**. Threading it means changing the `ChartInfo` interface getter
(`ChartInfo:889`, plus its default methods at `:901` and `:915`) and rippling into `TipCustomizeDialogModel`,
`ChartPropertyDialogService`, `GraphBuilder` and `VSChartModel`; and its most visible consumer,
`GraphBuilder:148`, ships `tooltipStyle` to the browser on the chart model, where the client still reads
org-gated flags until P5. So an unmarked chart on this branch renders legacy chrome with a modern card
tooltip, and fixing only the server half would buy nothing a user could see.

**P4 and P5 landing back-to-back is therefore a hard condition, not a preference.** Between them, an
unmarked dashboard has legacy server chrome under a modern body class, inline-SVG plots, and card tooltips.

**3 · P3 closed the dark re-stamp door, and it is cheap to reopen only until dashboards carry marks.**
`VizMark.fromGate()` stamps `MODERN_DARK` or `MODERN_LIGHT` from the org's dark setting *at the moment of
stamping*, and `VizContext.of(mark)` derives `dark` from the mark alone. `VizModernizeUtil.hasUnmarked`
looks only for a null mark and `modernize` skips anything already marked. So a dashboard modernized while
the org was in light mode, in an org that later turns dark mode on, reads light chrome on a dark page for
ever: it is fully marked, so nothing offers to restamp it, and no read path consults the org's dark setting
any more once P4 lands. The fix while it is cheap is a re-stamp branch — content whose mark disagrees with
the current gate becomes eligible again, which is a predicate change in `hasUnmarked` and a second mode for
`modernize`. **Decided 2026-08-18: its own small phase after P5 and before release, tracked as a release
condition — not bundled into P4.** It is Modernize-side work, not read-side, so folding it in would add a
second concern to the phase already carrying 43 sites and a manual export pass, and would widen what that
export pass has to cover. Nothing decays while it waits: the write-off means only dev content carries marks
until the branch ships, so the migration this would become never materialises provided the phase lands
before release.

### The shape P4 takes — decided 2026-08-18

The flip is 43 sites across 33 files, and they do not all resolve the same way. The plan takes one task per
group below, because the pattern and the risk differ per group; a single sweeping task would hide the two
groups that need judgement inside forty edits that do not.

| Group | Sites | How it resolves |
|---|---|---|
| Model layer | 9 in 9 files | The constructors already hold the info, so `of(info)`. Mechanical, and this is the group the browser renders |
| Export and painter | 11 in 7 files | `AbstractVSExporter` (4), `VSTableDataHelper`, `VSSelectionListHelper`, `VSSelectionTreeHelper`, `ExportUtil`, `VSCalendar` (2), `VSSlider`. The assembly is available at the prepare/paint seam. This is the export-affecting group |
| Chart pipeline | 6 in 5 files | A `VizContext` field set at construction: `GraphGenerator:218` takes a `ChartVSAssemblyInfo` so `of(chart)`, `:433` takes a `ChartInfo` so `LEGACY`. `CSSChartStyles.apply` gains a context parameter — `of(info)` from `VGraphPair:1353` and `VSChartDndService:224`, `LEGACY` from `CSSProcessor:303` |
| Dialog models | 6 in 3 files | Forward the info that `ChartPropertyDialogService` and `RegionPropertyDialogService` already hold |
| Services and controllers | 5 in 4 files | `BaseTableService` (2), `FormatPainterService` and `ChangeChartTypeService` hold assemblies; `ChartColorPaletteController` keeps `ofGate()` |
| Query and lens | 2 | `DataVSAQuery`, `VSTableLens` |
| Report-only | 3 | `CSSProcessor` takes `LEGACY`; `VsToReportConverter`'s two sites take the source assembly's info |
| Info-local | 1 | `SelectionBaseVSAssemblyInfo.getEffectiveCellHeight()` takes `of(this)` |

The chart-pipeline field settles something P2 left as a convention. `VizContext` encodes "is this a
viewsheet chart at all" as identity against `LEGACY`, defended today by two tests and a comment; a field set
from the constructor that knows the answer replaces the convention with a fact.

**The dialog decision, and its limit.** Threading the two services means a property dialog opened on an
unmarked chart previews legacy chrome and agrees with the canvas behind it — which matters precisely because
this phase's purpose is that unmarked content looks legacy. `ChartColorPaletteController` cannot join them:
its endpoint is a parameterless bootstrap GET with no assembly in scope, cached once per client. It keeps
the org gate with a comment saying why, on the grounds that a global swatch list is not per-assembly chrome.
Replacing it with a per-assembly endpoint would put a client change and an uncacheable fetch inside a
server-only phase.

**Not in P4.** `resolveSeededCorner` and the `VSCompositeFormat` tab carve-out stay org-scoped, per the
decision above. The dark re-stamp is its own later phase. The browser reads are P5. ~~The revert sweep
belongs to the reverse half and the release gate.~~ **Amended 2026-08-19:** there is no reverse half. Revert
is P6, in this document, and the two `PlotDescriptor` seed booleans go with it — so `resolveSeededCorner` and
the tab carve-out are the only two of decision 12's four mechanisms left outside these phases.

**Verification.** Per group, a unit test that a marked info reads modern and an unmarked one reads legacy —
the assertion the whole phase exists to make true. The 23-test characterization net from P3 continues to
guard creation, and the creation flip adds its own. Then the full `core` suite. Then a manual pass heavier
than P3's: an unmarked dashboard renders legacy; a Modernized copy of it renders modern; **export agrees
with view for both, across PDF, PNG and Excel**; the gate off reverts read-time chrome on marked assemblies;
dark off strands nothing. The export half cannot be settled by any test in the suite, which is why it is
called out separately rather than folded into the list.

**What P5 will revisit, carried per task so it is not rediscovered.** The body class becomes a per-assembly
scope, which modifies the behaviour of shipped toolbar slices 1–3. `VSObjectModel` gains the mark field, and
Jackson serialises `VizMark` by `name()` rather than `value()`, so the wire form needs deciding before the
browser keys off it. Seventeen `GuiTool` call sites read the class back off the body today.

**Built 2026-08-18, on `viz-updates`, uncommitted.** All 43 read sites plus the creation-path flip landed as
the shape above specifies, one task per group. `VSAssemblyInfo.java:1235` now reads
`seedChromeDefaults(VizContext.of(this));`, replacing the single creation-path `ofGate()` call P3 had
already reduced the four original creation sites to; the "decided 2026-08-18: creation flips too" paragraph
above is no longer a decision awaiting code. The only call to `VizContext.ofGate()` left anywhere in
`core/src/main` is `ChartColorPaletteController.java:45`, carrying the comment this section specifies,
confirmed by `grep -rnF "VizContext.ofGate()" --include=*.java core/src/main`.

**The plan's own sweep instruction expected two survivors and was wrong about why.** It read `VizContext.java`'s
factory definition, `ofGate() {`, as a second hit alongside `ChartColorPaletteController`. It is not a call:
an unescaped `.` in an earlier `grep -rn` pattern matches the space in `public static VizContext ofGate() {`
(`VizContext.java:48`), which is why the pattern-only sweep and the literal one (`grep -F`) disagree. The
literal sweep, and the persisted guard built to replace it,
`VizContextReadFlipTest.exactlyOneDocumentedSiteStillReadsTheOrgGate`, both return exactly one name,
`ChartColorPaletteController.java`. That test walks every `.java` file under `core/src/main/java/inetsoft`
rather than checking a package list, closing four packages a set of per-package assertions had left
uncovered — `FormatPainterService` (`web/composer/vs/controller`), `ChangeChartTypeService`
(`web/binding/controller`), `VsToReportConverter`'s two sites and `SelectionBaseVSAssemblyInfo` (both
`uql/viewsheet/internal`), and `CSSProcessor` (`report/css`) — any one of which could have grown a stray
`ofGate()` call with nothing in the suite noticing. The guard was watched fail before it was trusted: a
temporary `ofGate()` call was added to `SelectionBaseVSAssemblyInfo`, the assertion failed and named that
file in its message, and the injection was reverted.

Recording `VizContext.of(` for the same reason the sweep records `ofGate()`:
`grep -rncF "VizContext.of(" --include=*.java core/src/main` sums to 43, not "43 plus the creation site" as
a first pass estimated. The literal count includes the creation site itself and
`VizModernizeUtil.java:60`'s own `of(mark)` for the Modernize action, both outside the 43-site read table
above, while the export group's flip moved `ExportUtil.getBackGroundColor`'s `VizContext` read from one call
inside `ExportUtil` (`ExportUtil.java:108-109`, widened to take the context as a parameter) out to its two
callers (`PDFVSExporter.java:653`, `SVGVSExporter.java:374`), each computing `VizContext.of(info)`
independently — a site moved outward, not added. The raw total does not reconcile to the table's group sums
plus one by simple arithmetic; what the sweep exists to confirm, that nothing outside the documented
survivor still reads the gate, holds regardless.

**The full `core` suite passed clean at 4899 run, 0 failures, 0 errors, 67 skipped, `BUILD SUCCESS`.** No
fixture needed a mark added for this phase's own flips: the eight tests across
`ChartVSAssemblyInfoBarRoundingTest` and `VSObjectChromeDefaultsTest` that encoded "gate on means modern" for
an unmarked info were already repaired, fixture by fixture, during the creation flip's own fix round, before
any read site moved — so this run is the first time the full suite has exercised all 43 read-flip sites
together rather than each group's targeted subset. The arithmetic reconciles: 4887 at the P3 baseline, plus
2 for the creation flip's own tests, plus the 10 tests now in `VizContextReadFlipTest` (4 at its
introduction, 6 added one or two at a time as each group's flip landed its own assertion), is 4899.

**The nine manual checks below are outstanding. Nobody has run them, and nothing in this document should be
read as though they had.** They need a built and running server, a browser, a dashboard saved before the
mark existed, and exported PDF, PNG and Excel files, none of which this pass had.

1. **The reversal, and the check that could not have passed before this phase.** Gate on, on a dashboard
   saved before the mark existed: it renders legacy chrome — titles, fonts, axis colours and table banding
   all back to their pre-gate appearance. P3 could not make this true, because nothing yet read the mark;
   this phase's whole purpose is that the check now passes, and until it is run that purpose is asserted
   rather than demonstrated.
2. Press Modernize on it: it renders modern.
3. A dashboard created new under the gate renders modern without any action.
4. Export agrees with view, for both. Export the unmarked dashboard and the modernized one to PDF, PNG and
   Excel and compare each against its canvas. Chrome colours move; geometry does not.
5. Turn the gate off: the marked dashboard reverts to legacy read-time chrome.
6. Turn dark mode off with the gate on: nothing is stranded — no dark card backgrounds under light chart
   chrome.
7. Open a chart property dialog on the unmarked dashboard: the preview shows legacy chrome, matching the
   canvas.
8. A mixed dashboard — one Modernized assembly beside unmarked ones — renders both correctly side by side.
9. Scheduled export of the unmarked dashboard matches its interactive export.

### P5 — flip the browser reads to the mark

`VSObjectModel` field; per-assembly `viz-modern`/`viz-dark` at the **three** binding sites §3 enumerates —
`.vs-object-parent-container` in the viewer, `.object-editor` in the composer, and `<mini-toolbar>`, which is
the wrapper's sibling rather than its child; the body class renamed for the org-level surfaces in all three
shells; the `GuiTool` sites take the model's resolved values.

**Plus one server-side reader P4 deferred here, added 2026-08-19 from `8ef511e45`'s commit message.**
`AbstractChartInfo.getTooltipStyle` resolves AUTO to CARD from the org gate. It was left out of P4 because
threading it changes the `ChartInfo` interface getter and ripples through four more classes, and because its
most visible consumer ships `tooltipStyle` to the browser — which still reads org-gated flags until this
phase, so fixing the server half alone would have bought nothing a user could see. It belongs here for the
same reason it was deferred: both halves land together or the mismatch inverts.

*Verification:* the same two dashboards are consistent end to end; a marked assembly pasted into an unmarked
sheet renders modern beside legacy siblings (decision 3's mixed dashboard); shipped toolbar slices 1–3
behave per-assembly rather than per-org.

*Added 2026-08-19:* the model field carries the resolved `modern`/`dark` from `VizContext.of(info)`, not the
raw `VizMark` — see §3's amendment. Verify with the gate off and a marked sheet open: browser and server must
agree, which they only do if the client is reading the resolved value while the `gate &&` term is still
alive.

### The one interim state worth declaring

**P4 and P5 should land back-to-back, and the commit message should say why.** Between them, an unmarked
dashboard under gate-on has legacy server chrome under modern browser CSS. That is bounded, understood, and
the same class of mismatch the branch carries today — but it is a real intermediate state and eliding it in
review would be dishonest. Splitting them is still right: 115 server edits and ~50 browser edits in one commit
is not reviewable. Server first, so the last commit is the one that makes it coherent.

### P6 — Revert, and the end of the gate's read-time life

**Added 2026-08-19 by decision 13**, which overruled the org-wide sweep this document had left out of scope.
What replaced the sweep is one composer action, so it belongs here beside its mirror rather than in a
reverse-half document that no longer has enough in it to exist.

> **Reviewed against the code 2026-08-20, at `35ca4fce0` (P5 shipped).** Every citation below verified; six
> findings changed the phase, four of them decided the same day. The largest: **this section's claim that the
> two `PlotDescriptor` booleans are "the only reads left that still test the org gate" is false.**
> `VSObjectChromeDefaults.resolveSeededCorner` is a third, and it strands card corners exactly the way the
> booleans strand bar corners — so it joins the same-commit set as piece 4, and the phase has five pieces
> rather than four. Two further gate reads survive P6 as documented accepted costs, and three items the
> section did not list are folded in. All of it is below, in place; nothing here was built when the review
> ran.

Five pieces, and pieces 1–4 must land in the same commit:

1. **`VizModernizeUtil.revert(Viewsheet)`** — the mirror of `modernize()` (`VizModernizeUtil.java:53-69`).
   Same enumeration with the predicate inverted: the sheet's own **marked** infos, including the sheet's own
   `ViewsheetVSAssemblyInfo` and the second non-recursive pass for embedded-viewsheet containers that
   `unmarked()` already gets right. Clear the mark, then call `seedChromeDefaults(VizContext.of(info))` —
   which now resolves unmarked and therefore writes the legacy branch of every ternary. **No reverser is
   authored; it is the creation call**, which is what decision 12's "revert calls the legacy creation path"
   was asking for and what decision 11's enumeration point makes free. A `hasMarked(Viewsheet)` beside
   `hasUnmarked` feeds the affordance.
2. **`ChartVSAssemblyInfo.seedChromeDefaults` gains its `else` branch** (`:106-112`, `if(ctx.modern)` at
   `:106` with no false side), writing the legacy values ~~`barCornerRadius = 0` and `smoothLines = false`~~
   **`barCornerRadius = 0` and the type-derived legacy `smoothLines`**, and
   `PlotDescriptor.modernCornerSeed` and `modernSmoothSeed` are deleted with their fields (`:1996`, `:2009`),
   XML read/write attributes (`:1534-1535`, `:1580-1583`, `:1721-1722`, `:1742-1744`), `equals` terms
   (`:1876-1877`, `:1902-1903`) and self-clearing setters (`:648-652`, `:1335-1339`). The two gate-reading
   getters (`isSmoothLines()` `:633-636`, `getBarCornerRadius()` `:1317-1320`) collapse to plain accessors.
   Non-negotiably this phase — see §2's amendment for what stranding them would look like.
   **Two production consumers this section did not list**, both found by the 2026-08-20 review:
   `ChangeChartTypeService.java:341` sets `setModernSmoothSeed(true)` beside its `setSmoothLines(true)` on a
   type change to Line, and `ChartPlotOptionsPaneModel.java:227-229` guards its radius write solely to keep a
   no-op OK from laundering a seeded radius into an authored one. The first loses one line; the second loses
   its `if` and writes unconditionally.

   **Corrected 2026-08-21 by the whole-phase review: `smoothLines = false` is wrong, and this document is
   what prescribed the constant.** `smoothLines` is not only a modern seed — it is also a chart-**type**
   default, with no modern term anywhere. `ChangeChartTypeService.applySmoothLinesTransition` and
   `VSWizardBindingHandler.applyWizardSmoothLines` both set it `true` for non-step Area, Area Stack and
   Circular Network. A constant `false` on the legacy branch therefore straight-lines every chart of those
   three types that Revert touches, a state no legacy Area chart has ever been in, and it breaks the
   byte-comparability check this section closes with in the one direction that check exists to guarantee:
   a legacy Area chart holds `true`, Modernize leaves it `true`, Revert writes `false`, so the two
   directions are not inverse. The `else` branch writes the **type-derived** legacy value instead.
   Three things the correct form depends on:
   - **`GraphTypes.isArea()` is the wrong predicate.** It includes `CHART_STEP_AREA` and
     `CHART_STEP_AREA_STACK` (`GraphTypes.java:331`), which both existing writers deliberately exclude. The
     list is exactly `CHART_AREA || CHART_AREA_STACK || CHART_CIRCULAR`, and it is now extracted once as
     `GraphTypes.isSmoothLinesDefault(int)` with all three writers pointing at it — a third copy inside the
     hook would recreate, on the type-default axis, the drift risk "no separate reverser" exists to prevent.
   - **Design types only.** `GraphTypeUtil.checkType` resolves `CHART_AUTO` through `getRTChartType()`, which
     would write `true` for an AUTO chart whose runtime type happens to be Area — a value no legacy chart of
     that shape holds, because the transition matrix never fired for it.
   - **Multi-style charts carry a type per measure** (`ChangeChartTypeService.java:140` sets `oldType` from
     the per-measure ref), so a per-measure switch to Area leaves the info-level type non-Area. The hook
     checks the info type and, when `isMultiStyles()`, the aggregate types on the X and Y fields.
   `getVSChartInfo()` can be null (`ChartVSAssemblyInfo.java:1069`, `:1576`), so the hook null-guards it the
   way `applyWizardSmoothLines` already does. **`barCornerRadius = 0` stands unchanged**: it has no type
   default, and resetting an author's hand-set radius is the accepted cost already recorded in decision 13's
   exemption, symmetric because Modernize clobbers it in the forward direction too.
3. **The `gate &&` term goes** from `VizContext.of(VizMark)` (`VizContext.java:66`), leaving
   `modern = mark != null`. `ofGate()` survives, for `ChartColorPaletteController` and for stamping at
   creation; the persisted guard `VizContextReadFlipTest.exactlyOneDocumentedSiteStillReadsTheOrgGate` keeps
   holding unchanged — **and note what that guard does not cover:** it counts `ofGate()` call sites, so none
   of the four direct `VSDensityDefaults.isModern()` reads this phase deletes was ever inside it.
4. **`resolveSeededCorner` is retired** — added 2026-08-20, decided the same day, and it is the review's
   central finding. `VSObjectChromeDefaults.resolveSeededCorner` (`:68-70`) reads the gate directly and is
   called from `VSCompositeFormat.getRoundCornerValue()` (`:323-327`) by way of `resolveDefaultTierCorner`
   (`:334-337`) — the composite getter every read path uses. Leave it in place and a marked assembly in a
   gate-off org keeps its modern border colour, card background, page background, density **and** bar
   corners while losing its 12px card radius: the identical stranding piece 2 exists to prevent, on the more
   visible property. Both methods are deleted, `getRoundCornerValue()` returns the raw DEFAULT-tier radius,
   and the TAB exemption `resolveDefaultTierCorner` carried dies with it. Revert already writes `0` to that
   tier, so reverted assemblies need no strip. Four tests in `VSObjectChromeDefaultsTest` (`:213-230`) go
   with it.
   **This is not the card radius 12→6 change**, which stays a follow-on: that is a constant move needing its
   own sign-off, and the roadmap's "Decided, unscheduled" entry for it is about the value, not the gate read.
   What made the retirement wait was needing *a* reversal path to fall back on, and Revert is one.
5. **The wiring**, mirroring P3's exactly: a `RevertViewsheetController`/`Service` pair beside
   `ModernizeViewsheetController`/`Service` (58 and 90 lines respectively), a `revertable` flag beside
   `modernizable` (`CoreLifecycleService.java:314`), a menu entry, and a **confirmation dialog** — the
   `ComponentTool.showConfirmDialog` pattern already in the pane at `viewsheet-pane.component.ts:1448`.

**Three places it deliberately differs from Modernize.**

- **No gate floor.** `modernize()` returns 0 when the gate is closed, because modernizing into a closed gate
  produces content that changes appearance the moment the gate opens. Clearing a mark has no such hazard, so
  `revertable` is "this sheet has marked content" with **no gate term** — decision 13 offers Revert under
  both gate states on purpose.
- **No bar.** P3's dismissable bar exists because Modernize is an offer the product makes. Revert is a
  request the author makes, so it lives in the menu only.
- **A confirmation.** Modernize needs none: it adds chrome, and undo is one step away. Revert discards chrome
  an author may have been working against for months, and the undo step is just as available but far less
  likely to be reached for calmly.

**The one interaction the section left to the plan — settled 2026-08-20: a revert suppresses the offer for
the rest of the session.** `modernizable` is recomputed on every refresh (`CoreLifecycleService.java:314`),
so a sheet reverted with the gate open qualifies again immediately and the Modernize bar returns, offering to
undo what was just done. Reverting therefore sets the same `modernizeBarDismissed` flag
(`composer/data/vs/viewsheet.ts:56`) the bar's own dismiss button sets, so the bar stays gone until the
session ends. One line client-side, and it reuses the per-session dismissal rather than inventing a second
suppression rule. The **menu entry** still recomputes normally — it outlives the dismissal today
(`viewsheet-pane.component.ts:1699-1702`) and there is no reason for Revert to change that.

**Two gate reads survive P6 on purpose, and both are new inconsistencies P6 creates.** Before this phase the
`gate &&` term made a gate-off org consistently legacy, so neither showed. Both are the *same* defect class
already accepted in the gate-on direction, and both are recorded as accepted costs rather than work:

- **`AbstractChartInfo.getTooltipStyle` (`:3736-3746`)** resolves AUTO → CARD/DEFAULT from the org gate, so a
  marked chart in a gate-off org renders modern everywhere and takes a legacy tooltip. This is R20, deferred
  at P5 for a reason that has not changed: `PlotArea` and `ChartArea` hold a `ChartInfo` across five
  constructor overloads whose callers include the report painter, the exporter, annotations and the
  scheduler, several with no assembly in existence. Threading it is larger than the whole of P6. **Decided
  2026-08-20: accept and document.**
- **`VSChartInteractionDefaults.isInlineSvg()` (`:41-49`)** follows the gate unless `graph.svg.inline` is set
  explicitly, so a marked chart in a gate-off org loses inline-SVG animation and hover dimming. It is
  interaction rather than chrome, it is org-scoped by design, and it already has an admin override — which is
  the workaround to name in release notes. **Decided 2026-08-20: accept and document.**

After P6, `VSDensityDefaults.isModern()` has exactly five readers left and every one is correct by design:
`VizContext.ofGate()`, `VizMark.fromGate()` (creation), `CoreLifecycleService` (the `modernizable` offer), and
the two above.

**Three items folded in that this section did not list.**

- **The `if(modern)` guard on the density body class** — carried into P6 from P5 and recorded in the
  roadmap. All three shells add `viz-density-<mode>` only when the org gate is on
  (`portal/app.component.ts:272`, `composer/app.component.ts:145`, `viewer-app.component.ts:2796`). After P6
  a marked assembly in a gate-off org would find no density class on the body and fall back to the bare
  `.viz-modern` dense defaults. One line each, and required under either density design considered.
- **A P5 regression on the same selectors** — found by the 2026-08-20 review. Pre-P5 the matrices were
  compound (`.viz-modern.viz-density-compact`), so a `body` carrying both classes matched. Post-P5 they are
  ancestor-descendant and `.viz-shell` appears only in the dense group (`_viz-tokens.scss:115-117`), so a
  body carrying `viz-shell` + `viz-density-compact` resolves **dense** — a descendant selector cannot match
  its own element. **§3's correction box claims this "matches what a body carrying `viz-modern` +
  `viz-density-<mode>` resolved before the split"; it does not**, and `git show 4c237a7dd^` of the file
  confirms the compound form it replaced. Affected are exactly the org-level consumers §3 names: the
  body-appended combo-box dropdown, the worksheet details pane, the schedule task list. Fix is two added
  selectors, `.viz-density-compact.viz-shell` and `.viz-density-comfortable.viz-shell`.
- **The EM hides Visualization Density behind the gate checkbox**
  (`look-and-feel-settings-view.component.html:38-49`). After P6 density is still read live for every marked
  assembly in a gate-off org — `VizContext.of(mark)` takes `density` from `VSDensityDefaults.mode()`
  regardless — and the body-class fix above makes it apply in the browser too, so an admin loses sight of a
  setting that still does something. Density is unhidden; **Dark Mode stays hidden**, and coherently, because
  the dark axis is stamped into the mark at creation and a gate-off org creates no marked content. The gate's
  own description is rewritten in the same pass, which is what decision 13's "the EM property now says
  something it does not do" asked for.

*Verification:* a dashboard modernized and then reverted is byte-comparable, ~~in the properties this hook
touches~~ **for the values this hook exclusively owns**, with one that was never modernized — the check
decision 12's creation-path routing exists to make possible, and it must be run against a **freshly
created** dashboard, because a pre-mark-cohort asset (P0's subject) carries seeded modern values with no
mark and would make the comparison meaningless; a marked chart in a **gate-off** org renders fully modern
including bar corners, smooth lines **and card radius**, which could not have passed before this phase and
is the clearest single signal that all four deletions went together; undo after Revert restores modern
chrome in one step; a mixed sheet reverts its marked assemblies and leaves its unmarked ones untouched; an
embedded viewsheet's own assemblies are not reverted by reverting its host; and export agrees with view
across PDF, PNG and Excel, since every value Revert writes is persisted and painter-read.

**Why the qualifier, added 2026-08-21 with the correction in piece 2.** "The properties this hook touches"
overstated the guarantee, because one of them is not the hook's alone. `smoothLines` has four writers — the
creation seed, the type-transition matrix, the wizard, and the author's Plot Options checkbox — and the mark is
per-assembly, not per-field, so it cannot record *which* values Modernize changed; the boolean that did
record exactly that, `modernSmoothSeed`, is deleted by piece 2. The legacy branch is therefore deriving a
value rather than restoring a remembered one, and any derivation is wrong for some population. The
type-derived form narrows that population from *every Area, Area Stack and Circular chart* to *charts of
those types whose author explicitly turned smoothing off* — and Modernize already destroyed that choice in
the forward direction, so it is unrecoverable either way. Byte-comparability holds for the values the hook
exclusively owns; for `smoothLines` it holds for every chart that has not been hand-edited against its type
default.

**Five existing tests invert, and that is the phase's assertion rather than breakage.** Enumerated while
planning, 2026-08-20 — an earlier draft of this paragraph said two, counting only the `VizContext` pair:

| Test | What it pins today | After P6 |
|---|---|---|
| `VizContextTest.ofAMarkIsLegacyWhenTheGateIsOff` (`:97`) | the `gate &&` term, by name | a mark alone makes it modern |
| `VizContextReadFlipTest.closingTheGateStillRevertsAMarkedAssembly` | the same term, by name | closing the gate reverts nothing |
| `ChartVSAssemblyInfoBarRoundingTest.seededBarRadiusRevertsWhenGateTurnedOff` | 0.3 → 0 on gate-off | 0.3 survives |
| `ChartVSAssemblyInfoBarRoundingTest.seededSmoothLinesRevertsWhenGateTurnedOff` | true → false on gate-off | true survives |
| `VSCompositeFormatRoundCornerGateTest.defaultTierSeedStrippedGateOff` | 12 → 0 on gate-off | 12 survives |

**And a deletion surface larger than the production diff, which is worth knowing before budgeting the
phase.** The two seed booleans are asserted across six test files — `PlotDescriptorXmlTest` (roughly a
dozen tests, most of them wholly about the booleans), `ChartVSAssemblyInfoBarRoundingTest`,
`ChartVSAScriptableTest`, `ChangeChartTypeServiceSmoothLinesTransitionTest`,
`ChartPlotOptionsPaneModelTest` (a whole `:426-565` block on no-op-save seed preservation) and
`VSWizardBindingHandlerSmoothLinesTest` — and `resolveSeededCorner` has a dedicated file of its own,
`VSCompositeFormatRoundCornerGateTest`, most of which dies with the strip, plus three tests in
`VSObjectChromeDefaultsTest` (`:213-230`). The production change is four deletions and one `else` branch;
the test change is the larger half.

---

## Refinements this design makes to the decisions file

Two, both because the decision as written had no implementable form.

**Decision 3's host mark is the sheet's own mark.** The decisions file says a dashboard's modern-ness is
"derived — every assembly in it is marked," which has no answer for a newly created empty sheet, and the
sheet already carries a persisted seed of its own (the page background, `ViewsheetVSAssemblyInfo:238`). So
the sheet's `ViewsheetVSAssemblyInfo` carries a real mark, stamped from the gate at creation, and that is the
host mark new assemblies inherit.

**Decision 4's "density heights key off the mark" means whether, not which.** Density stays a live org
preference read from `SreeEnv`; the mark decides whether an assembly honours it. See §2.

---

## Accepted costs

**The pre-mark cohort is written off**, per the decisions file, and P0 disposes of it rather than inheriting
it. The write-off holds only while `viz-updates` is unreleased.

~~**The three persisted colour seeds stay modern in a gate-off product.** Pre-existing, unchanged by this
design, fixed by the sweep. See §2.~~ **Reframed 2026-08-19: this stopped being a cost and became the
specification.** Under decision 13 a marked assembly is *meant* to stay modern when the gate closes, colours
included. Nothing fixes it because nothing is broken.

~~**The gate-off state is only partly reverted until the sweep lands.** Read-time chrome reverts via the
`gate &&` term; the persisted seeds do not. This is the status quo, not a regression.~~ **Superseded
2026-08-19.** Gate-off reverts nothing at all after P6, by design. The partial reversion above is now a
description of the P1–P5 window rather than of the end state.

**New 2026-08-19, and the real cost of decision 13: there is no org-wide off switch.** Backing an estate out
of modern is a per-dashboard job. Stated in full in the decisions file's decision 13, including the two
things that soften it — a scripted bulk revert and "revisit if customers ask" — and neither is committed
work.

**New 2026-08-20, from the P6 review: two chart surfaces stay org-gated after P6, so a marked chart in a
gate-off org is modern except for its tooltip chrome and its inline-SVG interaction.** Both are the gate-off
mirror of a gate-on inconsistency already accepted at P5 (a legacy assembly on a modern org taking modern
tooltip chrome), and both were decided as accepted costs rather than work: `getTooltipStyle` because threading
it means widening five `ChartArea` constructor overloads reached by the report painter, the exporter,
annotations and the scheduler, and `isInlineSvg()` because it is interaction rather than chrome and already
carries an explicit `graph.svg.inline` override for anyone who wants it back. Full reasoning in §5's P6.

**New 2026-08-20: Revert resets bar corner radius and smooth lines even when an author set them by hand.**
`PlotDescriptor.barCornerRadius` and `smoothLines` are untiered single fields, both user-settable in Chart
Plot Options (`chart-plot-options-pane.component.html:266-271`, `:235-240`), and once the two seed booleans
are gone nothing distinguishes an author's `0.4` from the seeded `0.3`. Accepted because it is genuinely
symmetric — Modernize already overwrites the same two values in the forward direction, which is decision 13's
whole argument — and because both alternatives are worse: value-sniffing the seed is the
`resolveSeededCorner` pattern this set is deleting, and keeping a renamed marker contradicts the phase's
subtractive premise. This is the one place decision 13's "Revert writes the DEFAULT tier only" does not hold;
the decisions file is amended to say so.

**A press that finds nothing left to modernize still takes an undo checkpoint.** `@Undoable` is implemented as
`@AfterReturning("@annotation(Undoable) && within(inetsoft.web..*)")` (`EventAspect.java:103-122`), which fires
on any normal method return and calls `makeUndoable` unconditionally — the service has no way to suppress it,
and every other composer action in this tree carries the same annotation the same way, so carving out an
exception for one action would be a new pattern rather than a fix. Accepted rather than engineered around.
Mitigated on the client by clearing `vs.modernizable` the instant the event is sent
(`viewsheet-pane.component.ts:1708-1713`), which closes the likeliest route — a double press before the
refresh lands — by hiding the bar and the menu entry before a second click can reach the endpoint. What
remains is a client whose flag is stale for another reason, such as an admin closing the gate between the
flag being sent and the button being pressed; that user spends one empty undo entry and loses nothing.

**If Modernize throws partway through its loop, some assemblies are stamped, no undo checkpoint is taken, and
no refresh is sent.** The exception propagates out before `@Undoable`'s `@AfterReturning` advice ever runs, so
the composer's model is left believing the dashboard is exactly as it was, until the user does something that
triggers a fresh `setViewsheetInfo`, at which point the flag and the bar both catch up to what actually
happened. Accepted rather than patched, because the alternative is worse on both sides: a per-assembly
`try`/`catch` inside `VizModernizeUtil.modernize` would swallow the real error rather than surface it through
`@HandleAssetExceptions` (`ModernizeViewsheetController.java:46`), and there is no rollback facility for format
mutations to roll back to. It is safe to accept because Modernize is idempotent and unmarked-only by
construction (§4): the assemblies it already stamped keep their marks and their seeded chrome, a second press
picks up exactly the remainder, and nothing is lost or duplicated by trying again.

---

## Open items to settle while writing the plans

Small enough to answer in the plan rather than before it.

1. ~~**`CSSProcessor.applyCSS`'s context.**~~ Closed by what P2 found, recorded 2026-08-18: there is no route
   to thread, structurally rather than merely today — every hop back stays inside the legacy
   report / `ReportSheet` / `ChartElementDef` model, which never carries a `VSAssembly`, and
   `applyCSS(ReportSheet)` has no callers in `core/src` or the enterprise modules. So P4 passes
   `VizContext.LEGACY` at `CSSProcessor:303` and at its own `ofGate()` site, not a threaded context.
2. ~~**The composer's new-viewsheet funnel.**~~ Closed: stamping in `ViewsheetVSAssemblyInfo`'s constructor
   covers every construction path, so no funnel needs locating.
3. ~~**The three legacy `initDefaultFormat()` call sites.**~~ Closed: only one is a constructor, and the
   per-assembly constructor stamp covers the other creation path without a special case. See §1.
4. ~~**Where the Modernize bar sits in the composer chrome**, and whether an existing banner mechanism already
   exists to host it.~~ Closed 2026-08-18: no existing mechanism does. The composer's only notice surface
   is `<notifications>` (`composer-main.component.html:305`), a five-second toast (`[timeout]="5000"`) with no
   action slot and nothing that survives past its own timer — unusable for an offer the user must be able to
   accept or decline on their own schedule. The bar ships as a new standalone component
   (`modernize-bar.component.ts`), positioned absolutely over the canvas rather than inserted as a row above
   it, because `.vs-pane-container` — the element the ruler origin and the canvas's own scroll geometry are
   both measured from (`viewsheet-pane.component.scss:18-33`) — already fills its parent at `position:
   absolute` with `width`/`height: 100%`. A sibling row above it would shrink the canvas and move the ruler
   origin; an absolutely positioned overlay disturbs neither.
5. ~~**The `VizContext` name.**~~ Closed: it shipped as `VizContext` in P2 (`119bfdaac`), carrying
   `modern`/`dark`/`density`. No better name appeared while the plan was written.
6. ~~**The hook's name.**~~ Raised and answered 2026-08-18: **`seedChromeDefaults(VizContext ctx)`**.
   `applyModernDefaults` was unavailable (§4's first correction), and `seedModernDefaults` was rejected as
   slightly wrong about gate-off creation, where the hook seeds the legacy branch of each ternary.
7. ~~**Whether the hook writes anything at TITLEPATH.**~~ Raised and answered 2026-08-18: **yes — the title
   border colour from `bcolors` (`VSAssemblyInfo:1210`), by mutating the installed composite in place.** The
   alternative, keeping the hook to OBJECTPATH plus the `PlotDescriptor`, would have left a Modernized
   assembly's title border legacy while a fresh one took the modern colour, and would have needed an explicit
   exemption in P3's verification claim. The accepted cost is that the hook touches a second path, so the
   per-path contract has to hold there too.
8. ~~**Whether a sheet reverted in a composer session suppresses the Modernize offer for the rest of it.**~~
   Answered 2026-08-20 during the P6 review: **yes**, by setting the existing per-session
   `modernizeBarDismissed` flag rather than adding a second suppression rule. The menu entry is unaffected and
   keeps recomputing. See §5's P6.

---

## What this unblocks

**Corrected 2026-08-18, now that P4 is built: L′ does not wait for P5.** This section previously read "when
P5 lands: L′…", written before either phase existed in code. `VSDensityDefaults.titleHeight()` is read by
the Java painters, not by the browser's per-assembly CSS scope, so the read path L′ needs is a server read —
P4's subject. **L′**, the title lane height row, needs both `userTitleHeight` (shipped in
`1d26dbefb`/`d4d0d5d48` — *did an author choose this height*) and a read path that consults the mark at the
title-height resolver (*is this assembly modern*) — the second half is what P4 supplies, so L′ is startable
now rather than at P5.

When P5 lands: **L″** behind L′, §07 derived selection and the retirement of the teal family, the range
slider's painter half, and the outlined text conversion. ~~The card radius 12px → 6px additionally needs the
sweep, because retiring `resolveSeededCorner` without it leaves no reversal path.~~ **Amended 2026-08-19:**
it needs **P6**, not the sweep. The requirement was never the sweep specifically — it was *a* reversal path
for `resolveSeededCorner`'s retirement to fall back on, and Revert is one. So the radius unblocks a phase
earlier and behind something an order of magnitude cheaper.

~~What it does **not** clear is the release gate. That needs the sweep, the bookmark path and the deletion of
the four old mechanisms.~~ **Rewritten 2026-08-19.** With P6 in scope, this document now clears most of the
release gate. What remains outside it: bookmark resolve-on-restore (decision 10), and the two of decision
12's mechanisms P6 does not take (`resolveSeededCorner` and its `VSCompositeFormat` tab carve-out). The
sweep, its restore point, its scheduler blocking and its composer-session blocking are gone from the gate
entirely rather than moved within it.

---

## Related

- [seeded-value-reversibility-decisions.md](./lookfeel/seeded-value-reversibility-decisions.md) — the twelve
  product decisions this implements, and the addendum on what `userTitleHeight` changed
- [chart-card-roadmap.md](./lookfeel/chart-card-roadmap.md) — where M sits in the dependency picture
- [chart-card-anchored-strip-lane-decisions.md](./lookfeel/chart-card-anchored-strip-lane-decisions.md) —
  decision 8 schedules L′ after the mark; decision 7 is the `TitleInfo.equals()` precedent
- `chart-card-design3/Seeded value reversibility - ticket.md` — the external ticket. Its central worry,
  version-blindness, needs no schema version (reversal is not subtractive), and its granularity section is
  moot here: `VSCompositeFormat` is untouched
- `chart-card-design3/Visualization Widget Spec.dc.html` §03 — the mark's original specification, superseded
  by the decisions file
