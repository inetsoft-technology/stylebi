import { readFileSync } from "fs";
import { dirname, join } from "path";
import { fileURLToPath } from "url";

const __dirname = dirname(fileURLToPath(import.meta.url));
const app = join(__dirname, "..", "app");
const read = (...p: string[]) => readFileSync(join(...p), "utf-8");

describe("assembly outline token", () => {
   const tokens = read(__dirname, "_viz-tokens.scss");

   it("modernMapsToTheNewOutline", () => {
      expect(tokens).toMatch(/--inet-viz-assembly-outline-modern:\s*#2F7F93/i);
      expect(tokens).toMatch(
         /\.viz-modern,\s*\[data-viz-theme="v2"\]\s*\{[^}]*--inet-viz-assembly-outline:\s*var\(--inet-viz-assembly-outline-modern\)/);
      const shared = tokens.match(/\.viz-modern,\s*\.viz-shell,\s*\[data-viz-theme="v2"\]\s*\{[^}]*\}/)[0];
      expect(shared).not.toContain("--inet-viz-assembly-outline");
   });

   it("vizShellKeepsTheOldOutlineForUnmarkedAssemblies", () => {
      const shell = tokens.match(/\.viz-shell\s*\{[^}]*\}/)[0];
      expect(shell).toMatch(/--inet-viz-assembly-outline:\s*var\(--inet-viz-selected-border-modern\)/);
      expect(tokens.indexOf(shell)).toBeLessThan(
         tokens.search(/\.viz-modern,\s*\[data-viz-theme="v2"\]\s*\{/));
   });

   it("rootDefaultFollowsTheSelectedBorderToken", () => {
      const root = tokens.match(/:root\s*\{[\s\S]*?\}/)[0];
      expect(root).toMatch(/--inet-viz-selected-border:\s*var\(--inet-primary-color\)/);
      expect(root).toMatch(/--inet-viz-assembly-outline:\s*var\(--inet-viz-selected-border\)/);
   });

   it("darkMapsToTheDarkOutline", () => {
      expect(tokens).toMatch(/--inet-viz-assembly-outline-dark:\s*#2DD4BF/i);
      expect(tokens).toMatch(
         /\.viz-dark\s*\{[^}]*--inet-viz-assembly-outline:\s*var\(--inet-viz-assembly-outline-dark\)/);
      const shared = tokens.match(/\.viz-dark,\s*\.viz-shell-dark\s*\{[^}]*\}/)[0];
      expect(shared).not.toContain("--inet-viz-assembly-outline");
   });

   it("vizShellDarkKeepsTheOldDarkOutlineForUnmarkedAssemblies", () => {
      const shellDark = tokens.match(/\.viz-shell-dark\s*\{[^}]*\}/)[0];
      expect(shellDark).toMatch(/--inet-viz-assembly-outline:\s*var\(--inet-viz-selected-border-dark\)/);
      const dark = tokens.search(/\.viz-dark\s*\{/);
      expect(tokens.indexOf(shellDark)).toBeLessThan(dark);
      expect(dark).toBeGreaterThan(tokens.search(/\.viz-modern,\s*\[data-viz-theme="v2"\]\s*\{/));
   });

   it("noOutlineConsumerReadsTheSelectedBorderToken", () => {
      const themeable = read(__dirname, "_themeable.scss");
      const cell = themeable.match(/\.bd-selected-cell\s*\{[^}]*\}/)[0];
      const assembly = themeable.match(
         /\.viz-modern\.bd-selected-assembly,\s*\.viz-dark\.bd-selected-assembly\s*\{[^}]*\}/)[0];
      const files = [
         cell, assembly,
         read(app, "composer/gui/vs/editor/editable-object-container.component.scss"),
         read(app, "composer/gui/vs/layouts/layout-object.component.scss"),
         read(app, "vsobjects/objects/annotation/vs-annotation.component.scss"),
         read(app, "graph/model/chart-tool.ts")
      ];
      for(const src of files) {
         expect(src).not.toMatch(/--inet-viz-selected-border(?!-)/);
         expect(src).toContain("--inet-viz-assembly-outline");
      }
   });
});
