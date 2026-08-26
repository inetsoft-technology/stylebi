# Chart Card — §10.2 Design (The seamless in-lane strip and the derived glyph tone)

**Date:** 2026-08-26
**Source doc:** `chart-card-design3/Chart Card Spec v3.dc.html` §10.2, deferred to from §02
**Predecessor:** L″, the geometric suppression, `408ea004c` — it is what makes the title-hidden
overlay case disappear, and therefore what removes §10.2's scrim rationale
**This design verified against:** community `viz-updates` @ `408ea004c`
**Layer:** browser DOM, read-time under the per-assembly gate. No seed mark, no server change, no
export path.

This design covers the strip's **appearance**: the anchored in-lane mini-toolbar draws no surface of
its own, and its glyphs take one of two tones derived from the colour they actually sit on. §02 defers
the strip's whole appearance here, on a structural rather than an aesthetic argument — *"The card
background is author-set, so the strip cannot carry a fixed treatment."*

**Four of §10.2's claims do not survive contact with the code, and this design corrects them rather
than following them.** They are listed in §1 and each is measured or cited. The most consequential is
the tone threshold: as specified it selects the *lower-contrast* ink across a wide band of author
colours, including the one swatch §10.2 flags as the case to check first.

---

## 0. Why this is the next step

L″ shipped in `408ea004c`, and with it the anchored strip's *geometry* is finished: the predicate now
measures the assembly's own lane, `lane >= 24` draws the strip, and below it draws no chrome at all
(`mini-toolbar.service.ts:67-111`). What has never been touched is what the strip *looks like*. What
ships today is exactly the "white bordered box" §10.2 exists to replace —
`mini-toolbar.component.scss:25-34` gives `.mini-toolbar-container` an `--inet-shell-surface-default`
fill, a 1px `--inet-default-border-color` border and a `--inet-radius-xl` radius, unconditionally, so
the anchored in-lane strip draws it too.

L″ also makes this pass cheaper than the spec costed it, for a reason the spec could not have known:
a hidden title now resolves to lane 0 and fails the same comparison a too-short lane does, so the
strip is **suppressed** rather than overlaid. §10.2's "the glyph overlays the plot" case no longer
exists on the anchored path, and the scrim it prescribes for that case has no remaining consumer.

---

## 1. What §10.2 gets wrong about this code

Recorded here, and to be carried into
[chart-card-source-doc-corrections.md](./chart-card-source-doc-corrections.md) — **not** into
`chart-card-design3/`, where a correction survives exactly one sync.

### 1.1 The `<img>` precondition does not exist

§10.2: *"The glyph is an `<img>` in mini-toolbar.component.html today, and color cannot tint an image.
Either switch to an inline SVG using currentColor — preferred — or drive them with a filter: invert()
pair."*

False. `mini-toolbar.component.html:48` and `:72` render `<i [class]="icon + ' icon-size-small'">`, an
icon-class element. All eighteen glyphs the chart toolbar can draw resolve to ineticons font
codepoints — checked individually against `assets/ineticons/variables.scss`, including
`brush-icon "\ea3b"`, `zoom-in-icon "\ec2a"`, `show-summary-icon "\ebea"` and
`menu-vertical-icon "\eb2a"`. None is missing from the font map and none resolves to a
`background-image`. `color` tints all of them.

**Consequence:** no inline-SVG conversion, no `filter: invert()` pair, and the two tones are one CSS
property. The swatches in §10.2 use the filter route, which is why its dark-tone dots are the same
asset inverted; that is an artifact of the mockup, not a requirement on the implementation.

### 1.2 The 0.45 threshold selects the lower-contrast ink

§10.2: *"Standard WCAG relative luminance of the resolved background colour, against L > 0.45 → light
tone, otherwise dark tone. Below the midpoint deliberately: mid-tone saturated colours read darker
than their computed luminance suggests, and 0.5 puts several common brand teals and greens on the
wrong side."*

The perceptual intuition is what drives the failure. Measured against WCAG 1.4.11's 3:1 floor for
meaningful non-text glyphs, using §10.2's own two ink values (light tone = black at .55, dark tone =
white at .80):

| Background | L | Rule picks | Black @.55 | White @.80 | Chosen | Verdict |
|---|---|---|---|---|---|---|
| `#E8563F` — §10.2 swatch | 0.242 | dark | 3.08 | 2.82 | 2.82 | **fails** |
| `#2FC4B2` — §10.2's flagged borderline | 0.433 | dark | 3.79 | 1.86 | 1.86 | **fails** |
| `#C8CCC9` — §10.2 swatch | 0.597 | light | 4.16 | 1.48 | 4.16 | passes |
| `#FFFFFF` — §10.2 swatch | 1.000 | light | 4.76 | 1.00 | 4.76 | passes |
| `#F1EFEA` — `TITLE_BG` today | 0.864 | light | 4.60 | 1.12 | 4.60 | passes |
| `#2D2B30` — `TITLE_BG_DARK` | 0.025 | dark | 1.32 | 9.52 | 9.52 | passes |
| `#252428` — `CARD_BG_DARK` | 0.018 | dark | 1.23 | 10.33 | 10.33 | passes |

Two of §10.2's four demonstration swatches fail with the tone its own rule selects, and the worst case
is the swatch it annotates *"the borderline case — check this one first if the threshold is
questioned."*

For these two specific ink values the tones actually cross over at **L ≈ 0.235**, not 0.45. Every
author colour between 0.235 and 0.45 — precisely the saturated teals and greens the threshold was
lowered to capture — receives the worse ink.

**The seeded defaults are all safe either way.** `#F1EFEA`, `#FFFFFF`, `#2D2B30` and `#252428` all
pass. This only bites on author-set backgrounds, which is the case §10.2 exists for.

### 1.3 The scrim reduces contrast

§10.2 prescribes, for the title-hidden overlay case, *"a 28px rounded patch in the tone colour at ~8%
alpha with backdrop-filter: blur(2px)."*

Two independent reasons it does not belong in this design.

**Its case no longer exists.** Post-L″ a hidden title yields lane 0 and the strip is suppressed
(`mini-toolbar.service.ts:70-111`), so on the anchored path no glyph overlays a plot.

**It is counterproductive where it was reused.** A scrim in the *tone colour* tints the background
toward the ink and reduces separation. Measured over the sub-3:1 band, worst achieved contrast by
scrim alpha: 0.08 → 2.76, 0.20 → 2.43, 0.35 → 2.03, 0.50 → 1.66, 0.80 → 1.19, 1.00 → 1.00. It
degrades monotonically, and at the prescribed ~8% it makes the worst case *worse* (2.97 → 2.76).

A fixed neutral plate would work (0.35 alpha → worst 4.54:1) but reintroduces a surface, which is the
one thing this design removes. §2.3 adopts a cheaper answer that needs no plate at all.

### 1.4 The hover background replaces nothing

§10.2 presents its hover background as adapting where a fixed grey would not. There is no grey to
replace. `.icon-hover-bg` sets `--inet-ui-neutral-hover-bg-color` on hover (`_themeable.scss:880-886`)
and the mini-toolbar's buttons carry that class — but `mini-toolbar.component.scss:40` sets
`background-color: transparent !important` on every button in the container, which beats it.
**Mini-toolbar buttons have no hover fill today.** §10.2's translucent tint is new behaviour, not a
swap, and should be reviewed as an addition.

---

## 2. The tone resolver

One pure exported function, `resolveStripGlyphTone`, in a new `strip-glyph-tone.ts` beside the
component. Returns `{ tone: "light" | "dark", alpha: number }`.

### 2.1 Dependencies, and one deliberate non-reuse

`@ctrl/tinycolor` is already a direct dependency (`web/package.json:37`, `^3.4.1`) and already imported
by `gui-tool.ts:20`. It supplies every primitive needed: `getLuminance()` (WCAG relative luminance),
`readability()` (WCAG contrast ratio), `setAlpha()`, `onBackground()` (alpha compositing) and
`isValid`. No hand-rolled colour maths, no new dependency.

**`GuiTool.getContrastColor` is deliberately not reused.** It decides via TinyColor's `isDark()` —
YIQ brightness against 128, not relative luminance — and returns pure `#fff`/`#000`
(`gui-tool.ts:1285-1293`). That is a different rule, and reusing it would reintroduce the class of
error §1.2 measures. Its two existing callers
(`viewsheet-pane.component.ts:842`, `vs-table-cell.component.ts:328`) are out of scope and untouched.

### 2.2 Which background the strip sits on

The anchored strip sits in the **title lane**, not on the card. `title-cell.component.html:20` paints
that lane from `titleFormat.background`; the card is `objectFormat.background`, seeded `#FFFFFF` /
`#252428` by `VSObjectChromeDefaults.cardBackgroundCss` via `ChartVSAssemblyInfo:104`.

Today the lane is filled: `VSTitleChromeDefaults:103` writes `TITLE_BG 0xF1EFEA` (or
`TITLE_BG_DARK 0x2D2B30`) onto the DEFAULT tier of a clone unless the author's USER tier or a
`format.css` TITLE class set it, in which case the author's colour is preserved and reaches the model.

**A single source is therefore wrong.** Decision 1 in
[chart-card-open-item-decisions.md](./chart-card-open-item-decisions.md) — decided, unscheduled —
makes the title band unfilled, at which point the card background shows through the lane and any rule
bound to `titleFormat.background` alone silently loses its input. A chain is what survives that:

1. `titleFormat.background` — the lane the strip is physically in
2. `objectFormat.background` — the card behind it
3. `viewsheetBackground` — the page canvas, wired as an **optional** input; it lives on
   `viewer-app.component.ts:463` and is not threaded down to `vs-object-container` today
4. `model.vizDark ? "#252428" : "#FFFFFF"`

A step is skipped when its value is absent, `transparent`, alpha-0, or fails `TinyColor.isValid`.
Step 3 only matters for an author who explicitly cleared the card background, since step 2 is seeded
for every marked assembly; it is in the chain so that case resolves deterministically rather than
falling to a default.

**Step 4 keys on `vizDark` rather than assuming white.** `vizDark` is already on the model
(`vs-object-model.ts:68`, and bound at `vs-object-container.component.html:46`), and without it an
author who cleared both the lane and the card in a dark-mode org would get dark ink on a dark canvas —
the one combination a fixed light default gets exactly backwards.

### 2.3 Selection: max contrast, then clamp to 3:1

Two candidate inks — black at base alpha 0.55, white at base alpha 0.80, §10.2's own values.

1. Composite each over the resolved background **at its base alpha** and measure `readability()`.
2. Take the ink with the higher ratio. An exact tie goes to light.
3. If the winner is under 3.0, raise **its** alpha toward 1.0 only until it reaches 3.0.

No threshold constant exists, so there is nothing to tune and nothing that silently invalidates when
an ink value changes.

**The choice is made before the climb, and that ordering is load-bearing.** Climbing both inks first
and then comparing their achieved ratios looks equivalent and is not: in the ~1% band where *both*
inks start under the floor, both climb to just above 3.000 and the comparison is then decided by
fourth-decimal noise, so the tone flips arbitrarily between two backgrounds a viewer cannot tell
apart. `#778888` is exactly that case — 2.9928 against 2.9820, a stable 0.011 apart at base, and
indistinguishable after both climb. Choosing at the base alphas keeps the decision on the stable
comparison; the climb is a correction applied to the winner, so it must not feed back into the
choice.

**Measured over the 4,096-colour cube (17-step RGB grid), in TinyColor itself:**

- worst achieved contrast **3.0002:1** (at `#009911`) — the floor is met everywhere
- alpha needed lifting on **0.9%** of colours
- largest alpha ever needed **0.805**, against a base of 0.80

The lifts are imperceptible: `#EE1199` 0.55 → 0.56, `#DD5544` 0.80 → 0.805, `#778888` 0.55 → 0.555.
Nothing here changes the appearance of any seeded default — `#FFFFFF`, `#F1EFEA` and `#252428` all
resolve at their base alpha.

For comparison, the alternatives considered and rejected: plain max-contrast with fixed alphas floors
at 2.97:1 (misses the bar by 1%); switching to full alpha in the band floors at 4.99:1 but introduces
a visible 0.55 → 1.0 jump in glyph weight between two near-identical author colours; a fixed neutral
plate at 0.35 floors at 4.54:1 but reintroduces a surface.

### 2.4 Hover is derived in CSS, not returned

One formula reproduces both of §10.2's hover values exactly:

```
--viz-strip-glyph-a-hover: min(1, calc(var(--viz-strip-glyph-a, 0.55) + 0.30));
```

Light: 0.55 + 0.30 = 0.85, matching `rgba(0,0,0,.85)`. Dark: 0.80 + 0.30 clamps to 1.0, matching
`#FFFFFF`. A lifted resting alpha carries through, so hover can never resolve weaker than rest. The
resolver therefore returns one alpha, not two, and the resting-to-hover relationship stays legible in
the stylesheet.

**The derived value needs its own property name.** Writing the step onto `--viz-strip-glyph-a` itself
— even in a `:hover` rule, where the referenced value looks like it comes from the non-hover cascade —
is a self-reference on the same element, which CSS Variables §3 makes invalid at computed-value time.
The step would silently not apply. A second name has no cycle and costs nothing.

The hover **background** stays static per tone — `rgba(0,0,0,.06)` light, `rgba(255,255,255,.18)` dark
— and lives entirely in CSS. Per §10.2 these resting alphas *are* the resting opacity; no further
multiplier may be applied on top of them (see §3.4).

### 2.5 Delivery and recomputation

Computed at the `vs-object-container` call site, where both formats are already in hand for each
`vsObject` (`vs-object-container.component.html:343-366`), and bound onto `<mini-toolbar>` as
`[attr.data-tone]` plus `[style.--viz-strip-glyph-a]`.

Custom-property binding is established in this codebase — five precedents, including
`[style.--inet-dropdown-toggle-color]` (`vs-table-cell.component.html:115`) and
`[style.--slider-wrapper-top.px]` (`vs-slider.component.html:56`).

Memoized on the resolved background string, so it recomputes per background change rather than per
change-detection pass — §10.2's *"computed once per background change — not per frame."*

---

## 3. The CSS seam

### 3.1 Scope, verified

Six templates render `<mini-toolbar>`: `vs-object-container.component.html:343`,
`editable-object-container.component.html:376` (composer), `embed-chart.component.html:52`,
`wizard-preview-container.component.html:108`, `vs-wizard-object.component.html:132` and
`vs-object-view.component.html:65`. **Only the first binds `anchorInTitleLane`**, so the other five
render the floating strip and are untouched by every rule below. The composer is out by
construction, not by exception.

Within that one site, the treatment applies to `.mini-toolbar--anchored`, which already carries both
the per-assembly gate and the anchored-set condition on its own (`mini-toolbar.component.scss:93-98`,
via `VSObjectContainer.isKebabResident`), so no separate `.viz-modern` scope is needed. The six types
are those in `ANCHORED_ASSEMBLY_TYPES` (`mini-toolbar.service.ts:41-53`); slices 4 and 5 inherit the
treatment for free when the container and calendar join that set.

The floating strip keeps its surface deliberately — it sits over arbitrary content and needs a
ground. The nav bar keeps `--inet-shadow-low` per §02.

### 3.2 Where the colour has to land

On the `<i>` **and** the button, with the same value. `assets/ineticons/style.scss:48` sets
`color: var(--inet-icon-color)` on the glyph element itself, so a `color` set only on the button never
inherits through — the glyph's own declaration wins. `.mini-toolbar--anchored .mini-toolbar-container
button i` is (0,2,2) against `.ineticons`'s (0,1,0) and wins without `!important`. The `!important` at
`_themeable.scss:1306` was needed only against `.icon-color-default`'s own `!important`
(`_icons.scss:95-97`), and that class is not applied in this template.

The button carries the same declaration for one reason: §3.3's focus ring uses `currentColor`, which
resolves against the element the `box-shadow` sits on. Setting the ink on the glyph alone would leave
`currentColor` on the button resolving to an inherited colour rather than the tone. One selector list
—  `button, button i` — covers both and keeps a single source for the value.

**One carve-out.** `manualRefresh` and `autoRefresh` (`chart-actions.ts:522,530`) return
`shape-filled-circle-icon auto-refresh-false` / `-true`, coloured `#fc575e` and `#66cc99` at
`_icons.scss:54-60` — a red/green state indicator, also (0,1,0), which the tone rule would flatten to
monochrome. Excluded with `:not(.auto-refresh-true):not(.auto-refresh-false)`. Both are `binding`-only
and the anchored strip renders only from `vs-object-container.component.html:343`, so they should
never co-occur; the guard is cheap insurance that keeps the rule correct if the binding pane ever
anchors.

### 3.3 Overrides, not deletions, for the surface

The surface declarations at `mini-toolbar.component.scss:31-33` are unconditional, so they are
overridden under the anchored scope rather than removed.
`.mini-toolbar.mini-toolbar--anchored .mini-toolbar-container` is (0,3,0) against
`.mini-toolbar .mini-toolbar-container`'s (0,2,0), so it wins without depending on source order —
the convention the file states for itself at `:156-161`.

Set to `background-color: transparent`, `border: none`, `border-radius: 0`, `overflow: visible`.

`overflow: hidden` at `:34` **stays** for the floating strip: buttons carry
`border-radius: 0 !important` (`:39`), so a square corner would poke outside the 6px pill there. It is
overridden only where the radius is gone.

The focus ring's inset arm becomes `inset 0 0 0 1px currentColor`. `currentColor` is the tone ink, and
`:focus-visible` also takes the hover alpha, so the ring is tone-correct with no extra plumbing. The
outer `var(--inet-focus-ring)` arm is unchanged, preserving the a11y contract.

### 3.4 Deletions, each with its reason

| Line | Deleted | Why |
|---|---|---|
| `:76-78` | divider `border-left` (suppressed under the anchored scope) | A fixed `--inet-default-border-color` on a surface the author owns — the exact failure §10.2 names |
| `:162-164`, `:166-169` | both anchored divider rules | Their whole job was suppressing that divider at rest and restoring it on reveal. Three rules collapse to one suppression |
| `:180` | `opacity: 0.55` on the resting kebab | The multiplier §10.2 forbids. `pointer-events: auto` stays; the transition moves from `opacity` to `color` |
| `:185-188` | hover `opacity: 1` | Replaced by the tone's hover alpha |
| `:204` | coarse `overflow: visible` | Dead once the anchored container is `overflow: visible` unconditionally |

**Why `:180` must go.** `mini-toolbar.component.scss:179-183` dims the resting anchored kebab to
`opacity: 0.55`. Multiplied by the light tone's own `rgba(0,0,0,.55)` that is an effective alpha of
0.303 — **2.12:1 on a white card, 2.10:1 on the `#F1EFEA` lane** — which would make the one control
visible at rest the least legible element on the card. The tone table already carries a
resting-to-hover step (§2.4), so the distinction the opacity rule provides is preserved in colour
instead.

**One deletion is a translation, not a drop.** `:207-211`'s coarse-pointer `opacity: 1` exists because
touch draws the kebab at rest as its only chrome. The coarse block therefore sets the resting alpha to
the hover value rather than losing the line. This design deliberately does **not** fold in §10.1
(resting visibility by pointer capability) — that is its own ready item, and this pass must not
pre-empt it.

---

## 4. What this design does not touch

- **§10.1**, resting visibility by pointer capability. Separate ready item. Today
  `isAnchoredResident` derives resting from geometry, and this pass leaves that alone.
- **§10.3**, dark mode. A luminance-derived tone is dark-correct by construction and needs no branch —
  `#252428` resolves to L 0.018 and takes the dark tone at base alpha, 10.33:1. Everything §10.3 lists
  as still needing a dark value (card frame, axis text, gridlines, plot background, legend rule) is
  outside this design.
- **The floating strip**, the composer, embed, both wizard surfaces and vsview. Byte-identical.
- **Gate-off.** Byte-identical; `.mini-toolbar--anchored` already carries the gate.
- **The glyph artwork.** No codepoint changes, no new glyphs, no alias repointing. This is tone and
  surface only.
- **Export.** Every file is under `web/projects/portal/src`. Nothing is written into a saved asset, so
  there is no seed mark involvement and no PDF/PNG/Excel consequence.

---

## 5. Testing

**The resolver is pure, so it is tested directly.** Table-driven over: §10.2's four swatches; the four
seeded defaults (`#FFFFFF`, `#F1EFEA`, `#2D2B30`, `#252428`); the two swatches that failed the old
threshold (`#E8563F`, `#2FC4B2`); the three lift cases (`#EE1199`, `#DD5544`, `#778888`); and the chain
fallbacks (`null`, `""`, `transparent`, `rgba(0,0,0,0)`, unparseable garbage, and each step falling
through to the next).

**One property test**, asserting the returned ink/alpha pair measures at least 3:1 over a coarse colour
grid. That ratio is the design's guarantee; it should be asserted rather than trusted, and it is the
test that would have caught §1.2.

**Component level** extends `mini-toolbar.component.spec.ts`: the `data-tone` and
`--viz-strip-glyph-a` bindings, and the anchored-vs-floating split. No new `*.tl.spec.ts` — nothing
here crosses HTTP.

**Regression guards.** The five non-anchored render sites and the gate-off path must be unchanged.

**Manual pass.** The three seeded backgrounds plus an author-set saturated card, at all three
densities, on a fine pointer and a coarse pointer: resting kebab, hovered strip, keyboard focus ring,
group spacing with the dividers gone, and the red/green refresh glyph if reachable.

---

## 6. Open risks

- ~~**Browser baseline.**~~ **Resolved 2026-08-26.** No `.browserslistrc` exists in `web/`, so
  Angular's own default target applies; `npx browserslist` resolves the oldest real engines to Chrome
  109, Firefox 140, Safari 26.3 / iOS 18.5, Edge 146 and Samsung 28. Space-separated
  `rgb(r g b / a)` is Chrome 65+ / Safari 12.1+ / Firefox 52+, and `min()` is Chrome 79+ /
  Safari 13.1+ / Firefox 75+ — both far below that floor. Ships with no fallback path.
- **Decision 1 interaction.** If the title band is unfilled, the chain falls through from
  `titleFormat.background` to `objectFormat.background`. Designed for, and both seeded values resolve
  to the same tone today (`#F1EFEA` L 0.864 and `#FFFFFF` L 1.000 both light; `#2D2B30` L 0.025 and
  `#252428` L 0.018 both dark), so no visible change is expected — but it should be re-verified when
  that lands rather than assumed.
- **Doc debt.** §1's four corrections belong in
  [chart-card-source-doc-corrections.md](./chart-card-source-doc-corrections.md). A correction written
  inside `chart-card-design3/` is destroyed by the next sync.
