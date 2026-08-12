# Remove dead `icon()` declarations from menu-only actions

**Type:** cleanup · **Branch verified:** `epic-74519` @ `c75c3fabdf64` · **Blocks:** nothing

## Summary

Every `*-actions.ts` action declares an `icon()`, but only the toolbar renders one. Roughly 50 icon declarations sit on menu-only actions where nothing reads them. Delete them.

## The render split (verified)

`web/projects/portal/src/app/vsobjects/objects/mini-toolbar/mini-toolbar.component.html` reads the icon and renders it:

```html
@let icon = action.icon();
<i [class]="icon + ' icon-size-small'" aria-hidden="true"></i>
```

`web/projects/portal/src/app/widget/fixed-dropdown/actions-contextmenu.component.html` renders a label and an optional child arrow, and nothing else:

```html
<span class="item-label" [innerHTML]="action.label()"></span>
@if (action.childAction) {
  <span class="bare-arrow-right-icon"></span>
}
```

So `icon()` is live — for toolbar actions only. Deleting it from menu-only actions cannot blank a toolbar glyph.

The split is already clean in the source. In `chart-actions.ts`, every toolbar action (lines 380–507) declares a real glyph class — `drill-down-filter-icon`, `brush-icon`, `brush-no-icon`, `zoom-in-icon`, `zoom-no-icon`, `show-summary-icon`, `show-detail-icon`, `expand-icon`, `contract-icon`, `refresh-icon`, `edit-icon`, `setting-icon` — and every `fa fa-*` value sits on a menu-only action.

## Scope

Two dead vocabularies.

**`fa fa-*`** — ~24 files under `web/projects/portal/src/app/vsobjects/action/`:
calc-table, calendar, check-box, combo-box, crosstab, gauge, group-container, image, line, oval, radio-button, range-slider, rectangle, selection-container, selection-list, selection-tree, slider, spinner, submit, tab, table, text, text-input, viewsheet.

**`place-holder-icon`** and its modifiers (`icon-properties`, `icon-hyperlink`, `icon-highlight`, `icon-conditions`, `icon-edit`, `icon-filter`, `icon-group`, `icon-rename`, `icon-ungroup`, `icon-insert-row`, `icon-append-row`, `icon-delete-row`, `icon-reset-layout`, `icon-convert-freehand`) — in `table-actions.ts`, `crosstab-actions.ts`, `calc-table-actions.ts` and `abstract-vs-actions.ts`.

Both go.

## Decision: delete, don't populate

Do not give these actions real glyphs. The menus are short and verb-labelled, they are opened deliberately rather than scanned, and the toolbar already carries the icon vocabulary — a second one in the overflow menu duplicates it without adding recognition. Populating instead of deleting also turns an afternoon into a design project: every action across every assembly would need a glyph, plus a call on whether icons appear on all items or only some.

Keep `icon()` on the interface. Toolbar actions share the same base class and genuinely render it.

## Evidence nothing renders these strings

The typo density is the proof:

- `fa fa-slider` — `chart-actions.ts` lines 65, 73, where every other file says `fa fa-sliders`
- `fa fa-spinners` (`spinner-actions.ts:44`), `fa fa-submits` (`submit-actions.ts:44`), `fa fa-tabs` (`tab-actions.ts:44`), `fa fa-text-inputs` (`text-input-actions.ts:44`) — none are Font Awesome names
- `"place-holder-icon, icon-reset-layout"` and `"place-holder-icon, icon-convert-freehand"` — a comma inside the class string (`crosstab-actions.ts:143, 150`)
- `icon-hightlight` — misspelled (`table-actions.ts:68`)

Years of unnoticed errors in strings the DOM never sees.

## Before starting

Confirm no third consumer reads `icon()`. The toolbar and the context menu were both checked; other renderers were not surveyed.

## Durable fix

After the deletion, make the split enforceable: `icon()` required on toolbar actions, absent on menu-only ones. If the type system can express that, the dead declarations cannot come back one at a time. Without it, the next contributor adds exactly one, unevenly — which is how this state was reached.

## Not in scope

The chart card spec (§02 toolbar, §09) — it ships identically with or without this cleanup.
