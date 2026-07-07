# Phase 2 Gate — Recovery Sheet (changes lost to a branch switch)

**Why this exists:** the Phase 2 changes were implemented uncommitted on `epic-74519`, then lost when
the community submodule was switched `epic-74519 → bug-75494 → epic-74519` and rebased. Only the
untracked `_viz-tokens.scss` survived (orphaned — `global.scss` no longer imports it). This sheet
lets anyone re-apply the full set on a **clean** tree. Do NOT apply while unrelated uncommitted work
is present.

Property key: **`viewsheet.modernVisualization`**. CSS class: **`viz-modern`**.

## Backend (core, Java)

**1. `web/admin/presentation/model/LookAndFeelSettingsModel.java`** — after `boolean vsEnabled();`
```java
   boolean modernVisualization();
```

**2. `web/admin/presentation/LookAndFeelService.java`**
- `getModel()`, after the `customLogoEnabled` line:
```java
      boolean modernVisualization = SreeEnv.getBooleanProperty("viewsheet.modernVisualization", false, !globalProperty);
```
- builder, after `.vsEnabled(true)`:
```java
         .modernVisualization(modernVisualization)
```
- `setModel()`, after `SreeEnv.setProperty("repository.tree.sort", sort);`:
```java
      SreeEnv.setProperty("viewsheet.modernVisualization",
                          Boolean.toString(model.modernVisualization()), !globalSettings);
```

**3. `web/portal/model/PortalModel.java`** — after the `elasticLicenseExhausted()` default:
```java
   @Value.Default
   public boolean modernVisualization() {
      return false;
   }
```

**4. `web/portal/controller/PortalController.java`**
- before `PortalCreationPermisisons creationModel = refreshPortalCreationPermissions(principal);`:
```java
      boolean modernVisualization =
         SreeEnv.getBooleanProperty("viewsheet.modernVisualization", false, true);
```
- builder, after `.elasticLicenseExhausted(elasticLicenseExhausted)`:
```java
         .modernVisualization(modernVisualization)
```

**5. `web/viewsheet/service/CoreLifecycleService.java`** — after the `infoMap.put("inlineSvg", …)` line:
```java
         infoMap.put("modernVisualization",
                     SreeEnv.getBooleanProperty("viewsheet.modernVisualization", false, true));
```

## Frontend (web)

**6. `projects/em/.../look-and-feel-settings-view/look-and-feel-settings-model.ts`** — after `vsEnabled: boolean;`
```ts
   modernVisualization: boolean;
```

**7. `projects/em/.../look-and-feel-settings-view/look-and-feel-settings-view.component.ts`**
- form group (add after `selectedTheme: ["default"]`, add the comma):
```ts
            selectedTheme: ["default"],
            modernVisualization: [false]
```
- `set model(...)`, `if(model)` branch, after the `expand` setValue:
```ts
         this.form.get("modernVisualization").setValue(model.modernVisualization, {emitEvent: false});
```
- `set model(...)`, `else` branch, after the `defaultFonts` setValue:
```ts
         this.form.get("modernVisualization").setValue(false, {emitEvent: false});
```
- `emitModel()`, after the `expand` assignment:
```ts
      this.model.modernVisualization = this.form.get("modernVisualization").value;
```

**8. `projects/em/.../look-and-feel-settings-view/look-and-feel-settings-view.component.html`** —
after the `@if (form.controls.repositoryTree?.value) { … Expand All Nodes … }` block:
```html
        <mat-checkbox formControlName="modernVisualization">_#(Modern Visualization)</mat-checkbox>
```

**9. `projects/portal/src/app/portal/portal-model.ts`** — after `accessible: boolean;`
```ts
   modernVisualization: boolean;
```

**10. `projects/portal/src/app/portal/app.component.ts`**
- after `private readonly ACCESSIBILITY_CLASS: string = "accessible";`:
```ts
   private readonly VIZ_MODERN_CLASS: string = "viz-modern";
```
- in the `get-portal-model` subscribe, after `this.updateAccessibility();`:
```ts
            this.updateVisualizationMode();
```
- new method, after `updateAccessibility()`:
```ts
   updateVisualizationMode(): void {
      const body: HTMLElement = this.document.body;
      body.classList.toggle(this.VIZ_MODERN_CLASS, !!this.model.modernVisualization);
   }
```

**11. `projects/portal/src/app/vsobjects/viewer-app.component.ts`**
- field, after `accessible: boolean = false;`:
```ts
   modernVisualization: boolean = false;
```
- in `processSetViewsheetInfoCommand`, after `this.accessible = command.info["accessible"];`:
```ts
      this.modernVisualization = command.info["modernVisualization"];
```
- after the existing `if(this.accessible && !this.inPortal) { … }` block:
```ts
      if(!this.inPortal) {
         this.document.body.classList.toggle("viz-modern", !!this.modernVisualization);
      }
```

## CSS

**12. `projects/portal/src/scss/_viz-tokens.scss`** — NEW FILE (this one survived on disk as
untracked; verify it matches below, comments free of internal phase references). Full content:
```scss
/*!
 * <copy the AGPL header block from global.scss lines 1-17>
 */

// Visualization token contract. Legacy-default values; inert until a selector consumes them.
// Do not wire the chart-color group to marks or in-graph chrome: those are server-rendered
// (graph engine + format.css + GDefaults) and appear in export, so browser CSS cannot drive them.
:root {
  // density — browser-DOM data surfaces only; viewsheet-assembly density is server-side.
  --inet-viz-row-height: var(--inet-row-height-md);
  --inet-viz-chrome-row-height: var(--inet-row-height-md);
  --inet-viz-cell-padding-x: var(--inet-space-4);
  --inet-viz-cell-padding-y: var(--inet-space-3);
  --inet-viz-toolbar-height: var(--inet-control-height-md);
  --inet-viz-control-height: var(--inet-control-height-sm);
  --inet-viz-font-size: var(--inet-font-size-base);

  // state — live-view DOM overlays only; export-visible state is server-rendered via VSFormat.
  --inet-viz-hover-bg: var(--inet-ui-neutral-hover-bg-color);
  --inet-viz-selected-bg: var(--inet-shell-selected-bg-color);
  --inet-viz-selected-text: var(--inet-selected-item-text-color);
  --inet-viz-selected-border: var(--inet-primary-color);
  --inet-viz-active-border: var(--inet-primary-color);
  --inet-viz-context-bg: var(--inet-shell-surface-subtle);
  --inet-viz-inline-edit-bg: var(--inet-shell-surface-default);
  --inet-viz-filtered-bg: var(--inet-shell-surface-subtle);
  --inet-viz-sorted-color: var(--inet-text-muted-color);
  --inet-viz-pinned-divider: var(--inet-border-strong-color);
  --inet-viz-warning-bg: var(--inet-warning-color-light);
  --inet-viz-anomaly-bg: var(--inet-danger-color-light);
  --inet-viz-dimmed-opacity: 0.5;

  // compatibility — CSS gate switches DOM only; server-rendered surfaces follow the server-side
  // per-org property.
  --inet-viz-mode: legacy;

  // chart color: RESERVED / CONCEPTUAL ONLY — server-rendered, do not wire to marks or in-graph
  // chrome. Colors resolve server-side via format.css / descriptors.
  // --inet-viz-chart-series-*, --inet-viz-ramp-sequential-*, --inet-viz-ramp-diverging-*,
  // --inet-viz-threshold-*, --inet-viz-conditional-*
}

// Modern gate scope (DOM surfaces only), toggled per org via body.viz-modern.
.viz-modern,
[data-viz-theme="v2"] {
  --inet-viz-mode: modern;
}
```

**13. `projects/portal/src/global.scss`** — after `@import './scss/variables';`:
```scss
@import './scss/viz-tokens';
```

## Catalog (i18n)

**14. `core/src/main/resources/inetsoft/util/srinter.properties`** — in the alphabetical `M` block,
after `Model=Model`:
```
Modern\ Visualization=Modern Visualization
```

## Re-apply procedure

1. Ensure the community tree is clean (bug-75494 work committed/stashed).
2. Apply items 1–14 above.
3. Build: `cd community/web && npm run build` and `./mvnw -q -pl core compile -DskipTests`.
4. **Redeploy/restart the server** — the earlier "not visible" symptom was a bundle that predated the
   change; the running instance must be rebuilt from these sources.
5. Verify: EM › Settings › Presentation › Look and Feel → tick **Modern Visualization** → Apply →
   reload portal → `<body>` has `viz-modern`, `--inet-viz-mode` computes to `modern`.
6. **Commit immediately** so it can't be lost to another branch switch.
