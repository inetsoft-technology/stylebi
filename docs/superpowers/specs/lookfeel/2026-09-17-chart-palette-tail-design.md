# Chart palette tail — design

**Date:** 2026-09-17
**Status:** implemented on `feature-chart-palette-tail`; automated suite green and the manual
browser pass verified 2026-09-18 — see "What the implementation found".
**Branch:** `epic-74519` (base). Community-only.
**Source:** ENGINE §3 of the external design set `SBI Color and Type Pairings.dc.html` and its
`design_handoff_chart_palettes/` folder, at
`docs/superpowers/specs/lookfeel/design-handoff-chart-palettes/`. **§3's stated premise does not
hold on this branch** — see "Why the source needed correcting".

## What this is

The fifth slice of the chart palette redesign, after the
[re-tune](./2026-09-11-chart-palette-retune-design.md) (#5188), the
[companions](./2026-09-14-chart-companion-colors-design.md) (#5275), the
[brushing](./2026-09-15-brushing-companion-colors-design.md) (#5311) and the
ramps ([#5345](https://github.com/inetsoft-technology/stylebi/pull/5345), still open — its
`2026-09-16-chart-ramps-design.md` arrives on `epic-74519` with it).

Slots 9–40 of `Modern` and `Modern Dark` are still the 2010-era legacy list. Every slice so far has
re-tuned, companioned or gated the **first eight**; a chart with nine or more categories has been
drawing the rest from a palette that predates the design set entirely. This slice replaces those 32
slots with a tail derived from each palette's own head.

Nothing generates at runtime. The hexes ship as literals in two places — the CSS declaration and
the Java fallback — and a test re-derives them from the head and compares, which is how
[#5345](https://github.com/inetsoft-technology/stylebi/pull/5345) shipped the ramps.

## Why the source needed correcting

**ENGINE §3's premise is dead, and the re-tune already said so.** §3 argues the overflow wrap is
"survivable at 40 slots, not at 8", on the assumption that `Default` was cut to eight. It was not.
The [re-tune design](./2026-09-11-chart-palette-retune-design.md)'s Corrections section recorded
this at the time: *"Keeping `Modern` at 40 means the wrap engages only past 40, exactly as today.
Deferred, not declined."*

Verified again at `HEAD`:

- `defaults.css` declares `Modern` and `Modern Dark` at **40 slots each**.
- `VSChartPaletteDefaults.fromFrame` falls back to `spliceLegacy()` **below** 40, deliberately, so a
  short or holed palette cannot shrink a chart's colour supply.
- `spliceLegacy` keeps the head at 0–7 and takes 8–39 from `CategoricalColorFrame.COLOR_PALETTE`.
- So `colors.get(index % colors.size())` at `CategoricalColorFrame.java:361` engages only past 40.

**So the wrap is not the defect. The seam is.** What
[the §3 trigger record](./engine-3-palette-overflow-trigger.md) measured on a brushed eleven-category
tree is a head-plus-tail palette that was never designed as one set:

- Head colours sit at L 0.269–0.813. Tail entries reach L 0.925, so one chart renders some series at
  full weight and others as pastels before brushing is involved.
- `Modern-soft` and `Modern Dark-soft` are **eight slots**. Everything past slot 8 falls to the
  derivation rule, and seven of the tail's forty entries trip the `LIGHT_MAX_L` branch — so two
  series in one chart recede in opposite directions.
- `#8ED604` (acid, head slot 8) sits beside `#9368be` (violet, legacy) with no relationship.

This slice does not touch `CategoricalColorFrame` at all. Every constraint ENGINE §3 lists as "easy
to miss" — purity across `clone()` and `shareColors`, reading the live pool size through
`updateUnusedColors()`, generated slots being unreachable by `updateCSSColors()`'s `getColorCount()`
bound — exists **only** because §3 proposed generating at runtime. Authoring the tail removes all
three rather than solving them.

## Decisions

1. **Author the tail; do not generate at runtime.** `getColorCount()` stays 40, per-slot CSS
   themeability is automatic, the wrap stays where it is, and the derivation lives in test sources
   the way `ChartRampDerivation` does. Recorded alternative: ENGINE §3 as written.
2. **`Modern` and `Modern Dark` only.** They are the two palettes a modern chart is seeded on and
   the only two where the seam is a shipped default. `Contrast` and `Soft` are eight slots and reach
   a tail only through `PaletteDialog.saveChanges()`, which splices a short palette over the first
   *n* colours and preserves whatever tail the chart already had — a deliberate author action, not a
   default. Deferred with a trigger in §6.
3. **The rule wins over the handoff's three published samples.** §3 publishes slots 9–11 as
   `#009FB6`, `#9E9000` and `#D253C0`. Those are **single-ring** values, and decision 1's departure
   from a single ring moves two of them: the derived tail keeps §3's hue sequence exactly — 212.8°,
   102.6°, 333.5° — and changes only lightness. Slot 10 lands on the middle ring and so keeps its
   published `#9E9000` unchanged; slots 9 and 11 move, by construction rather than by disagreement.

   Reproducing them was still how the port was validated. Run against §3's own single-ring rule,
   `OKLab` here answers `#009FB6` and `#9E9000` **exactly**. The third does not reproduce even then:
   its hue matches to 0.3°, but its chroma is 0.202 where the head's mean is 0.188, so the published
   sample came from something other than the stated rule. Recorded for the designer in §6.
4. **The repaint is the fix, not a side effect.** See §4.

## 1. The derivation rule

A pure function of the eight head colours. Deterministic, no state, no runtime input — though not
invariant across `Math.pow` implementations; see §5.

Convert the head to OKLCH and take its mean lightness and mean chroma. Then, 32 times:

1. **Bisect the widest remaining hue gap** on the hue circle, seeded with the eight head hues. Each
   generated hue joins the circle for the next round.
2. **Choose the lightness ring.** Three rings sit at head-mean L −0.12, +0, +0.12. Try them in
   interleave order — ring `(i + r) % 3` for `r` in 0..2 — and keep the candidate whose nearest
   neighbour among **everything already placed, head included**, is furthest away. Ties break on the
   interleave order.
3. **Chroma is the head's mean C**, brought into sRGB by `OKLab.toColorInGamut`, which reduces
   chroma only and never shifts hue.

Two departures from ENGINE §3 as written, both forced by measurement rather than taste:

**§3 specifies a single ring** at the ring's mean lightness and chroma. At 32 slots that collapses —
min ΔE 0.0160 across the full palette, *worse* than the legacy tail it replaces. Hue alone cannot
carry forty categories, which is why lightness becomes a second axis.

**Ring choice is a repair step, not a rotation.** Plain interleaving (ring `i % 3`) already beats
filling ring-by-ring, but it still lands slot 34 at ΔE 0.0283 from head slot 1 — worse than today's
worst head-to-tail pair. Letting each slot take the ring that separates best fixes that and improves
every other figure at the same time. The step is still pure: it reads only colours already placed in
this same deterministic walk.

### Modern, slots 9–40

```
#00788A #9E9000 #F77FE4 #00A956 #A25100 #00A2A0 #2FC1FF #FF8B93
#405CD4 #836600 #AEBF00 #BF60D1 #B02B80 #BC9FFF #1C8000 #007E57
#FF915F #C87800 #007B70 #00CAD7 #009DC2 #0071AB #BF272B #E65078
#757CFC #86B3FF #E4A700 #5D7500 #D1B000 #706F00 #913DB3 #9F35A1
```

### Modern Dark, slots 9–40

```
#008FA4 #8E8100 #FFA7EF #2FC16A #C06200 #00BBB9 #84D5FF #D2485A
#5575E5 #9FAF00 #F6C200 #D37BE5 #C44B94 #8C63D8 #009668 #369725
#FFB596 #E78A00 #009386 #0087CB #00E4F2 #00B5DF #FD716A #FFB0BE
#ABCBFF #8D98FF #D19800 #D2D226 #6F8C00 #C0A200 #A459C5 #B352B4
```

## 2. Acceptance constraints

Measured against the palette this replaces. ΔE is Euclidean distance in OKLab.

| | today | derived |
|---|---|---|
| **Modern** — light-end rule exceptions at n=16 | 3 | **0** |
| **Modern** — lightness range | 0.269–0.925 | **0.269–0.813** |
| **Modern** — min ΔE at n=12 | 0.1171 | **0.1311** |
| **Modern** — min ΔE at n=16 | 0.0459 | **0.1127** |
| **Modern** — min ΔE, full 40 | 0.0253 | **0.0319** |
| **Modern** — worst head-to-tail pair | 0.0365 | **0.0521** |
| **Modern Dark** — anchor rule exceptions at n=40 | 4 | **1** |
| **Modern Dark** — min ΔE at n=12 | 0.1168 | **0.1491** |
| **Modern Dark** — min ΔE at n=16 | 0.0459 | **0.1079** |
| **Modern Dark** — min ΔE, full 40 | 0.0253 | **0.0361** |

**Zero light-end exceptions is the point of the slice**, and it is what the trigger record asked
for: *"If the generator holds its output inside the head's lightness band, the existing rule covers
it and the light-end case never fires for a generated slot — which is the outcome to aim for, and a
reasonable assertion for the generator's tests."* Modern Dark's one remaining anchor exception is
the anchor itself, `#49447D`, which is correct.

**The n=16 figures are the strongest result** — a 2.5× improvement at the cardinality the trigger
was actually raised at, and the reason this is worth shipping rather than only worth recording.

### What this does not buy, stated plainly

It does not make a twenty-category chart readable. ENGINE §3 says so about its own generator —
*"Generation guarantees the chart is never wrong. It cannot make a nine-hue chart readable"* — and
the numbers agree: min ΔE at forty slots is 0.0319 against today's 0.0253, better but nowhere near
separable. The Composer nudge toward top-*n*-plus-Other that §3 pairs itself with is still the
answer to that problem, and is still unbuilt.

**One axis where dark is marginally behind.** Modern Dark's worst head-to-tail pair is 0.0405
against today's 0.0461 — `#FF8367` (slot 2) against `#FD716A` (slot 31). It is reached only at
thirty-one categories, where nothing is readable on either palette, and every other dark figure
improves. Recorded rather than traded against the n=12 and n=16 gains.

## 3. Component inventory

| File | Change |
|---|---|
| `core/src/main/resources/inetsoft/util/css/defaults.css` | `Modern` 9–40, `Modern Dark` 9–40 — 64 declarations, 1-based |
| `core/src/main/java/inetsoft/uql/viewsheet/internal/VSChartPaletteDefaults.java` | `spliceLegacy(Color[])` replaced by literal `MODERN_TAIL` / `DARK_TAIL`; `fromFrame`'s fallback re-points at them |
| `core/src/test/java/inetsoft/uql/viewsheet/internal/ChartTailDerivation.java` | **new**, test sources — the rule, so nothing derives at runtime |
| `core/src/test/java/inetsoft/uql/viewsheet/internal/ChartTailDerivationTest.java` | **new** — drift guard and the acceptance constraints in §2 |
| `core/src/test/java/inetsoft/uql/viewsheet/internal/VSChartPaletteDefaultsTest.java` | CSS↔Java drift guard extends from 8 slots to 40 |

`MODERN_HEAD` and `DARK_HEAD` are **unchanged**. `CategoricalColorFrame` is **untouched**.

**Both sides must move together**, for the reason the re-tune recorded: the Java constants are the
fallback when a CSS rule is missing or malformed, so a stale constant makes a broken `format.css`
render half the old palette.

**`spliceLegacy` has one other caller shape to preserve.** `fromFrame` calls it on three paths — a
null frame, a frame under 40 colours, and a frame with an undeclared hole. All three mean "the CSS
did not answer", and all three should now get the derived tail rather than the legacy one. The
legacy 40 survives untouched as `CategoricalColorFrame.COLOR_PALETTE`, which is what a **classic**
chart takes through `legacyPalette()`.

## 4. What changes on screen, and for whom

`VSChartPaletteDefaults.applyModernPalette` is `ctx.modern` → `setDefaultColors(activePalette(ctx))`,
called from `VGraphPair:1300`, `ChangeChartProcessor:1893` and `CSSProcessor:474`. A modern chart
therefore re-resolves **all forty slots from live CSS on every render**.

So **every modern-marked chart with nine or more categories changes colour on upgrade** — no
migration, no stored value to fight, nothing an author has to do. That is the same mechanism that
carried the head re-tune in #5188, working on the other 32 slots.

- **Classic charts are untouched.** `seedPalette` sends a non-modern context to `legacyPalette()`,
  which is `CategoricalColorFrame.COLOR_PALETTE`, unchanged by this slice.
- **A chart on `Contrast` or `Soft` is untouched**, per decision 2.
- **A chart whose tail colours were pinned into the user tier keeps them.** The user tier wins over
  defaults unconditionally. This is the same opening the re-tune recorded for the head and checked
  manually on 2026-09-11; the check is worth repeating for the tail.

It belongs in release notes. It is the defect being fixed rather than a regression, but a dashboard
with twelve series will look different the morning after an upgrade.

## 5. Testing

**The drift guard is the centre of it**, mirroring `ChartRampDerivationTest`: re-derive all 32 slots
from `MODERN_HEAD` / `DARK_HEAD` and compare against the shipped literals, in both `defaults.css`
and `VSChartPaletteDefaults`. If the head is ever re-tuned again, the tail fails loudly instead of
silently belonging to the previous head.

**The guard holds on the build toolchain, not on the rule in the abstract.** It is verified against
HotSpot's `Math.pow` on x86-64, on Temurin 17 and 21. `widestGapMidpoint` contains exact ties that a
1-ulp difference in `Math.pow` resolves the other way, so a JVM without the `_dpow` intrinsic
derives a different tail from the same rule. That is a toolchain assumption recorded in
`ChartTailDerivation`'s javadoc, not a hidden defect: a toolchain change would make
`shippedTailsMatchTheRule` fail loudly with what looks like corrupted constants, not silently ship
a wrong tail.

The §2 table becomes assertions rather than prose:

- Zero `LIGHT_MAX_L` exceptions across `Modern`'s forty slots, and exactly one anchor exception
  across `Modern Dark`'s — the anchor.
- Every slot's lightness inside the head's band.
- Min ΔE floors at n=12, n=16 and n=40, and the worst head-to-tail pair, each asserted against the
  measured figure rather than a round number, so a change to the rule has to restate its cost.
- `fromFrame`'s three fallback paths return the derived tail, not the legacy one.

**Not unit-testable, and therefore the manual pass:** that a twelve-series chart reads as one set.
Light and dark, a bar and a tree, brushed and unbrushed — the tree because that is what raised the
trigger.

## 6. Deferred, with triggers

| Item | Trigger |
|---|---|
| **`Contrast` and `Soft` tails** | Both are eight slots and reach a tail only through `PaletteDialog.saveChanges()`, which preserves whatever the chart already had. An author who picks `Contrast` and then binds twelve categories gets an unrelated tail today and still will. Trigger: that is reported, or a third 8-slot palette ships — at which point "a palette is its head plus a derived tail" should become the rule rather than a special case for two names. |
| **The handoff's published slot 11** | Run against §3's own single-ring rule, slots 9 and 10 reproduce exactly and `#D253C0` does not: hue matches to 0.3°, chroma is 0.202 against the head's mean 0.188. So the published sample came from somewhere other than the stated rule. Separate from the three-ring departure, which moves all three deliberately. Trigger: the next sync of the external design set, alongside the ramps slice's own ratification item. Both must be recorded in a sibling file, never inside `chart-card-design3/`, which is regenerated wholesale. |
| **Designer ratification of the three-ring rule** | §3 specifies one ring; this ships three, because one collapses at 32 slots (§1). The hue sequence is §3's untouched, but every published lightness moves. Trigger: the same external sync. |
| **Authored companions past slot 8** | `Modern-soft` and `Modern Dark-soft` stay at eight. Generated slots fall to the derivation rule, which is now exact for them by construction — that is the point of holding the tail inside the head's band. Trigger: a designer authors companions for the tail, at which case `getCompanionColor`'s authored-palette branch already prefers them with no code change. |
| **The Composer top-*n*-plus-Other nudge** | ENGINE §3 pairs the generator with it and this slice does not build it. Nothing here makes a twenty-category chart readable. Trigger: unchanged from §3 — an author-facing warning must read the **live** pool size, because `updateUnusedColors()` removes pinned colours and generation can begin at category 6 if three are pinned. |
| **`CategoricalColorFrame.getColor`'s wrap past 40** | `colors.get(index % colors.size())` still hands two categories one colour past forty. Untouched and unreachable below forty-one categories. Trigger: a chart past forty categories is reported, which no evidence on this branch shows. |
| **The negative-colour wrap** | `negcolors.get(index % negcolors.size())` at `CategoricalColorFrame.java:357` is the same shape on a separate list that no slice has touched. Observed, not changed. |

## Files touched

Five files, listed in §3. Every path is inside the `community` submodule, so this ships as a
community PR against `epic-74519`.

## What the implementation found

**Automated verification.** `./mvnw test -pl core` reports `Tests run: 5867, Failures: 0,
Errors: 22, Skipped: 69` — BUILD FAILURE. All three of this slice's own test classes pass in
full and are not among the 22 errors: `ChartTailDerivationTest` (10/10), `VSChartPaletteDefaultsTest`
(29/29), `ColorPalettesModernTest` (11/11). Every one of the 22 errors is a `NoSuchMethodError` in
five classes — `SeededLinearFrameTest`, `ChartVSAssemblyInfoSeedTest`,
`WizardSeededLinearFrameTest`, `ChartRampDerivationTest`, `HouseRampTest` — that do not exist
anywhere in this branch's source tree; they appear only inside
`.superpowers/sdd/2026-09-16-chart-ramps/`'s review diffs, the still-open ramps PR #5345 this
design's "What this is" section names. They are orphaned `.class` files left in
`core/target/test-classes` from an earlier local build of that unrelated branch, picked up by
Surefire because `./mvnw test` without `clean` does not remove stale compiled test classes. A
`./mvnw clean install -pl core -am -DskipTests` (Step 2) clears `target/` and reports
BUILD SUCCESS across all six reactor modules touched (`StyleBI`, `build-tools-parent`,
`Antlr 2 Maven Plugin`, `tern-annotations`, `cluster-proxy-annotations`, `inetsoft-core`) —
confirming the `fromFrame` signature change and the `spliceLegacy` removal left no other module
stale. A confirmatory `./mvnw test -pl core` re-run after that clean reports
`Tests run: 5833, Failures: 0, Errors: 0, Skipped: 69` — BUILD SUCCESS, and none of the five
orphaned classes are even discovered. The 34-test gap (5867 − 5833) is exactly the five orphaned
classes' own test counts (6 + 6 + 14 + 5 + 3), confirmed by diffing the two runs' class lists.
Nothing in this slice's own code or tests is implicated.

Those are the figures as this section was first written. The review's fix wave then deleted two
tests and added one, so the branch settles at **5832 tests, 0 failures, 0 errors, 69 skipped**,
which is the figure to quote.

**Every new test class needs `@Tag("core")`, or it silently doesn't run.**
`core/pom.xml:996` hardcodes Surefire's `<groups>core</groups>`, so a JUnit 5 class with no
`@Tag("core")` is excluded from the suite entirely and reports `Tests run: 0` — a false pass, not
a green run. The plan's Task 1 code block, copied verbatim from the brief, carried no such tag;
running it as written produced exactly that false "Tests run: 0" before the tag was added.

**The plan told Task 2 to expect `VSChartPaletteDefaultsTest` to pass, which was impossible.**
`modernPalette()`/`darkPalette()` resolve through `resolve()`, which reads a CSS-declared frame
before ever consulting the Java fallback — and CSS did not move until Task 3. Between Task 2's
commit and Task 3's, `gateOnSwapsToModernHeadAndDerivedTail` and
`darkPaletteSwapsToDarkHeadAndDerivedTail` were necessarily red: both call `modernPalette()`/
`darkPalette()`, both got the still-legacy CSS value back, and no code change inside Task 2's
scope could have made them pass. The plan was amended in place to accept this as an expected
intermediate state, the same way `ColorPalettesModernTest.tailMatchesLegacyPalette` already was.

**The plan's "known-breaking tests" table listed seven tests; the real number was nine.**
`modernPaletteResolvesFromCss` and `darkPaletteResolvesFromCss` — both pre-existing, from commit
`ca3c9d647`, unrelated to this slice's own file list — hardcoded `COLOR_PALETTE[8]`/`COLOR_PALETTE[39]`
as a stand-in for "whatever CSS currently declares." That stand-in was only ever true because CSS
happened to still carry the legacy tail; once Task 3 wrote the derived tail into `defaults.css`,
both tests broke as a direct, foreseeable consequence of the change the table was supposed to be
enumerating. Neither test installs a CSS override or otherwise distinguishes CSS resolution from
the Java fallback, so re-pointing their hardcoded expectations to the derived colours cost no
assertion power.

**The manual browser pass ran on 2026-09-18 and passed.** All five cases from the brief's Step 3
were confirmed against rendered charts:

1. A twelve-series bar chart in light mode, on a modern-marked dashboard.
2. The same chart in dark mode.
3. An eleven-category tree chart, brushed — the exact case that raised the original trigger.
4. A classic (unmarked) chart with twelve series, confirming pixel-identical output to before.
5. A chart on the `Contrast` palette with nine categories, confirming the deferred tail is
   unchanged.

The automated suite proves the derivation rule, the constants and the CSS declarations agree with
each other and with the acceptance constraints in §2; it could not prove those five charts read as
one set on screen, which is what this pass adds.

**A defect found while verifying case 3 is fixed separately, and is not this slice's.** Modernize
and Revert did not repaint a tree chart's node colours when the node shelf carried a *category*:
`RelationVSChartInfo` keeps a `VSDimensionRef`-backed `nodeColorField` out of `getAestheticRefs()`
so its dimension stays out of the GROUP BY (bug #75253), and `ChartVSAssemblyInfo.seedColorPalette`
walked only that accessor. It predates this slice — the same hook has missed that field since the
seed mark shipped — and it is fixed on `bug-tree-node-color-seed`, not here.
