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
