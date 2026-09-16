# Chart companion colours — design

**Date:** 2026-09-14
**Status:** implemented
**Branch:** `epic-74519` (base)
**Source:** `design_handoff_chart_palettes/` — README, CSS.md, **ENGINE.md §0 and §1**, and
`SBI Color and Type Pairings.dc.html` §3b / §3i / §3j. Recovered 2026-09-14 from
`chart palette redesign.zip`; see Provenance below.

## What this is

The second slice of the chart palette redesign, after
[the re-tune](./2026-09-11-chart-palette-retune-design.md). It adds the **companion colour** — a
soft partner for each palette slot, hue held constant — and spends it on its first consumer, the
**target band**.

A companion is not a palette and not a second series. It is the colour source for three places
where the engine currently fakes a second tone: the target band (this slice), brushing (§2,
deferred) and the area fill (§5, deferred). Each of those fakes it differently — an unrelated
green, a global grey, an opacity — and all three want the same thing.

Scope: one new utility class, two methods on `CategoricalColorFrame`, one policy method, two
authored CSS palettes, and one seeding call in the Chart Properties dialog service. No new
colour-frame classes, nothing in the picker's selectable list, and **no change to the render path
at all** — see decision 4.

## Provenance — the handoff was recovered, not re-derived

The re-tune design cited `design_handoff_chart_palettes/` as its source, but the folder was never
committed: `git log --all --diff-filter=A -- "**/ENGINE.md"` is empty in both repos, it is not
`.gitignore`d, and it is absent from `chart-card-design3/`. Only its *conclusions* survived, inside
the re-tune design's prose.

It has been recovered in full from `chart palette redesign.zip` and re-read for this slice. Two
things follow. First, the re-tune design's deferred-table entry for this work — *"Needs the
sRGB-OKLab utility plus `Modern-soft` and `Modern Dark-soft` CSS"* — was an accurate but very
lossy summary: ENGINE.md has **seven** sections, and §0's companion is a mechanism with a
four-step resolution order, not a pair of CSS blocks. Second, the corrections below are recorded
here because the source is still not in the repo and may be lost again.

## Corrections to the source

Recorded so a later slice does not re-derive them. The re-tune design found four handoff claims
false against this branch; these are four more.

**1. `TargetForm` is not handed an empty frame.** ENGINE §1, the design doc §3b and the target-band
drawing's own caption all say `setBandColorFrame()` "is handed an empty one". It is handed
`(CategoricalColorFrame) bandFill.getVisualFrame()` (`GraphTarget.java:588`), and `bandFill` is
initialised with four authored pale greens — `#e4f2e9`, `#d8e5dd`, `#ccd9d0`, `#c0ccc4`
(`GraphTarget.java:794-803`). This is the correction with the largest consequence: §1 is not
"wiring only, ~2 lines". It **replaces an existing visible default**.

**2. There is no alpha softening to drop.** ENGINE §1 says to "drop the alpha". `alphaValue`
defaults to `100` (`GraphTarget.java:786`) and `FormVO.java:128` computes
`alpha = form.getAlpha() / 100.0` → `1.0`. Target bands are already fully opaque. The alpha is an
author control that happens to sit at its neutral value, not a softening mechanism.

**3. The palette names moved.** The handoff writes `Default` / `Default-dark` for the eight bases;
on this branch those are `Modern` / `Modern Dark`, and `Default` is the classic 40. The companions
are therefore **`Modern-soft` and `Modern Dark-soft`**, matching the re-tune design's own naming.

**4. README's migration and alias section is moot**, already recorded in the re-tune design's
Corrections: palette names are not stored in saved viewsheet XML.

## Decisions

**1. Companions are derived by rule; the authored palette is an override.** The design doc §3j
argues this and it is adopted: authoring by hand *"solves it for our three sets and leaves every
deployment palette without companions — which would make the companion a feature only we can
use."* A deployment's own `Northwind`, user-picked series colours and the classic 40 all get
companions by rule. The sixteen authored hexes ship as the built-in override so our own sets are
exact and independent of floating-point drift.

**2. The band takes the companion of its measure's series colour. Where the measure has no series
colour, today's greens stay.** The common case — colour bound to a dimension, so the bars are many
colours and one target spans all of them — has no single measure colour to companion, and
borrowing the first category's colour would assert a relationship the data does not have. Those
charts keep the existing pale greens. The band gets a companion where the measure genuinely
resolves to a slot: no colour binding at all (bars are slot 1, which is the case the drawing
shows), or colour bound by measure.

**3. Multi-band targets step the companion in OKLab lightness.** A std-deviation target with n
factors draws n−1 stacked bands, which today walk the four greens. All bands take the measure's
companion hue and walk **L** from the companion toward the base, never reaching it — so depth
reads as depth and the band never collides with the series colour. The alternative, one companion
separated by opacity, is the *"opacity standing in for colour"* pattern the design objects to in
three other places.

**4. The companion is a seeded default, not a render-time substitution.** Two earlier readings of
this slice are superseded and the reversals are recorded rather than dropped. The first proposed
seeding at creation and was abandoned on the grounds that the measure's colour is not knowable
then; the second substituted at render. **Both were wrong.** The Edit Target dialog exposes the band
fill as an editable swatch showing the stored value, so substituting a different colour at render
makes that swatch lie about what the chart draws — the same design-time WYSIWYG break the roadmap
records against `VSTitleChromeDefaults`. And the measure's colour *is* knowable at dialog time: it
lives on the `ChartAggregateRef` (`VSChartAggregateRef:294`), not only in the runtime frame the
visitor builds. So the companion is seeded once, onto the dialog's new-target template, and is
thereafter an ordinary stored value the author owns.

**5. No provenance mechanism is required.** Decision 4 removes the question a flag existed to
answer. A target created after this slice is born holding the companion; an author who changes it
simply stores a different colour, exactly as before. The `bandFillUserSet` field, its XML
attribute, the `ChartProcessor` script-path guard and the `ChartPropertyService` change-detection
are all deleted.

## 1. The OKLab utility

New: `core/src/main/java/inetsoft/graph/internal/OKLab.java`. Pure math, no dependency, ~80 lines,
package-siblings with `GTool`.

```java
public static double[] fromColor(Color c);                     // {L, a, b}
public static Color toColor(double l, double a, double b);     // raw conversion
public static double[] toLCH(double[] lab);                    // {L, C, H}, H in degrees [0,360)
public static double[] fromLCH(double l, double c, double h);
public static Color toColorInGamut(double l, double c, double h);  // chroma-reduced to fit sRGB
```

`toColorInGamut` is the only entry point a caller should use for a *computed* colour — it is where
the chroma-reduction rule below lives.

Nothing like it exists: `grep -rn "oklab\|labToRgb\|relativeLuminance" core/src/main/java/inetsoft/`
is empty, and the graph package's only colour model is `Color.RGBtoHSB` (`GShape.java:1075`,
`HueImageFilter.java:43,96`).

**Why neither existing model can do this job.** `ColorFrame.process` (`ColorFrame.java:101-103`)
dims by multiplying each sRGB channel — a 0.8 multiplier barely touches Amber `#FFB020` and
destroys Ink `#241C4F`, because sRGB channel values are not proportional to perceived lightness.
HSB is worse for a rule that must hold across eight hues: its `B` is `max(r,g,b)`, so pure yellow
and pure blue both sit at 1.0. Only a perceptually uniform space makes `L + 0.14` mean the same
amount of "lighter" for every base.

**Two measures are in play and this slice picks one.** The re-tune design tuned `Contrast` on
**WCAG relative luminance** (quoted to four decimals: Teal 0.2271, Green 0.3076) while
[the dark selection-border ticket](./2026-09-11-dark-assembly-selection-border-ticket.md) reasons in
**OKLCH L**. They are different numbers and do not rank colours identically. Companion derivation is
stated in **OKLab L**, because that is what the derivation rule's constants are expressed in.
`Contrast`'s ladder is not restated and not re-tuned here.

## 2. `getCompanionColor` — mechanism and policy

Split across the two classes that already own these concerns, rather than one accessor doing both:

**Mechanism, on `CategoricalColorFrame`:**

```java
public Color getCompanionColor(int index, boolean dark)
```

Derives from the frame's own base at `index` by the rule below. Pure, needs no palette name, and
works for a frame that has no registered name at all — user-picked series colours, a deployment's
own palette, the classic 40.

**Policy, on `VSChartPaletteDefaults`:**

```java
public static Color companionColor(CategoricalColorFrame base, String paletteName, int index,
                                   boolean dark)
```

Authored-first, derived-second. It resolves one slot rather than a whole palette: the caller
already holds the frame and the index, and a per-slot answer is what both the drift guard and the
target-band wiring want. The authored lookup is memoized per org on the CSS timestamp, sharing
`resolve()`'s memo, because `ColorPalettes.getPalette` locks on its own class and can NPE on a
malformed `format.css` — neither may reach the render path.

That class already owns modern palette policy, already imports `ColorPalettes`, and already holds
`hiddenPaletteNames(VizContext)`.

**Storage access, on `ColorPalettes`:** `getCompanionPalette(String name)` returns the
`<name>-soft` frame.

### The derivation rule

Hue is never touched, so a companion always reads as the same series.

| | Non-anchor slots | Anchor |
|---|---|---|
| Light | L + 0.14, C × 0.40 | L → 0.855, C × 0.45 |
| Dark | L − 0.26, C × 1.03 | L → 0.64, C × 0.55 |

Two constants that look like mistakes and are not, both argued in the design doc §3j:

- **Dark holds chroma rather than cutting it.** On a dark surface, low chroma and low lightness
  disappear together.
- **The anchor lifts in dark mode.** Ink is already the darkest member and has nowhere to deepen
  to; a uniform offset produces a second dark colour and inverts the emphasis of every chart built
  on slot 3.

The anchor test is on the **base's** L, not the companion's.

**The anchor threshold is mode-specific: `L < 0.40` in light, `L < 0.50` in dark.** The handoff
states `L < 0.40` flat, and that is wrong for the dark set — dark Ink `#49447D` measures **L =
0.420**, so a flat 0.40 classifies it as an ordinary slot, subtracts 0.26 and yields `#0E0033`
against an authored `#8988AB`. Both thresholds separate cleanly with margin: the light bases are
0.269 then 0.550 upward, the dark bases 0.420 then 0.611 upward. A per-frame L test is preferred to
keying on slot index 3, because index 3 means nothing in a deployment's own palette.

**Out-of-gamut targets are resolved by reducing chroma, holding L and H — never by clamping
channels.** This is load-bearing, not a detail: naive per-channel clamping shifts the hue, which
breaks the rule's one promise. Verified across both sets — with chroma reduction the dark
companions reproduce the authored hexes **8 of 8 exactly**; with channel clamping only 2 of 8.

**Light slot 6 is the one authored value the rule does not reproduce, and it is a deliberate
hand-tune.** Amber's target (L 0.9531, C 0.0660, H 75.04) is outside sRGB. Chroma reduction alone
gives `#FFEDD4` (L 0.9540); the authored `#FFE7C7` sits at L 0.9393, so the designer lowered
lightness as well. Every other light slot reproduces exactly. This is precisely the case the
authored-override layer exists for, and §7's drift guard must exempt it rather than assert it.

### Resolution order

1. **Authored.** `<name>-soft` exists and has a non-null colour at this index → use it.
2. **Derived.** No authored entry → apply the rule to the base.
3. *(Base from the overflow generator → derive from the generated colour. Not reachable: ENGINE §3
   is deferred, so an index past the palette still wraps and the base still resolves.)*
4. *(Non-categorical frame → today's global grey. That is §2's fallback, not this slice's.)*

**A null must fall through to derived, not render as absent.** The loader sets palette length to
the highest declared index (`ColorPalettes.loadPalettes()`), so overriding slot 3 alone yields a
three-long palette with nulls at 1 and 2. Treating those nulls as authored would silently break two
other series. This is the one trap in the override path.

## 3. Palette data

`core/src/main/resources/inetsoft/util/css/defaults.css`, **1-based** indexes — the handoff's
block is 0-based and must be shifted, exactly as in the re-tune.

`Modern-soft`, companions of the eight `Modern` bases:

| 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 |
|---|---|---|---|---|---|---|---|
| `#97BEEB` | `#F6B2A2` | `#CCCCE9` | `#BFF6E5` | `#AD8BCB` | `#FFE7C7` | `#DE93AB` | `#D8F7BA` |

`Modern Dark-soft`, companions of the eight `Modern Dark` bases:

| 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 |
|---|---|---|---|---|---|---|---|
| `#00569C` | `#A62E11` | `#8988AB` | `#009378` | `#550080` | `#AB792A` | `#870047` | `#5F9100` |

Slot 3 is the anchor in both, and inverts: light `#CCCCE9` is a lift to the canvas end, dark
`#8988AB` is also a lift. Every other slot recedes toward the canvas in light and deepens in dark.

`Contrast` gets no companions in this slice — it has no dark variant by design, and nothing in
this slice consumes one.

**These are the derivation rule's own output.** Authoring them makes the built-in sets exact and
independent of floating-point drift; they are an override of the mechanism, never a substitute for
it.

## 4. Where the companion is seeded

One site: `ChartPropertyDialogService.java:257`, where the Edit Target dialog's **new-target
template** is built. Before that `GraphTarget` is handed to `getTargetInfo`, its band fill is seeded
with the companion ladder by `ChartPropertyService.seedCompanionBandFill`.

The colour is resolved entirely from state the dialog already holds:

`GraphUtil.getMeasuresName(info, rt)` → first measure → `info.getFieldByName(name, rt)`
(`AbstractChartInfo:3121`) → `ChartAggregateRef.getColorFrame()` (`VSChartAggregateRef:294`) →
`StaticColorFrame.getColor()`, which resolves `userColor ?? cssColor ?? defaultColor`.

**That is the same value the renderer uses.** `VSColorFrameStrategy.getFieldFrame(ref)` returns
`ref.getColorFrame()` and feeds it into the runtime `MultiMeasureColorFrame`. Seeding from it makes
the dialog and the canvas agree *by construction* rather than by two code paths staying in sync —
which is the whole point of decision 4.

The companion itself resolves through `VSChartPaletteDefaults.companionColor`, never
`getCompanionColor` directly, so an authored `Modern-soft` entry still wins over the rule. The
ladder is `CategoricalColorFrame.companionBands(base, companion, 4)`, written onto band slots 0-3 —
mirroring the four greens `GraphTarget`'s initialiser already seeds, so nothing downstream changes
shape.

**Four guard rails, each preserving today's greens:**

| Condition | Why it keeps the classic fill |
|---|---|
| `!ctx.modern` | a classic chart is not in scope for this look |
| no measures on the chart | nothing to companion |
| first field is not a `ChartAggregateRef` | **colour bound to a dimension** — one band spans many series colours, so no single measure colour exists |
| `companionColor` returns null | no companion is derivable; fall back rather than invent one |

**Only the template is seeded.** `getTargetInfoList` → `getTargetInfo` serves *existing* targets;
seeding there would overwrite an author's stored colour every time the dialog opened. Nor is the
target reseeded in `updateAllTargets`: whatever the dialog displayed is what the author saw, and it
is stored verbatim.

**One accepted consequence.** The seeded colour does not follow the Field dropdown. Choose a
different measure and the swatch keeps the first measure's companion until changed by hand. That
matches how the setting behaves today — the greens are fixed regardless of field — and it is the
price of the dialog never lying. Making it track the field would need the client to re-request on
every field change.

## 5. What this removes

The render path is untouched by this slice. Everything below existed only to make a render-time
substitution safe, and all of it is deleted:

- `GraphGenerator`: `targetSeriesColor`, `resolveCompanion`, and the substitution gate in
  `addTarget`.
- `TargetForm`: `companionBase`, `companionFill`, their setters, and the companion branch in the
  band loop — restored to reading `bandColors.getColor(i - 1)`.
- `GraphTarget`: `bandFillUserSet`, its accessors, and its `writeAttributes` / `parseAttributes`
  handling. Removing the attribute is safe for already-saved sheets: an unknown attribute is simply
  not read.
- `ChartProcessor.addTarget`: the `hasBandColor` tracking and flag set.
- `ChartPropertyService`: the change-detection in `updateBandTarget` / `updateStatTarget`.

`Modern-soft` / `Modern Dark-soft`, `OKLab`, `getCompanionColor`, `companionBands` and
`companionColor` are all retained unchanged — that is the colour engine, and it was never the part
that was wrong.


## 6. Picker filtering

`ColorPalettes.getPaletteNames()` returns every name in the CSS, so `Modern-soft` and
`Modern Dark-soft` would appear in Select Palette as choosable palettes.

**Filter inside `getPaletteNames()` itself**, and expose companions only through
`getCompanionPalette(name)` — the design doc §3j's recommendation, so the reserved suffix is
enforced in one place rather than assumed by every caller. There is exactly one consumer to verify,
`VSChartBindingController.java:250`.

`getPalette(name)` resolution stays **unfiltered**, for the same reason the re-tune gave: filtering
resolution would break rendering.

**Accepted limitation:** `-soft` becomes a reserved suffix, so a deployment palette genuinely named
`Sage-soft` would vanish from that deployment's own picker. The design doc names this and prefers
it to the alternative — teaching `CSSDictionary` a third attribute
(`ChartPalette[name][index][variant=soft]`), which is the honest model but touches the parser.
Centralising the suffix check is what keeps that future migration to one method.

## 7. Testing

**Java**

- `OKLabTest` — round-trip every sRGB value within tolerance; a handful of published reference
  vectors; gamut clamping on out-of-range input.
- `CategoricalColorFrameCompanionTest` — the four rule branches (light/dark × anchor/non-anchor),
  hue preserved to within tolerance in every case, and the anchor test keyed on the base's L.
- `VSChartPaletteDefaultsTest`, extending the drift guard the re-tune added: assert `Modern-soft`
  and `Modern Dark-soft` declare 8 non-null colours, and that each **equals the rule's output for
  the corresponding base** — this is what keeps the authored override and the mechanism from
  silently diverging. **All 8 dark slots and 7 of 8 light slots hold exactly; light slot 6 (Amber)
  is exempted with its reason named in the test**, per §2. An exemption list of one is the point:
  if a second slot ever needs exempting, the rule or the authored set has drifted.
- Null-hole case: a partial `<name>-soft` override falls through to derived at the undeclared
  indexes rather than rendering absent.
- `getPaletteNames()` omits both `-soft` names; `getPalette("Modern-soft")` still resolves.
- `ChartPropertyServiceCompanionSeedTest`: a modern chart seeds slot 0 with the measure's authored
  companion rather than `#e4f2e9`; all four band slots are seeded and consecutive slots differ; a
  **classic** chart keeps the greens; a chart whose first field is not a `ChartAggregateRef` keeps
  the greens (the dimension-bound case); and an **existing** target is never reseeded — the
  regression guard for "an author's stored band fill is theirs".

**Frontend**

Nothing in this slice changes a component. The picker specs added by the re-tune
(`palette-dialog.spec.ts`) should be checked to confirm neither `-soft` name appears in
`paletteSelectOptions`.

## 8. Deferred, with triggers

| Item | Trigger |
|---|---|
| **ENGINE §2 brushing** | Independent, and the most visible remaining consumer. `getDimColor()`/`getHighlightColor()` are static and context-free (`BrushingColor.java:36,50`), captured once per chart into `GraphGenerator:7601-7602` and threaded to ~20 live sites across `GraphGenerator`, `MapGenerator`, `VGraphPair`, `BrushingComparator` and `ExcelChartHelper`. The work is making the lookup per-mark, not swapping a call. Shipped for cartesian charts; maps deferred — see [the brushing companion-colours design](./2026-09-15-brushing-companion-colors-design.md). |
| **ENGINE §5 area fill** | Independent. `AreaElement` has no fill colour at all — it paints the line's colour at `HINT_ALPHA 0.8` (`AreaElement.java:75`). A new property on the element and on `AreaVO`, so descriptor, persistence and UI come with it. Buys area, filled line and radar at once. |
| **ENGINE §3 overflow generator** | Now cheaper: this slice lands the OKLab utility it shares. Still gated on the slot-9 seam mattering — `Modern` index 8 is `#8ed604`, index 9 `#9368be`, straight into the 2010-era tail via `spliceLegacy()`. Note the design doc §3i answers ENGINE's own open question: declaring `[index=9]` makes the slot real, so generated colours are themeable by declaration and no `updateCSSColors()` bound needs changing. |
| **ENGINE §4 ramps** | Fully independent of companions. Three `AbstractSplineColorFrame` subclasses (`Amber`, `Teal`, `Variance`, 7 stops each, hexes in CSS.md), `LinearColorFrameWrapper`s, and cases in `VisualFrameWrapper`'s class-name switch (`:258`). Must **not** ship as `ChartPalette` CSS rules — that would put them in the categorical picker. |
| **ENGINE §6 multi-level shading** | Blocked upstream, unchanged: nothing passes depth, breadth or sibling index into colour resolution. `getShade(...)` would share this slice's OKLab utility but is not the same function as `getCompanionColor`. |
| `Soft` re-authoring, `Pastel` folded into companions | Both fold into the wider companion rollout. |
| **Observed, not fixed: `CategoricalColorFrameWrapper` round-trips asymmetrically.** `writeContents` emits the default tier into `<colors>` (`:189-200`), and `parseContents` restores it with `setColor(index, …)` → `setUserColor` (`:320`), so a save and reload promotes every default-tier colour into the user tier. §4 works around it rather than fixing it. | A decision to make default-tier seeding survive persistence on any categorical frame. The fix is symmetric parsing, but the class is shared by every categorical frame in every chart, so it needs its own slice and its own regression pass — `VSFrameVisitor:577`'s `clearUserColors()` currently masks it for chart series colours and would interact. |

**The mechanism-with-one-consumer risk is real on this branch.** The roadmap records
`applyDarkForeground` as *"the 9B mechanism that existed for exactly this and was wired to only one
surface, the slider"* — landed with one caller and left, surfacing later as a defect. Companions
have the same shape. The mechanism now has three consumers — the target band, the brushing dim
colour, and the brushing marker — rather than one, enumerated above with their call sites so the next
person finds a list instead of rediscovering it.

## Files touched

| File | Change |
|---|---|
| `core/src/main/java/inetsoft/graph/internal/OKLab.java` | **new** — sRGB↔OKLab/OKLCH conversion, chroma-reduction gamut mapping |
| `core/src/main/java/inetsoft/graph/aesthetic/CategoricalColorFrame.java` | `getCompanionColor(int, boolean)`, `companionBands(Color, Color, int)` |
| `core/src/main/java/inetsoft/uql/viewsheet/internal/VSChartPaletteDefaults.java` | `companionColor(...)`, `getCompanionPaletteSafely`, companion entries in `MEMO` |
| `core/src/main/java/inetsoft/uql/viewsheet/graph/aesthetic/ColorPalettes.java` | `getCompanionPalette(String)`; `getPaletteNames()` filters the reserved suffix |
| `core/src/main/resources/inetsoft/util/css/defaults.css` | new `Modern-soft` and `Modern Dark-soft`, 1-8 each |
| `core/src/main/java/inetsoft/web/viewsheet/service/ChartPropertyService.java` | `seedCompanionBandFill(...)` + its private `resolveCompanion` |
| `core/src/main/java/inetsoft/web/composer/vs/dialog/ChartPropertyDialogService.java` | seeds the dialog's new-target template |
| `core/src/test/java/inetsoft/graph/internal/OKLabTest.java` | **new** |
| `core/src/test/java/inetsoft/graph/aesthetic/CategoricalColorFrameCompanionTest.java` | **new** |
| `core/src/test/java/inetsoft/graph/aesthetic/CompanionBandFrameTest.java` | **new** |
| `core/src/test/java/inetsoft/web/viewsheet/service/ChartPropertyServiceCompanionSeedTest.java` | **new** — the seeding behaviour and its four guard rails |
| `core/src/test/java/inetsoft/uql/viewsheet/internal/VSChartPaletteDefaultsTest.java` | companion resolution + the CSS↔rule drift guard |
| `core/src/test/java/inetsoft/uql/viewsheet/graph/aesthetic/ColorPalettesModernTest.java` | companion declarations, suffix filtering |

Every path is inside the `community` submodule, so this ships as a community PR.

## Verification

- `./mvnw test -pl core` green against the branch baseline.
- `./mvnw clean install -DskipTests -Pcommunity,enterprise` — **use `clean`**; an incremental
  `install` has reported SUCCESS over a real cross-module break on this branch before.
- **Browser, and the checks that prove new behaviour:** a modern chart with no colour binding and a
  single-band target draws a pale-azure band, not green (this is the drawing's case) — **including
  after opening the target dialog and clicking OK without touching the band colour**, which is the
  path that made the feature unreachable; the same chart with colour bound to a dimension still
  draws green; a std-deviation target with three factors draws three distinguishable bands at one
  hue; an author-picked band colour survives a reload and a re-render; a dashboard saved before
  this slice is pixel-unchanged.
- Dark: the same matrix on a `MODERN_DARK` chart, where the companion deepens rather than lifts —
  except slot 3.
- Export parity: one modern chart with a target band to PDF, PNG and Excel.

## What the implementation found

**The anchor threshold is mode-specific.** The design said `L < 0.40`; the dark anchor `#49447D`
measures L = 0.420, so dark needs `L < 0.50`. A flat threshold yields `#0E0033` instead of the
authored `#8988AB`.

**Two findings below are now historical.** They were real defects in the render-time mechanism and
were fixed there; that mechanism has since been withdrawn entirely (see *The rework* at the end), so
the code they describe no longer exists. They are kept because each names a trap a future consumer
of the companion will meet again.

**A third path sets author-chosen band colours** *(historical)*. `ChartProcessor.addTarget` (`:544`)
backs the public chart-script functions `addTargetBand`, `addTargetLine`, `addPercentageTarget`,
`addPercentileTarget`, `addQuantileTarget`, `addStandardDeviationTarget` and
`addConfidenceIntervalTarget`. Any future provenance scheme must cover the scripting surface, not
just the dialog. Note `parseColors(null)` returns `new Color[]{null}` — length 1, not 0 — so a
length check misfires where a non-null check does not.

**The measure's colour must be keyed by field** *(historical)*. Taking `getDefaultColor(0)` from the
colour frame always yields the FIRST measure's colour on a `MultiMeasureColorFrame`. The correct
form is `getMeasures().contains(field) ? getColor(field) : null`, which also returns null for a
dimension-coloured measure under multi-aesthetic binding. Any code resolving "this measure's colour"
needs this shape.

**The dialog round-trips the band colour, so "a colour arrived" is not "the author chose one".**
Found in the final whole-branch review. `getTargetInfo` always emits a non-empty band hex — the
greens live in the user tier and `CategoricalColorFrameWrapper.getColor(int)` never returns null in
range — so every target created or edited in Chart Properties was marked author-set and the
companion never reached the UI at all.

This was patched by comparing the incoming colour against the stored one, and **that patch is what
exposed the deeper problem**: if the dialog must display the band colour for the comparison to mean
anything, then the dialog is the authority on what the band is, and a render-time substitution can
only contradict it. The provenance question was a symptom. Decision 4 removes it.

**Every new test class needs `@Tag("core")`.** `core/pom.xml:996` hardcodes Surefire
`<groups>core</groups>`, so an untagged JUnit 5 class is never selected and reports `Tests run: 0`
with `BUILD SUCCESS` — indistinguishable from passing.

## The rework — why the render-time mechanism was withdrawn

Recorded because the branch shipped, was reviewed eight times plus a whole-branch review, and was
still wrong in a way none of those reviews could see.

The tell came from a screenshot of the Edit Target dialog, not from code. The dialog's **Fill Band**
control is an editable swatch displaying the stored colour. Every review had checked the
implementation against this design; the design itself said "substitute at render", so a swatch
reading `#e4f2e9` beside a canvas drawing azure was never anyone's assertion to fail. The question
that broke it — *why would a user expect a different colour than the one the dialog shows?* — has no
good answer.

What made the withdrawal cheap is that the mistake was confined to the **consumer**. The colour
engine — OKLab, the derivation rule, the authored-override resolver, the CSS palettes, the suffix
reservation — was correct throughout and is retained verbatim. Only the delivery mechanism changed,
and it changed from adding machinery to deleting it: a provenance flag, a persisted XML attribute, a
render-path gate, two `TargetForm` fields and five test classes all became unnecessary the moment
the companion became a seeded default instead of a substitution.

**The generalisable lesson**: a value the user can see and edit in a dialog is not chrome. Chrome
can be resolved at render; a settable value must be *stored* as what it renders, or the authoring
surface lies. The roadmap already recorded this once for `VSTitleChromeDefaults` — "it runs at every
read point, `FormatPainterService` included" — and this slice rediscovered it from the other side.
