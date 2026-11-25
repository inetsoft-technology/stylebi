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

   if(i < 0) {
      return "";
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

   it("titleAndListCellHandlesAreTwoToneInTheComposerOnly", () => {
      // the viewer draws these handles too (vsobjects title, selection list cell): the two-tone rule is
      // scoped to the composer's object editor, and the unscoped rule keeps today's values
      const scoped = rule(moving(),
         /^\.object-editor\s+:is\(\.title-resize-handle,\s*\.selection-list-cell-handle\)\.bg-primary\s*\{/m);
      expect(scoped).toMatch(HALO);
      expect(scoped).toMatch(STROKE);
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
      // layout pane: the stroke on its 2px dotted border, the halo as a ring outside it
      const lay = rule(layout(), /^\.active \.object\s*\{/m);
      expect(lay).toMatch(/border:\s*2px dotted var\(--inet-composer-overlay-stroke,\s*var\(--inet-viz-assembly-outline\)\)/);
      expect(lay).toMatch(HALO_RING);
   });

   it("outlineStatesDontStack", () => {
      // search result and drop zone draw a solid 2px outline over the same 2px band, which hides the halo
      expect(rule(container(), /^\.search-result\s*\{/m)).toMatch(/outline:\s*2px solid !important/);
      expect(rule(read(__dirname, "_themeable.scss"), /^\.bd-highlight-med\s*\{/m))
         .toMatch(/outline:\s*2px solid var\(--inet-drag-drop-highlight-color\) !important/);
      // the layout search result replaces the border, so it drops the halo ring
      expect(rule(layout(), /^\.active \.object\.search-result\s*\{/m)).toMatch(/box-shadow:\s*none/);
   });
});
