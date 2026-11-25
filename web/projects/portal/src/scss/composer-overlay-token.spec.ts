import { readdirSync, readFileSync, statSync } from "fs";
import { dirname, join, relative } from "path";
import { fileURLToPath } from "url";

const __dirname = dirname(fileURLToPath(import.meta.url));
const src = join(__dirname, "..");
const read = (...p: string[]) => readFileSync(join(...p), "utf-8");

function scssFiles(dir: string): string[] {
   return readdirSync(dir).flatMap(name => {
      const p = join(dir, name);
      return statSync(p).isDirectory() ? scssFiles(p) : p.endsWith(".scss") ? [p] : [];
   });
}

// WCAG 2.x relative luminance and contrast ratio
function lum(hex: string): number {
   const c = [1, 3, 5].map(i => parseInt(hex.slice(i, i + 2), 16) / 255)
      .map(v => v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4));
   return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2];
}

function contrast(a: string, b: string): number {
   const [hi, lo] = [lum(a), lum(b)].sort((x, y) => y - x);
   return (hi + 0.05) / (lo + 0.05);
}

// the block that starts at the first match of `start` and ends at its matching brace
function block(text: string, start: RegExp): string {
   const i = text.search(start);

   // a missing anchor must fail the test, not hand an empty block to a not.toContain
   if(i < 0) {
      throw new Error(`anchor not found: ${start}`);
   }

   let depth = 0;

   for(let j = text.indexOf("{", i); j < text.length; j++) {
      if(text[j] === "{") {
         depth++;
      }
      else if(text[j] === "}" && --depth === 0) {
         return text.slice(i, j + 1);
      }
   }

   return text.slice(i);
}

describe("composer overlay tokens", () => {
   const overlay = () => read(__dirname, "_composer-overlay.scss");
   const tone = (name: string) =>
      overlay().match(new RegExp(`--inet-composer-overlay-${name}:\\s*(#[0-9a-f]{6})`, "i"))[1];

   it("declaredOnlyBehindTheThemeGate", () => {
      expect(overlay()).toMatch(
         /:root:where\(:not\(\.custom-theme\)\)\s*\{[^}]*--inet-composer-overlay-stroke:\s*#[0-9a-f]{6}[^}]*--inet-composer-overlay-halo:\s*#[0-9a-f]{6}/i);
      const declaring = scssFiles(src)
         .filter(f => /--inet-composer-overlay-(stroke|halo)\s*:/.test(readFileSync(f, "utf-8")));
      expect(declaring.map(f => relative(src, f).split("\\").join("/"))).toEqual(["scss/_composer-overlay.scss"]);
   });

   it("twoTonesAtLeastNineToOneApart", () => {
      expect(contrast(tone("stroke"), tone("halo"))).toBeGreaterThanOrEqual(9);
   });

   it("notInTheRuntimeMixinOrTheLightPins", () => {
      const variables = read(__dirname, "_variables.scss");
      expect(block(variables, /@mixin\s+inet-runtime-tokens\b/)).not.toContain("composer-overlay");
      const pins = variables.split(":where(:root:not(.custom-theme)) :where(#{$dashboard-content}").slice(1)
         .map(rest => block("x" + rest, /x/));
      expect(pins.length).toBeGreaterThanOrEqual(2);
      pins.forEach(pin => expect(pin).not.toContain("composer-overlay"));
      expect(read(__dirname, "_shell-dark.scss")).not.toContain("composer-overlay");
   });

   it("eachTokenDeclaredExactlyOnceAndNeverPerShell", () => {
      const all = scssFiles(src).map(f => readFileSync(f, "utf-8")).join("\n");

      for(const name of ["stroke", "halo", "band"]) {
         expect(all.match(new RegExp(`--inet-composer-overlay-${name}\\s*:`, "g")) || []).toHaveLength(1);
      }

      // no per-shell value: no .shell-dark selector (comments may name _shell-dark.scss)
      expect(overlay()).not.toMatch(/\.shell-dark\b/);
   });

   it("noPaneScopedSelectionBoxSelector", () => {
      // a descendant selector such as .vs-pane .selection-box would also restyle the chart brush and
      // the table cell marquees drawn inside the pane (C1); the composer marquee has its own class.
      // Limits: this is a flat text match that stops at { ; , so it catches one-line selectors like
      // ".vs-pane .selection-box" or ".layout-pane > div .selection-box" but NOT nested SCSS (a
      // .selection-box rule inside a .vs-pane block), :is(.vs-pane, ...) .selection-box, or
      // #{$dashboard-content} interpolation; the dark shell's existing .selection-box rules
      // (_shell-dark.scss:563, :738) pass only because of that. Review covers those forms.
      const offenders = scssFiles(src).filter(f =>
         /(\.vs-pane|\.layout-pane|vs-wizard-pane|\.scroll-container)[^{};,]*\.selection-box/.test(readFileSync(f, "utf-8")));
      expect(offenders.map(f => relative(src, f))).toEqual([]);
   });

   it("blockThrowsOnAMissingAnchor", () => {
      expect(() => block("a { b }", /@mixin\s+no-such-mixin\b/)).toThrow(/anchor not found/);
   });

   it("globalImportsThePartial", () => {
      expect(read(src, "global.scss")).toContain("@import './scss/composer-overlay';");
   });
});

describe("composer overlay consumers", () => {
   const app = join(src, "app");
   const container = () => read(app, "composer/gui/vs/editor/editable-object-container.component.scss");
   const moving = () => read(__dirname, "internal/_moving-resize.scss");
   // the declarations of the first rule whose selector line matches
   const rule = (text: string, selector: RegExp) => {
      const b = block(text, selector);
      return b.slice(b.indexOf("{") + 1, b.lastIndexOf("}"));
   };
   const HALO = /background-color:\s*var\(--inet-composer-overlay-halo,\s*var\(--inet-shell-surface-default\)\)\s*!important/;
   const STROKE = /border:\s*1px solid var\(--inet-composer-overlay-stroke,\s*var\(--inet-composer-selected-outline\)\)/;

   it("handlesUseTheTwoTonesWithTodaysFallback", () => {
      const handle = rule(container(), /^\.handle\.bg-primary\s*\{/m);
      expect(handle).toMatch(HALO);
      expect(handle).toMatch(STROKE);
      const move = rule(container(), /^\.composer-object__move-handle\s*\{/m);
      expect(move).toMatch(/background:\s*var\(--inet-composer-overlay-halo,\s*var\(--inet-shell-surface-default\)\)/);
      expect(move).toMatch(/border:\s*1px solid var\(--inet-composer-overlay-stroke,\s*var\(--inet-default-border-color\)\)/);
   });

   // owner review 1, must-fix 1: the move icon has no colour of its own; on the white halo fill it must
   // take the stroke (the wizard pane is outside the content restore, so it inherited the dark shell's
   // light text); themed pages inherit as before
   // the glyph is an icon font colour from var(--inet-icon-color) on the icon class (global.css), so the
   // handle's own color never reaches it: the gated partial points --inet-icon-color at the stroke on the
   // three move handles (a themed page keeps its icon colour); live check: computed icon colour = stroke
   it("moveHandlesHaveAStrokeGlyph", () => {
      const glyph = rule(read(__dirname, "_composer-overlay.scss"),
         /^:root:where\(:not\(\.custom-theme\)\)\s+:is\(\.composer-object__move-handle,\s*\.layout-object__move-handle,\s*\.vs-wizard-object__move-handle\)\s*\{/m);
      expect(glyph).toMatch(/--inet-icon-color:\s*var\(--inet-composer-overlay-stroke\)/);
      // no dead color declarations left on the handles themselves
      expect(rule(container(), /^\.composer-object__move-handle\s*\{/m)).not.toMatch(/(^|\s)color:/);
      expect(rule(layout(), /^\.layout-object__move-handle\s*\{/m)).not.toMatch(/(^|\s)color:/);
      expect(rule(read(app, "vs-wizard/gui/objects/vs-wizard-object.component.scss"), /^\.vs-wizard-object__move-handle\s*\{/m))
         .not.toMatch(/(^|\s)color:/);
   });

   it("titleAndListCellHandlesAreTwoToneInTheComposerOnly", () => {
      // the viewer draws these handles too (vsobjects title, selection list cell): the two-tone rule is
      // scoped to the composer's object editor, and the unscoped rule keeps today's values
      const scoped = rule(moving(),
         /^\.object-editor\s+:is\(\.title-resize-handle,\s*\.selection-list-cell-handle\)\.bg-primary\s*\{/m);
      expect(scoped).toMatch(HALO);
      expect(scoped).toMatch(STROKE);
      // same specificity as the narrow title's transparent full-width drag target, so it must stay
      // before it (else that target paints an opaque bar over the title)
      expect(moving().search(/^\.object-editor\s+:is\(\.title-resize-handle/m))
         .toBeLessThan(moving().search(/^\.selected-cell > \.title-resize-handle\.resize-line/m));
      const viewer = rule(moving(), /^\.title-resize-handle\.bg-primary,\s*\r?\n\.selection-list-cell-handle\.bg-primary\s*\{/m);
      expect(viewer).toMatch(/background-color:\s*var\(--inet-shell-surface-default\)\s*!important/);
      expect(viewer).toMatch(/border:\s*1px solid var\(--inet-composer-selected-outline\)/);
   });

   const layout = () => read(app, "composer/gui/vs/layouts/layout-object.component.scss");
   const HALO_RING = /box-shadow:\s*0 0 0 2px var\(--inet-composer-overlay-halo,\s*transparent\)/;

   it("outlinesAreTwoToneAndKeepTheirStyle", () => {
      // canvas: a 2px stroke outline with a 2px halo under it, so dash and dot gaps show the halo
      const canvas = rule(container(), /^\.active \.object\s*\{/m);
      expect(canvas).toMatch(/outline:\s*2px dotted var\(--inet-composer-overlay-stroke,\s*var\(--inet-viz-assembly-outline\)\)/);
      expect(canvas).toMatch(HALO_RING);
      expect(canvas).not.toMatch(/outline-offset/);
      // legacy dotted, modern and dark dashed (C5)
      expect(rule(container(), /^\.active\.viz-modern \.object,\s*\r?\n\.active\.viz-dark \.object\s*\{/m).trim())
         .toBe("outline-style: dashed;");
      // layout pane: .object-container clips anything outside .object (overflow: hidden), so both tones
      // share the 2px band: a halo border with the dotted stroke outline drawn over it (offset -2px)
      // owner review 1, must-fix 2: on a themed page this computes exactly today's look, a 2px dotted
      // (dashed) border in the assembly outline colour and no visible outline: the band style comes from
      // a gated token, and the stroke outline falls back to transparent
      const lay = rule(layout(), /^\.active \.object\s*\{/m);
      expect(lay).toMatch(
         /border:\s*2px var\(--inet-composer-overlay-band,\s*dotted\) var\(--inet-composer-overlay-halo,\s*var\(--inet-viz-assembly-outline\)\)/);
      expect(lay).toMatch(/outline:\s*2px dotted var\(--inet-composer-overlay-stroke,\s*transparent\)/);
      expect(lay).toMatch(/outline-offset:\s*-2px/);
      expect(lay).not.toMatch(/box-shadow/);
      expect(layout()).toMatch(
         /\.active \.viz-dark \.object\s*\{\s*border-style:\s*var\(--inet-composer-overlay-band,\s*dashed\);\s*outline-style:\s*dashed;\s*\}/);
      expect(read(__dirname, "_composer-overlay.scss")).toMatch(
         /:root:where\(:not\(\.custom-theme\)\)\s*\{[^}]*--inet-composer-overlay-band:\s*solid;/);
      // a selection-container child's outline sits inside its box (offset -2px) in a scroller that clips:
      // no outward halo there
      // .container-child and .active sit on the same element, so the rule must be as specific as
      // .active .object and come after it to win
      expect(rule(container(), /^\.active\.container-child > \.object\s*\{/m)).toMatch(/box-shadow:\s*none/);
      expect(container().search(/^\.active\.container-child > \.object\s*\{/m))
         .toBeGreaterThan(container().search(/^\.active \.object\s*\{/m));
   });

   it("outlineStatesDontStack", () => {
      // search result and drop zone draw a solid 2px outline over the same 2px band, which hides the halo
      expect(rule(container(), /^\.search-result\s*\{/m)).toMatch(/outline:\s*2px solid !important/);
      expect(rule(read(__dirname, "_themeable.scss"), /^\.bd-highlight-med\s*\{/m))
         .toMatch(/outline:\s*2px solid var\(--inet-drag-drop-highlight-color\) !important/);
      // no leftover rule for a layout search result (its border never won over the selection)
      expect(layout()).not.toContain(".active .object.search-result");
   });

   it("wizardHandlesAreTwoTone", () => {
      const wizard = read(app, "vs-wizard/gui/objects/vs-wizard-object.component.scss");
      const handle = rule(wizard, /^\.wizard-object-container \.handle\.bg-primary\s*\{/m);
      // themed pages keep today's solid primary handle (.bg-primary, no border)
      expect(handle).toMatch(
         /background-color:\s*var\(--inet-composer-overlay-halo,\s*var\(--inet-primary-color\)\)\s*!important/);
      expect(handle).toMatch(/border:\s*1px solid var\(--inet-composer-overlay-stroke,\s*transparent\)/);
      const move = rule(wizard, /^\.vs-wizard-object__move-handle\s*\{/m);
      expect(move).toMatch(/border:\s*1px solid var\(--inet-composer-overlay-stroke,\s*var\(--inet-default-border-color\)\)/);
      expect(move).toMatch(/background:\s*var\(--inet-composer-overlay-halo,\s*var\(--inet-shell-surface-default\)\)/);
      // the object border is PR14's and stays as it is
      expect(wizard.replace(/\r\n/g, "\n")).toContain(
         "  .object-border {\n    border: 1px solid transparent;\n\n    &:hover, &.selected-bd {\n" +
         "      border: 1px solid #e5e5e5 !important;\n    }\n\n    &.def-bd {\n      border: 1px solid #f5f5f5;\n    }\n  }");
   });

   it("layoutHandlesAreTwoTone", () => {
      // the layout pane's own resize and move handles (layout-object.component.html:51-63); themed pages
      // keep today's solid primary handle and the earlier move handle
      const handle = rule(layout(), /^\.resize-handle\.bg-primary\s*\{/m);
      expect(handle).toMatch(
         /background-color:\s*var\(--inet-composer-overlay-halo,\s*var\(--inet-primary-color\)\)\s*!important/);
      expect(handle).toMatch(/border:\s*1px solid var\(--inet-composer-overlay-stroke,\s*transparent\)/);
      // the canvas size: the AI assistant bundle injects an unscoped .resize-handle.top/left 6px rule
      expect(handle).toMatch(/width:\s*8px/);
      expect(handle).toMatch(/height:\s*8px/);
      const move = rule(layout(), /^\.layout-object__move-handle\s*\{/m);
      expect(move).toMatch(/border:\s*1px solid var\(--inet-composer-overlay-stroke,\s*var\(--inet-default-border-color\)\)/);
      expect(move).toMatch(/background:\s*var\(--inet-composer-overlay-halo,\s*var\(--inet-shell-surface-default\)\)/);
   });

   // owner review 2, must-fix 1: the AI assistant bundle's unscoped .resize-handle.top/.bottom {left:0;
   // right:0} and .left/.right {top:0; bottom:0} pull the right and bottom handles onto the top-left
   // corner (their rings stacked into a "four-pane" mark); the corner rules set only their own edges,
   // so the opposite offsets are reset here. Stacking as on the canvas: the move handle comes first in
   // the template, so the resize handles paint above it.
   it("layoutHandlesStayOnTheirCornersAboveTheMoveHandle", () => {
      expect(rule(layout(), /^\.resize-handle\.bg-primary\.right\s*\{/m)).toMatch(/left:\s*auto/);
      expect(rule(layout(), /^\.resize-handle\.bg-primary\.bottom\s*\{/m)).toMatch(/top:\s*auto/);
      const html = read(app, "composer/gui/vs/layouts/layout-object.component.html");
      expect(html.indexOf("layout-object__move-handle")).toBeGreaterThan(0);
      expect(html.indexOf("layout-object__move-handle")).toBeLessThan(html.indexOf("resize-handle bg-primary top left"));
   });

   const PANES = [
      "composer/gui/vs/editor/viewsheet-pane.component.html",
      "composer/gui/vs/layouts/layout-pane.component.html",
      "vs-wizard/gui/wizard-pane/vs-wizard-pane.component.html",
   ];

   it("composerMarqueeIsTwoToneOnTheAuthoringPanesOnly", () => {
      // behind the theme gate; !important beats .selection-box's light rule (_themeable.scss), the dark
      // rule and the dark shell's content restore (:root.shell-dark :is(.vs-pane, ...) .selection-box,
      // specificity 0,5,0) without a specificity race
      const marquee = rule(read(__dirname, "_composer-overlay.scss"),
         /^:root:where\(:not\(\.custom-theme\)\)\s+\.composer-selection-box\s*\{/m);
      expect(marquee).toMatch(
         /box-shadow:\s*inset 0 0 0 1px var\(--inet-composer-overlay-stroke\),\s*inset 0 0 0 2px var\(--inet-composer-overlay-halo\)\s*!important/);
      // owner review 1: one absolute fill in every pane, the stroke colour at 6%
      expect(marquee).toMatch(/background-color:\s*rgba\(18, 60, 68, 0\.06\)\s*!important/);
      expect(read(__dirname, "_composer-overlay.scss")).toMatch(/--inet-composer-overlay-stroke:\s*#123C44;/i);
      // only one marquee may draw: the dashboard pane's own directive ignores drags that start in the
      // layout pane (which draws its own), else two translucent boxes stack
      expect(read(app, PANES[0])).toMatch(/selectionBoxBannedSelector="[^"]*\blayout-pane\b[^"]*"/);
      PANES.forEach(p => expect(read(app, p)).toContain('selectionBoxClass="selection-box composer-selection-box"'));
      // no other template passes the class: the chart brush and cell marquees keep theirs (C1)
      const html = (dir: string): string[] => readdirSync(dir).flatMap(n => {
         const p = join(dir, n);
         return statSync(p).isDirectory() ? html(p) : p.endsWith(".html") ? [p] : [];
      });
      const users = html(app).filter(f => readFileSync(f, "utf-8").includes("composer-selection-box"))
         .map(f => relative(app, f).split("\\").join("/")).sort();
      expect(users).toEqual([...PANES].sort());
   });
});
