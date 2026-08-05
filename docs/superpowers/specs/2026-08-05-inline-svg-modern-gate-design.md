# Inline-SVG Chart Rendering Under the Modern Visualization Gate — Design

Date: 2026-08-05
Status: approved, ready for implementation planning

## Goal

Make inline-SVG chart rendering follow the modern-visualization gate instead of requiring a
second, undiscoverable property.

`graph.svg.inline` is what turns the chart plot area from an opaque `<img>` into inline SVG in the
page DOM. Every modern chart interaction depends on it: hover dimming, snap-series dimming,
external series/relation dimming, and the CARD tooltip tail. Today an administrator who enables
**Modern Visualization** in EM gets modern density, chrome, object chrome and palette, but none of
the interaction behavior, and nothing in the UI hints that a second switch exists.

After this change:

1. `graph.svg.inline` unset + modern on → inline SVG on.
2. The property remains an explicit override in both directions, so an administrator who hits a
   performance ceiling can fall back to `<img>` while keeping modern chrome, and existing
   deployments that set it standalone keep working unchanged.
3. A chart large enough that the server refuses SVG renders as a PNG plot instead of a blank tile.

## Current state

### The property and its single read site

`CoreLifecycleService.java:304`:

```java
infoMap.put("inlineSvg", "true".equals(SreeEnv.getProperty("graph.svg.inline")));
```

That is the only read of the property in the tree. It is already org-scoped, though not visibly:
`SreeEnv.getProperty(String)` (`SreeEnv.java:53-55`) calls `PropertiesEngine.getProperty(name,
false)`, which defaults `orgScope` to `true` (`PropertiesEngine.java:102-104`). So a per-org
`graph.svg.inline` override already resolves today — the same scoping
`viewsheet.modernVisualization` gets from its explicit `orgScope=true` on the next line
(`:305-306`).

The value ships to the client in `SetViewsheetInfoCommand` and lands in
`chartConfigService.inlineSvg` (`chart-config.service.ts:27`), read by the viewer
(`viewer-app.component.ts:2760`), the composer (`viewsheet-pane.component.ts:792`) and the embed
viewer (`embed-chart.component.ts:271`).

`chart-plot-area.component.html:62-78` is where it takes effect — the tile branch and its
pan double-buffer twin at `:91-99`:

```html
@if (inlineSvg) {
  <div class="chart-plot-area__inline-svg" [chartInlineSvg]="getSrc(tile, container)" ...>
} @else {
  <img [chartImage]="getSrc(tile, container)" ...>
}
```

### The server does not consult either flag

`chart-object-area-base.ts:219-231` hard-codes `svg=true` into every plot-area URL it builds, and
`VGraphPair.getSubGraphic` (`:2455-2458`) applies the animation hint — which injects the hover-dim
CSS unconditionally, per the comment there — on every plot request. The response bytes are
identical whether or not `graph.svg.inline` is set. The property decides only whether the client
puts those bytes in the page DOM or in an `<img>`.

Two consequences:

- Turning it on changes nothing about what the server computes or how long it takes.
- It has **zero export surface**. Export runs through the same service with `export=true` and its
  own format selection; no export path reads `inlineSvg`. This is the only piece of the modern
  visualization work with no live/export parity obligation.

### Features gated on it

`chart-plot-area.component.ts` routes all of these through `inlineSvgTiles` under an
`if(this.inlineSvg)` guard: `highlightElement` / `highlightElements` (`:832-840`),
`highlightSnapSeries` (`:848`), `setExternalSeriesDim` (`:1097`, `:1106`),
`setExternalRelationHighlight` (`:1129`, `:1135`).

The CARD tooltip tail is gated at `chart-area.component.ts:379-381`:

```ts
// Tail needs a mark rect, only available from inline-svg tiles.
this.tooltipTail = this.model && this.model.tooltipStyle === "CARD" &&
   this.chartConfigService.inlineSvg;
```

### Administrator-facing surface

EM → Settings → Presentation → Look and Feel has a **Modern Visualization** checkbox with a nested
**Dark Mode** checkbox (`look-and-feel-settings-view.component.html:38-48`). `graph.svg.inline`
appears in no UI; it is reachable only through the free-form Properties page. The other modern
sub-gates — `viewsheet.modernChartChrome`, `viewsheet.modernObjectChrome`,
`viewsheet.modernTableStructure`, `viewsheet.modernChartPalette` — are likewise advanced
properties with no UI.

### The large-chart PNG fallback

`AssemblyImageService.java:820-828` refuses SVG for a plot area of 10,000 rows or more:

```java
// svg is more expensive than java graphics. very large svg can also crash the
// browser, so only only use svg when the number of points is not excessive
if(rcnt < 10000) {
   image = pair.getPlotGraphic(row, col, !noAnimation);
}
```

`getChartSVG` then returns null and `:658-660` falls back to `getChartImage`, and the response is
labelled honestly: `:1204-1217` sets `image/png` when the result is a raster and `image/svg+xml`
otherwise.

The client does not check. `chart-inline-svg.directive.ts:233` requests `responseType: "text"` and
`:249` assigns `response.body` straight to `innerHTML`. In inline mode an oversized plot therefore
decodes PNG bytes as text and injects them as markup: a blank tile. The bug exists today and is
masked only by the property defaulting off; coupling the flag to the modern gate is what makes it
reachable.

## Design

### Part 1 — a gated resolver

New `VSChartInteractionDefaults` in `inetsoft/uql/viewsheet/internal/`, alongside the eight
resolvers already there — `VSDensityDefaults`, `VSChartChromeDefaults`, `VSChartPaletteDefaults`,
`VSTableStructureDefaults`, `VSTitleChromeDefaults`, `VSObjectChromeDefaults`,
`VSOutputChromeDefaults`, `VSCalendarChromeDefaults`:

```java
public static boolean isInlineSvg() {
   String prop = SreeEnv.getProperty("graph.svg.inline", false, true);

   if(prop != null && !prop.isEmpty()) {
      return "true".equals(prop);
   }

   return VSDensityDefaults.isModern();
}
```

Resolution table:

| `viewsheet.modernVisualization` | `graph.svg.inline` | result | vs. today |
|---|---|---|---|
| off | unset | false | unchanged |
| off | `"true"` | true | unchanged |
| on | unset | **true** | new |
| on | `"false"` | false | new opt-out |

An explicit property wins in both directions; unset follows the modern gate.

This deviates deliberately from the `isModern() && !"false".equals(...)` idiom used by
`VSChartChromeDefaults.isModern()` (`:44-48`). Those sub-gates only ever need to switch a modern
feature *off*; this one must also support the existing "inline SVG while modern is off"
configuration, which the `&&` form cannot express.

Not folded into `VSChartChromeDefaults`: that class supplies a chrome color palette, and this is a
render-mode switch with no colors. Keeping it separate also gives the interaction features a named
home for anything that follows.

#### Org scoping

**Correction (2026-08-05, caught during Task 1 review).** An earlier draft of this section claimed
`graph.svg.inline` was read globally, and that org-scoping it repaired a multi-tenant bug where one
org's `false` would disable inline SVG for every other org. That was wrong.
`SreeEnv.getProperty(String)` (`:53-55`) calls `PropertiesEngine.getProperty(name, false)`, which
defaults `orgScope` to `true` (`:102-104`) — so a per-org override has always resolved and there is
no bug here to fix.

The read is written as `SreeEnv.getProperty("graph.svg.inline", false, true)`: behaviorally
identical to the single-argument form it replaces, but explicit, matching how
`VSDensityDefaults.isModern()` and every sibling resolver state their scoping. No behavior change,
no migration.

Scoping still shapes the design, just not as a defect being repaired. Because the resolver derives
from a per-org gate, each org's inline mode follows its own modern flag with no property set
anywhere, and an explicit override applies at whichever scope it is written to.
`PropertiesEngine.useAvailableOrgProperty` (`:371-397`) returns `inetsoft.org.<orgID>.graph.svg.inline`
only when that key actually exists in the properties, and otherwise the bare name, so a globally set
value still resolves for every org. `graph.svg.inline` is not in `EXCLUDED_ORG_PROPERTIES`
(`:1049-1051`, five security properties), so nothing blocks it.

Org resolution requires a principal on the thread — `useAvailableOrgProperty` (`:377-382`) falls
back to the global name without one. `CoreLifecycleService` reads this at viewsheet open on a user
request thread, so the requirement is always met. Unlike the chrome and palette resolvers, this one
is never evaluated on a scheduler or export thread.

#### Writing the override

There is no per-org write path for a free-form property, and this design does not add one.

- `LookAndFeelService.setModel` (`:173-178`) writes the modern gate with
  `SreeEnv.setProperty(name, val, !globalSettings)`, where `globalSettings` comes from
  `PresentationSettingsController` (`:105-106`, `:178-180`) as
  `isSiteAdmin && !orgSettings || !securityEnabled || !multiTenant`. That is a proper org-scoped
  write, but it only covers the fields on the Look and Feel form.
- EM → Settings → Properties writes global only: `PropertiesController.java:113` calls
  `SreeEnv.setProperty(propertyName, value)` with no scope argument.

So the override is set by name in the Properties page:

| Intent | Property |
|---|---|
| Opt every org out | `graph.svg.inline` = `false` |
| Opt one org out | `inetsoft.org.<orgID>.graph.svg.inline` = `false` |
| Inline SVG with modern off, all orgs | `graph.svg.inline` = `true` (as today) |
| Inline SVG with modern off, one org | `inetsoft.org.<orgID>.graph.svg.inline` = `true` |

The qualified form works because `setProperty` applies `fixPropertyNameCase`, which at `:360-366`
preserves the org segment and lowercases the remainder — producing exactly the key
`useAvailableOrgProperty` looks up. This is how per-org configuration of any advanced property works
today, including the four existing modern sub-gates, none of which has a UI. Both forms belong in
the release note.

Rejected: a nested checkbox on the Look and Feel page beside Dark Mode. It would be discoverable and
correctly scoped, but a performance escape hatch reads wrong in a look-and-feel dialog, and it would
make this the only modern sub-gate with a UI.

`CoreLifecycleService.java:304` becomes:

```java
infoMap.put("inlineSvg", VSChartInteractionDefaults.isInlineSvg());
```

Nothing downstream changes — the command payload, `chartConfigService.inlineSvg`, the viewer, the
composer, the embed viewer and both template branches are untouched.

### Part 2 — honour the PNG fallback

All in `chart-inline-svg.directive.ts`. The plot is fetched once as bytes
(`responseType: "arraybuffer"`), and the success branch of `loadSvg()` (`:244-253`) inspects
`response.headers.get("Content-Type")` to decide what to do with them. Diverge only on an explicit
raster type — `type.startsWith("image/") && !type.includes("svg")` — so a proxy that strips the
header cannot break a working chart. On the SVG path the bytes are decoded as UTF-8 and inlined as
before; on the raster path they are wrapped in a `Blob`, whose object url becomes the `src` of an
`<img>` that replaces the host's contents. That single fetch is deliberate: the plot URL carries no
`Cache-Control`, `Expires` or validator, so a second GET for the `<img>` would be re-served from the
server and re-rasterize the tile — precisely the cost the 10,000-row threshold exists to avoid. The
object url is revoked whenever it is replaced and on destroy. Then continue into
`afterSvgInjected()`, `scheduleReady()` and `onLoaded` exactly as the SVG path does.

**No `pngFallback` flag and no per-method guards.** `afterSvgInjected()` (`:665-705`) clears
`elementGroupMap`, `anchorGroupMap`, `labelGroupMap`, the relation and treemap state and every
hover flag, then sets `svgRootEl = querySelector("svg")` — null when the host holds an `<img>`.
That state makes every interaction path inert on its own:

- `activateKeys` (`:438-501`) finds nothing in `elementGroupMap`, and its `inetsoft-dim-all` branch
  requires `elementGroupMap.size > 0 && this.svgRootEl`.
- `highlightSnapSeries` (`:354-356`) early-returns on `!isLineSeriesHover`.
- `setExternalSeriesDim` (`:1484`) and `setExternalRelationHighlight` (`:1422`) find no matching
  elements and both null-guard `svgRootEl`.
- `getElementAnchor` (`:328-333`) returns null on an empty `anchorGroupMap`.

Calling `afterSvgInjected()` on the raster path is therefore the whole guard — and it is also what
prevents the sharper hazard: a tile that previously held real SVG and then reloads oversized would
otherwise keep a populated index and a **detached** `svgRootEl`, handing the tooltip a bounding rect
from an element no longer in the document.

`scheduleReady()` (`:633-643`) already returns early when there is no `<svg>`, so it needs no change.

The tooltip tail needs no work. `markAnchor` (`chart-plot-area.component.ts:859-871`) already
falls back to `regionAnchor` when no tile resolves an anchor, and `regionAnchor` (`:878-887`)
derives its point from `region.centroid` in the chart model, independent of the SVG. Under PNG
fallback the tail still places itself, from a bounding-box centre rather than a mark rect.

Net behavior for a chart of 10,000 rows or more under modern viz: a PNG plot with no hover dimming,
exactly what legacy mode renders today, instead of a blank tile.

### Testing

- `VSChartInteractionDefaultsTest` — the four resolution rows above, plus an empty-string value
  (counts as unset) and a non-`true` value (off). Not covered: that an org-qualified key overrides
  the global one. That is `PropertiesEngine` behavior, needs a `ThreadContext` principal and an
  `OrganizationManager` org to exercise, and none of the eight sibling `*DefaultsTest` classes set
  that up.
- `chart-inline-svg.directive.spec.ts` — `image/svg+xml` inlines the SVG and builds the index;
  `image/png` injects an `<img>`, leaves the index empty, nulls `svgRootEl` and still emits
  `onLoaded`; an svg→raster reload drops the stale index; a missing content type keeps inlining; the
  raster's object url is revoked when it is replaced and on destroy.
- No export tests: verified above that no export path reads this flag.

Run the frontend spec with:

```
npx ng test portal --include="**/chart-inline-svg.directive.spec.ts"
```

### Rollout

The default flip is a visible behavior change for organizations already running modern
visualization: on their next viewsheet open they gain hover dimming, snap dimming, series and
relation dimming, and the CARD tooltip tail, with no configuration change on their part. This
belongs in the release notes.

The release note must also give both override forms from the table above, since the qualified
per-org name is not discoverable from any UI. They exist because inline SVG puts every mark of every
chart into the page DOM, so a chart-dense dashboard carries a client-side DOM cost the `<img>` path
does not — which is why an org running such dashboards may want out even under modern viz.

### Scope

Five files, all under `community/`:

| File | Change |
|---|---|
| `core/src/main/java/inetsoft/uql/viewsheet/internal/VSChartInteractionDefaults.java` | create |
| `core/src/main/java/inetsoft/web/viewsheet/service/CoreLifecycleService.java` | one line at `:304` |
| `web/projects/portal/src/app/graph/objects/chart-inline-svg.directive.ts` | PNG fallback branch and guards |
| `core/src/test/java/inetsoft/uql/viewsheet/internal/VSChartInteractionDefaultsTest.java` | create, following the eight sibling `*DefaultsTest` classes |
| `web/projects/portal/src/app/graph/objects/chart-inline-svg.directive.spec.ts` | add the two content-type cases |

Community PR only; no enterprise counterpart.
