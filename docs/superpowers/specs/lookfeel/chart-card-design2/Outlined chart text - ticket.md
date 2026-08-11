# Convert chart SVG text from outlines to `<text>`

**Type:** rendering change · **Branch verified:** `epic-74519` @ `c75c3fabdf64` · **Blocks:** nothing · **Blocked by:** chart type scale (see Prerequisite)

## Summary

Chart SVG contains zero `<text>` elements — Batik converts every glyph to a path. Switching to real `<text>` makes chart labels selectable, searchable, screen-reader accessible, and reachable by CSS. The code change is one boolean. The project is the layout consequences.

## The switch

`utils/inetsoft-xml-formats/src/main/java/inetsoft/util/graphics/SVGUtil.java:63`, in `getSVGDocument()`:

```java
return new SVGGraphics2D(ctx, true);
```

Batik's signature is `SVGGraphics2D(SVGGeneratorContext generatorCtx, boolean textAsShapes)`. The `true` is why there are no `<text>` elements. This is the **only** construction site — a single choke point.

## Prerequisite — extend the seed mark to cover this switch, don't just gate it live

As written, the switch is a single global boolean at one choke point (`getSVGDocument()`), with no
conditional on it at all. Flipping it ships to every chart in every org the moment it deploys — gate-on
or gate-off, new assemblies or years-old ones — because nothing about this mechanism is per-object or
per-org the way the seeded chrome properties are.

**A bare live `isVizModern()` read is not enough here.** That would flip every chart in an org at once
the moment the org's gate is on — the same all-or-nothing problem the seed mark (a sibling
visualization-widget-spec project, §03) was built to avoid for card chrome. That project's mark is a
general tri-state field on `VSAssemblyInfo` — not chrome-specific — already read by the chrome and density
resolvers, and chart is already one of the marked assembly types. **Extend it to cover this switch**: at
the SVG generation call site, resolve `textAsShapes` from the chart's own mark vs. the current gate (mark
≠ gate → recompute), the same shape as the chrome resolver, instead of a bare gate read.

That also gets per-dashboard, designer-driven migration for free: that same project's "Modernize" bar
(dismissible, per-dashboard, stamps unmarked assemblies forward when a designer opts in) can stamp charts
into text rendering the same way it stamps chrome — no separate opt-in UI needed. **One gap to close when
wiring this in:** that bar's "N assemblies would change appearance" count is scoped to chrome geometry
today; a chart whose only visible change is outline→text needs to be counted too, or the bar undersells
what it's about to do.

This should land before anything below is implemented — it is a precondition alongside the type scale,
not an optional cleanup, and it should ship as part of the same mark work rather than as its own gate.

## Prerequisite — settle the chart type scale first

`GDefaults.java` sets chart fonts at **9pt / 10pt / 11pt** (small / text / title). Java2D's default transform puts those at roughly 9/10/11 device pixels. The browser shell starts from `--inet-font-size-base: 13px`. Chart labels are therefore ~15–30% smaller than surrounding UI text.

Today this is harmless: the chart is an image, so the two scales cannot see each other. Once chart text is real `<text>`, it lives in the document and inherits — and "what size is chart text?" stops being a private answer.

**Do not let it inherit 13px.** Every label would grow 18–44%, and every layout the server computed at 9–11px — tick spacing, label clearance, the fit decisions behind the degradation ladder, top-lane height — was measured against the smaller size. That is a silent global re-layout of every chart, not a text-selection feature.

**Do this instead:** give the chart its own named type scale (`--inet-chart-font-size-sm/md/lg` or equivalent) set to the *current rendered pixel sizes*, so conversion is visually neutral by construction. Any deliberate size change then happens later, separately, on purpose.

Related: points and pixels don't scale together under export DPI, browser zoom or HiDPI. Contained inside an image today; not afterwards.

## The real cost — text metrics move to the viewer

Batik lays out labels with Java2D metrics on the render host. The chart's own decisions — tick thinning, label placement, whether an axis title fits, ellipsis points — are all made against those metrics. With `<text>`, the browser re-measures with its own engine and can disagree.

Where it bites, in order:

- **Right-anchored and centred text** put the full or half measurement error at the visible edge. Y-axis labels and centred titles are the exposed cases.
- **Left-anchored text** lands correctly at its absolute x; error only shows at the end of the run. Mostly safe.
- **Anything already tight** — ellipsized labels, rotated tick labels, and every fit decision in the degradation ladder — can now be contradicted by the browser.

### Mitigation that bounds the risk: `textLength`

The server already knows the advance width it measured. Emit it on each run:

```xml
<text x="…" y="…" textLength="…" lengthAdjust="spacingAndGlyphs">
```

The browser is then *forced* to the server's width and cannot disagree. Layout becomes deterministic again. Cost is slight glyph-spacing distortion where the two engines differ — invisible at label sizes, and strictly better than collision.

Batik does not emit this by default, so it is a post-processing pass over the generated DOM. Real work, but bounded and testable.

**Without `textLength`, this is open-ended visual QA across every chart type. With it, it is a small-to-medium project.** Do not attempt the former.

## Scope beyond charts

`getSVGDocument()` is the shared factory. Flipping the flag globally also changes:

- viewsheet export (`report/io/viewsheet/svg/SVGVSExporter.java` and helpers)
- image and shape assemblies
- annotations
- `SVGAnimationDOMInjector` (166KB, manipulates this DOM — may assume path elements)

**Recommended scoping:** browser display only, retaining outlines for export. Note this does *not* reduce the metric risk — that risk lives entirely in the browser path — but it removes the font-delivery and third-party-tool risks below, and it is reversible.

## Font delivery (export paths only)

Outlines are self-contained: the SVG renders identically anywhere, including Illustrator or an emailed file. `<text>` requires the font wherever it is opened. Roboto ships to the browser via `roboto-fontface`, so in-app display is fine; exported SVG opened elsewhere is not. Either embed the font in exported SVG or keep export on outlines.

## Test surface

- Charts with long, rotated, and ellipsized tick labels
- Charts at the bottom of the degradation ladder, where fit decisions are tightest
- Server-side PNG/PDF export (transcoders rasterize — should be unaffected, must be confirmed)
- Animated charts through `SVGAnimationDOMInjector`
- A render host missing the configured font (JDK falls back silently to `'Dialog'`)

## What it buys

Selectable and searchable chart text; screen-reader access to labels; smaller SVG payloads; and CSS finally able to reach chart type — which is the standing limitation recorded in the chart card spec §09.

## Not in scope

The chart card spec. Every rule in §04 and §06 is stated as geometry, not type styling; the card ships identically before or after this conversion.
