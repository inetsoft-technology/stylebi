# Shell palette follow-up (PR7): focus ring, selected, hover, danger hover, themes

**Date:** 2026-11-20
**Issue:** Feature #80673 (epic #74519); open gaps tracked in Bug #81984
**Status:** decided; implemented on `feature-80673-7`
**Verified against:** community `feature-80673-7` (off `epic-74519` @ `284e8fcee4`, after #8570); line numbers are as of this branch
**Scope:** portal shell styles (light and dark), the static copies in `layout.css` and `login.css`,
and a `custom-theme` class on 6 page templates. Dashboard content is unchanged. No server logic,
export or persisted-state change.

This file records the decisions behind PR7 and the reasoning for each, so a reviewer can overturn
any one of them without re-deriving the rest.

The design lead's standing rule applies: **small changes are made, and the judgement call goes to
what most design-system vendors do.** Each decision says who took it, either under that rule or by
the product owner where the rule didn't settle it, and what overturning it costs. Contrast figures
are WCAG relative-luminance ratios, worked out from the hex values given. Vendor claims marked
"(uncited)" are from the team's survey and have no source linked here.

## Summary

| # | Decision | Taken by | Overturn cost |
|---|---|---|---|
| D1 | Danger button hover, focus and press: white label on `#B03A3A`, shell only | Vendor rule | One token, one rule |
| D2 | Light focus ring: solid `#B4610F`, keyboard only (`:focus-visible`), with a 2px gap on filled buttons; the navbar's ring is inset and tile-sized, in the navbar text colour in light | Vendor rule | One token value; the selector change, the gap and the navbar rule are separate |
| D3 | Dark focus ring: solid `#F0A75A` | Vendor rule | One token value |
| D4 | Dark neutrals: final, no change | Vendor rule | n/a |
| D5 | Text layout designer Text chip: unchanged | Vendor rule | n/a |
| D6 | Selected fill: a stronger neutral, `#DFD9CC` light and `#4A4750` dark | Product owner, from a side-by-side comparison | Two token values, plus O1's light value |
| D7 | Hover: one fixed grey per surface, with the beige midpoint `#E8E4DB` | Product owner, from a side-by-side comparison | Hover values in the map; keep the containers' gap rules for D2 |
| O1 | Grey text on selected rows: darker in the light shell (`#5F5E56`); dark accepted and tracked | Product owner | One token value and its content re-declaration |
| O3 | Custom-themed pages keep today's colours, including D1 | Product owner | The `custom-theme` gate, 6 template classes and the `login.css` gate |

---

## D1. Danger button hover, focus and press

**Problem.**
- The hover, focus and press label was `#7F2E2E` (light) or `#F2A3A3` (dark) on `#C84C4C`: 1.97:1
  and 2.29:1.
- WCAG 1.4.3 asks 4.5:1.
- Its only shell use is the viewer's "Reload Now".

**Decision.**
- A white label on `#B03A3A`, 5.98:1. The rest state is unchanged.
- White on today's `#C84C4C` would be 4.57:1, but the dark press filter (`brightness(1.12)`) would
  take it below 4.5:1. Hence the darker fill; the press then gives about `#C54141`, 4.99:1.
- Focus uses `:focus-visible`, as in D2, so a clicked button doesn't stay red.
- **Shell only.** The `.btn-danger` rule is global, so the new hover branch excludes dashboard
  content: dashboard danger buttons keep today's label on `#C84C4C`, and content parity checks it.
  On custom-themed pages the branch is gated off too (O3). The `:focus` to `:focus-visible` change
  is behaviour and applies everywhere.

**Basis.** Bootstrap's danger button keeps a white label on a darker hover fill (its hover is the
danger colour shaded 15%). Material 3 pairs its error colour with an on-error label (uncited).

**Overturning it.** Revert the token and the hover branch. Nothing else reads the token.

## D2 and D3. Focus ring: solid, keyboard only

**Problem.**
- The light ring was the brand orange at 28% alpha: 1.34:1 on white, and it showed on every mouse
  click because buttons used `:focus`. WCAG 1.4.11 asks 3:1 for focus indicators.
- The dark ring was the base orange at 75%, next to `#F0A75A` hover borders, so two oranges sat
  side by side.

**Decision.**
- **Light:** solid `#B4610F`, the brand orange one step darker. It measures:
  - white 4.51;
  - canvas 4.21;
  - beige 3.92;
  - hover (D7) 3.59 and the beige hover 3.55;
  - the selected fill (D6) 3.21.

  Plain `#D97A1A` would be only 2.71 on beige.

  Correction: these are the content surfaces, not every shell surface (as the code comment on the
  token claimed). The list never included the navbar: there `#B4610F` is only 2.02:1 on the
  navbar `#4A4845` and 1.15:1 on its hover tile (`#6E6D6A`). Before PR7 the navbar ring was the
  faint 28% orange, also below 3:1.
- **Navbar:** in the light shell the navbar draws the ring inset and tile-sized, in the navbar
  text colour: white is 9.11:1 on the navbar and 5.17:1 on the hover tile. The dark navbar keeps
  `#F0A75A` (9.15:1 on `#141316`) and takes the same inset, tile-sized shape. The colour is
  gated off on themed pages; the shape is behaviour and applies on them too. The ring is drawn on
  the tile (the `li`, which carries the hover fill) through `:has()`, because the link inside is
  narrower than the tile and growing it would widen its click target. Where `:has()` is not
  supported the link draws its own ring, in the navbar text colour in the light shell (9.11:1 on
  the navbar, 5.17:1 on the hover tile), flush around the link rather than the full tile.
- **Dark:** solid `#F0A75A`, 7.62:1 on `#252428` and 4.50:1 on the dark selected fill. The ring and
  the hover borders are now one orange; primary buttons still rest at `#D97A1A`.
- **Behaviour:**
  - The ring shows on `:focus-visible` only.
  - The press rule no longer sets `box-shadow`.
  - Filled buttons draw a 2px gap, in the surface colour, between the button and the ring. In
    dark, a focused primary button fills with `#F0A75A`, so without the gap the ring would merge
    into the button.
  - The login page's filled primary buttons (sign in, the restricted page's primary action, the
    signup buttons and the active-session dialog's Yes) take the same 2px gap: flush, the ring was
    1.45:1 (light) and 1.54:1 (dark) against the orange fill. The gap is the card's colour, and the
    dialog's colour in the active-session dialog. The default button stays flush.

**Basis.**
- Solid rings: Carbon's `$focus` is an opaque focus-border colour, and Atlassian sizes "the focus
  ring" at a 2px border width. Red Hat's focus indicator is a solid outline. Material 3, Primer and
  Fluent also use solid rings (uncited). Bootstrap's translucent ring is the exception: its button
  focus shadow is drawn at 50% alpha.
- Dark themes use the lighter accent tone for the ring: Red Hat (`#92c5f9` dark against `#0066cc`
  light); TYPO3 and Mozilla (uncited). Material 3's dark tones are lighter for every role, but its
  ring uses the secondary colour (uncited).
- Keyboard-only rings: browsers don't show a focus ring when a button is clicked with a pointer,
  and `:focus-visible` lets a page restyle the ring without changing when it appears (MDN).
- Inset navbar ring: WCAG 2.4.13 (Focus Appearance) counts a solid line inside the component,
  against its outer edge, as the perimeter. Carbon's header ring covers the whole tile. The
  composer navbar already draws an inset outline in its text colour.

**Overturning it.** Each ring is one token value in `inet-shell-state-tokens` (light) or the dark
`:root` block. The `:focus-visible` selector, the press rule and the gap are separate; reverting
the selector brings back the ring on every mouse click. The navbar's shape and its light colour
are four rules in `_themeable.scss` (the link's own ring suppressed, the tile ring, the
light-only colour rule, and the light-only link ring for browsers without `:has()`); the login
gap is one rule in `login.css`.

### Considered and not changed

- **Hovering a selected row shows no extra step.** Deferred to a follow-up with Bug #81984: in
  light, any visible step takes grey text below 4.5:1 (4.20) and the ring below 3:1 (2.91).
  Carbon's tree defines a selected-hover state; Primer's does not.
- **The orange ring on the danger button is kept.** With the gap it never touches the red fill;
  its neighbours are white (4.51:1) and the dark surface (7.62:1). Carbon, Primer and Polaris keep
  their normal focus colour on destructive buttons.
- **The dark navbar ring stays `#F0A75A`.** On the selected tab it meets the `#D97A1A` underline
  (1.54:1), so the ring's bottom edge and the underline read as one orange edge. A white ring (the
  navbar text colour, as in the light shell: 18.51:1 on the navbar, 3.11:1 against the underline)
  was compared side by side; the product owner kept the orange ring, since it appears only on
  keyboard focus and keeps the dark navbar's ring the same colour as the rest of the dark shell.
- **The tree-toolbar search button's ring stays flush** (3.72:1 on its chip, 4.51:1 on the
  surface). It sits tight against the search field and can read as a bordered box. Spacing it 1-2px
  from the field is a layout change, left as a follow-up with Bug #81489 (focus-ring polish).

### Follow-up: a thin ring, and a filled primary button

D2's solid 2px ring read as a heavy border on fields (the field's own border already turns the
primary orange on focus, and the 2px ring sat against it). The colour is what carries the 3:1, so the
width was reduced and the colours kept.

- **Fields and the rest of the shell:** `--inet-focus-ring-width` is 1px in the light and the dark
  shell. The ring colours are unchanged, so the contrast figures above are too (4.51, 3.92 and 3.21:1
  in light, 7.62:1 in dark). Dashboard content and themed pages keep the 2px ring: the content pin
  re-declares the width. The static pages follow it: `layout.css` through the token, `login.css`
  through `--ln-focus-width`, which stays 2px on a themed login page.
- **Modern primary button:** on keyboard focus it has no ring and no gap. Its fill becomes the ring
  colour and its label the shell's default surface colour (4.51:1 on the fill in light, 7.62:1 in dark;
  not the gap token, which a beige container re-declares as beige). It applies
  to `.viz-shell` only, outside dashboard content and custom-themed pages, and only on
  `:focus-visible`, so a mouse click or a dialog's automatic focus keeps today's deeper tint.
- **Other buttons:** secondary, default and classic primary keep their 2px ring and danger and
  classic primary keep the 2px gap, with the width fixed in the rule so the thin shared ring does not
  reach them.
- **Checked checkboxes, radios and switches:** unchanged. Their own rule (Bug #81489) draws a 2px gap
  and a 2px ring in the text colour, because a checked border is the same orange as its fill.
- **Considered and not taken:** a soft halo around the thin ring (about 5px across with the border,
  heavier than the button's ring, and it adds nothing to the contrast); the old faint 28% ring on the
  primary button (1.34:1); filling the button with the field orange `#D97A1A` (3.11:1 on white,
  2.71:1 on beige, 2.21:1 on a selected row); filling it with the text colour (13.5:1, but a heavy
  dark block).
- **Overturning it:** the width is one token value per shell (`inet-shell-state-tokens` and the dark
  block); the button's fill is one rule in `_bootstrap-override.scss`, after the filled-button gap
  rule.

## D4 and D5. Kept as they are

- **D4.** The dark neutrals are based on Material 3's dark baseline (uncited). Most text and border
  values match it; muted text and the strong border are in-between steps, and the surface steps are
  close to its container steps.
- **D5.** The Text chip in the Text layout designer (`#3A2E1A`, warning-soft) colour-codes the
  item type. A dark warning-subtle is a dark brown in Atlassian too (uncited).

## D6. Selected rows stand out from hover

**Problem.** A selected row and a hovered row looked almost the same: `#E7E2D8` against `#ECE9E2`
is 1.06:1 in light, and `#45424B` against `#37353B` is 1.23:1 in dark.

**Options.** Vendors split three ways (uncited):
- B: a stronger neutral fill (the comparison page cited Fluent and Primer);
- C: a tint (Material 3, Atlassian);
- D: a mild fill with an accent bar (Primer and Carbon navigation).

All three, plus today's fill (A), were compared side by side in both shells. The team leaned
towards the accent bar; the product owner chose B.

**Decision.** The stronger neutral fill: `#DFD9CC` light, `#4A4750` dark. Shell only; dashboard
content keeps `#E7E2D8`.
- **Light:** selected and hover are now 1.11 to 1.12:1 apart.
- **Dark:** 1.35:1 on the default surface and 1.56:1 on canvas. On subtle containers (dark
  toolbars) it is 1.21:1, slightly closer than today's 1.23:1, because D7 raises that surface's
  hover.

The trade-off for grey text is O1.

**Overturning it.** Two token values. O1's light muted value exists only because of this fill, so
revert it too.

## D7. Hover shows on every surface

**Problem.** Hover was one fixed grey. It showed on white (1.21:1) but almost vanished on the beige
side panel and toolbars (1.06:1).

**Options.**
- A: today's single fixed grey.
- B: a Material 3 translucent overlay, which gives equal strength everywhere but touches about 70
  readers (the 15 direct readers of `--inet-shell-surface-hover` and the 55 of
  `--inet-ui-neutral-hover-bg-color`) and conflicts with the glyph button's focus spread.
- C: one fixed grey per surface. Carbon sets an opaque hover token per layer
  (`$layer-hover-01/02/03`).

**Decision.** C, one fixed grey per surface:

| Shell | Surface | Hover | Against the surface |
|---|---|---|---|
| Light | white | `#E9E5DD` | 1.26 |
| Light | canvas | `#E9E5DD` | 1.17 |
| Light | beige | `#E8E4DB` | 1.10 |
| Dark | canvas | `#2C2A30` | 1.21 |
| Dark | default | `#36343A` | 1.26 |
| Dark | subtle | `#3E3B42` | 1.27 |

The dark default barely changes (today 1.27).

Beige is the tightest case. The beige surface and the selected fill are only 1.22:1 apart, so no
hover can be clearly different from both. The global `#E9E5DD` would be 1.09 from beige and 1.12
from selected; the midpoint `#E8E4DB` balances the two at 1.10 and 1.11. It was the product owner's
pick from the comparison, and the beige containers are needed anyway for the ring-gap colour (D2).
The earlier mock value `#E2DDD3` would have left selected and hover 1.04:1 apart in the side
panel's tree.

**Implementation note.** A token that uses `var()` is resolved where it is declared. So each
container re-declares the whole hover chain with `inet-surface-hover-tokens($hover)`, not just the base
token; dark adds its own readers through `inet-dark-surface-hover-tokens`. The ring-gap colour is
declared per container in a separate ungated rule as `var(<the container's surface token>)`, so
themed pages also get a gap in their own surface colour.

**Containers.** The table is the Sass map `$surface-hover-containers`. The full exclusion list is in
the PR description.

| Container selector | Light surface | Light hover | Dark surface | Dark hover | Gap token (light / dark) |
|---|---|---|---|---|---|
| `.repository-tree-pane, .tab-body .data-tree-pane` (report tree; data tab tree and the query editor's table tree) | beige `#F1EFEA` | `#E8E4DB` | subtle `#2D2B30` | `#3E3B42` | `--inet-portal-side-panel-bg-color` / same |
| `.portal-task-list-items .data-tree-pane` (schedule tree rail, `--schedule-task-list-rail-bg`) | beige | `#E8E4DB` | subtle | `#3E3B42` | `--inet-shell-surface-subtle` / same |
| `.data-model-left-top-pane` (physical model tables pane) | beige | `#E8E4DB` | subtle | `#3E3B42` | `--inet-portal-side-panel-bg-color` / same |
| `logical-model-property-pane .data-model-left-top-pane` (subtle `!important` in its component) | beige | `#E8E4DB` | subtle | `#3E3B42` | `--inet-shell-surface-subtle` / same |
| `.viewer-toolbar` (outside `.viewer-root`) | beige | `#E8E4DB` | subtle | `#3E3B42` | `--inet-vs-toolbar-bg-color` / same |
| `.data-model-header-pane, .physical-graph-toolbar, .ws-composite-table-breadcrumb-container, .slide-out-toggle-container, .chart-style-container` | beige | `#E8E4DB` | subtle | `#3E3B42` | `--inet-toolbar-bg-color` / same |
| `.ws-table-bar` (also `.bg-white1`: the light toolbar rule wins by order, the dark `.bg-white1` rule wins in dark) | beige | `#E8E4DB` | default `#252428` | `#36343A` | `--inet-toolbar-bg-color` / `--inet-shell-surface-default` |
| `.composer-bottom-tabs` | beige | `#E8E4DB` | subtle | `#3E3B42` | `--inet-nav-tabs-bg-color` / same |
| `.binding-editor .left-container:not(.modal *), .object-wizard-pane .left-container:not(.modal *)` | beige | `#E8E4DB` | subtle | `#3E3B42` | `--inet-composer-side-panel-bg-color` / same |

No shell control hovers directly on the dark canvas today, so the canvas value `#2C2A30` is defined for
future containers but unused; on dark canvas the `:root` hover `#36343A` applies.

**Overturning it.** Set the hover values back to one grey. Keep the container rules' gap declarations:
they carry D2's ring-gap colour on beige and on the dark subtle surfaces.

## O1. Grey secondary text on selected rows

**Problem.** The stronger selected fill lowers the contrast of grey secondary text, such as a
modified date:

| | Light | Dark |
|---|---|---|
| Before PR7 | 4.33:1 | 4.23:1 |
| After the D6 fill | 3.97:1 | 3.91:1 |

**Decision.**
- **Light:** the shell's grey text goes from `#6A685F` to `#5F5E56`: 4.64:1 on a selected row,
  6.52:1 on white, and still 1.91:1 lighter than normal text. It is set only in the shell tokens.
  `$shell-text-muted` is unchanged, so dashboards and the build-time aliases (`$inet-gray3`,
  `$inet-default-icon-color`) keep `#6A685F`. Dashboard content re-declares the muted token and its
  derived tokens, so it keeps the old value at run time too.
- **Dark:** it stays at 3.91:1, tracked in Bug #81984. A passing grey (about `#BEB9C6`, 4.74:1) is
  only 1.13:1 from normal text, so it would erase the text hierarchy.
- **Deferred:** showing secondary text in the normal text colour on selected rows (8.87:1 light,
  5.34:1 dark) waits until a design-system example is cited.

**Overturning it.** One token value in `inet-shell-state-tokens`, and its content re-declaration.

## O3. Custom-themed pages keep today's colours

**Problem.**
- An Enterprise Manager theme is written as one `:root { … }` block (enterprise
  `ThemeService.java:927`), loaded after the app styles. It covers the whole page, dashboards
  included. The theme editor exposes 145 portal tokens (`theme-css-view.component.ts:115-336`).
- Keeping dashboards unchanged means re-declaring the old values on dashboard elements, and D7
  re-declares hover on shell containers. A declared value beats an inherited one, so either would
  quietly override a theme's values. That is up to 13 theme tokens (9 from the ring, selected and
  hover chains, 4 from O1's muted-text chain), or up to 134 of the 145 if the whole default token
  set were re-declared.
- Themed pages are never dark (`ShellDarkMode.isDark`).

**Options.**
- **A:** accept the override.
- **B:** themed pages opt out.
- **C:** rewrite the theme's selector to reach dashboards. C doesn't fix the shell's containers,
  and it duplicates the dashboard selectors in Java.

**Decision: B.**
- **The gate.** Every PR7 colour rule applies only when the root lacks `custom-theme`:
  - the shell-token block on the root itself uses `:root:where(:not(.custom-theme))`, which keeps
    `:root`'s own specificity, so it wins over the `inet-runtime-tokens` block by coming later and
    still loses to `:root.shell-dark`;
  - rules on descendants use the prefix `:where(:root:not(.custom-theme))`, which adds no
    specificity: the dashboard re-declaration, the hover containers and the danger hover (D1);
  - the static `login.css` copy is gated on `.custom-theme` in the same way.
- **Templates.** The server sets `custom-theme` from the existing `customTheme` model attribute on
  the 6 pages that link the theme file and load the portal or login styles: portal `index.html`,
  `login.html`, `restricted.html`, `signup.html`, `signupDetail.html` and the error template.
  (Enterprise Manager links its own theme file but doesn't load the portal styles.)
- **What themed pages still get.** The keyboard-only ring and the ring gap are behaviour, not a
  colour a theme chooses, so they apply everywhere.

**Cost, accepted.** Themed sites keep the faint 28% ring (1.34:1) and the roughly 2:1 danger hover
label unless their theme sets those colours. A clicked danger button no longer stays red, but
keyboard focus still shows the 1.97:1 label. The release note tells them to set
`--inet-focus-ring-color`. Both gaps are tracked in Bug #81984.

**Overturning it.** Remove the gate from the selectors, the 6 template classes and the `login.css`
gate. Themes then lose up to 13 tokens on dashboards and the hover token in shell containers.

### Content parity: named exceptions and edges

- **The dark-marked calendar** (`.dropdown-datepicker.viz-dark`) follows the dark shell by design,
  as it did before PR7, so the content re-declaration leaves it out. In the dark shell it takes the
  new dark ring and hover. In the light shell it is pinned to its pre-PR7 values by its own rule
  (`:where(:root:not(.custom-theme):not(.shell-dark)) .dropdown-datepicker.viz-dark` in
  `_variables.scss`), as literals, since the calendar's own muted token is dark.
- **CKEditor** always mounts outside dashboard content: the annotation dialog attaches to `<body>`,
  the viewer's email dialog next to `.viewer-root`, and the schedule and Enterprise Manager editors
  are shell pages. Only embed paths mount dialogs inside a content root, and embeds always render
  light. So CKEditor is shell chrome and follows the shell palette. The editing area stays light
  (the `ckeditor-wrapper` rule) and rendered annotations are unchanged.
- **Custom CSS** (`portal.customCss`, `portal.additional.styles`, and `.css` files in the data
  space `web-assets/`) is outside O3, which covers Enterprise Manager themes only. These sheets load
  after the app styles without `custom-theme`, so a `:root` override of a PR7 token in them reaches
  the shell but no longer reaches dashboards. The release note handles it: it lists the
  re-declared tokens, gives a snippet that also targets dashboard content, and points to themes as
  the supported way.
- **Ring gap on the canvas.** No container re-declares the gap on the canvas, so a filled button
  directly on the canvas gets the default-surface gap (white light, `#252428` dark). No shell
  screen with that layout was found.

## Not in this PR

- Bug #81489's other items: rings that are missing on some controls, and keyboard reachability.
- The dialog primary-action audit.
- The organisation logo.
- The signup pages' own styling.
- Faint dark chips, card and pinboard icon tiles, and the calendar day hover.

## Verification

- `shell-palette.spec.ts`: source-reading guards for every token, selector and gate above.
- Content parity: dashboard content computes the same in both shells and against a pre-PR7 build.
- Light identity: every light-shell difference maps to D1, D2, D6, D7 or O1.
  Side effects of these: the thin scroll bar thumb follows O1's muted colour, and annotation
  tooltips inherit `--inet-viz-selected-bg` and `--inet-viz-sorted-color` with the new values,
  but no rule inside a tooltip reads them.
- Themed-page check: a test theme's values show unchanged, apart from the keyboard-only ring and
  the gap.
- Paired light and dark screenshots, and an axe colour-contrast sweep in both shells.

## Sources

- Bootstrap 5.2 buttons (danger hover shade, 50% focus shadow):
  https://getbootstrap.com/docs/5.2/components/buttons/
- MDN, `:focus-visible`: https://developer.mozilla.org/en-US/docs/Web/CSS/:focus-visible
- Material 3 state layers: https://m3.material.io/foundations/interaction/states/state-layers
- Carbon colour tokens (`$focus`, hover set per layer):
  https://carbondesignsystem.com/elements/color/tokens/
- Atlassian design tokens (`border.width.focused`):
  https://atlassian.design/components/tokens/all-tokens
- Red Hat focus indicators: https://ux.redhat.com/foundations/interactions/focus-indicators/
- WCAG 2.2 SC 1.4.11, Non-text Contrast: https://www.w3.org/WAI/WCAG22/Understanding/non-text-contrast
- WCAG 2.2 SC 2.4.13, Focus Appearance: https://www.w3.org/WAI/WCAG22/Understanding/focus-appearance
- Carbon UI shell header: https://carbondesignsystem.com/components/UI-shell-header/usage/
- WCAG 2.2 SC 1.4.3, Contrast (Minimum): https://www.w3.org/WAI/WCAG22/Understanding/contrast-minimum
