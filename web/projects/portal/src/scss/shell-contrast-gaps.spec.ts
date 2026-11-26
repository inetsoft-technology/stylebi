/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

/**
 * Guards the contrast gaps left after the shell palette (Bug #81984): the dark shell's secondary
 * text on a selected row, and a custom-themed page's focus ring and danger hover fill. Sits next
 * to shell-palette.spec.ts, which guards the values these rules must not change.
 */
import { execFileSync } from "node:child_process";
import { execPath } from "node:process";
import { readdirSync, readFileSync, statSync } from "fs";
import { dirname, join } from "path";
import { fileURLToPath } from "url";

const __dirname = dirname(fileURLToPath(import.meta.url));
const src = join(__dirname, "..");
const read = (relPath: string): string => readFileSync(join(src, relPath), "utf-8");
const stripComments = (css: string): string =>
   css.replace(/\/\*[\s\S]*?\*\//g, "").replace(/^\s*\/\/.*$/gm, "");

// global.scss compiled, without comments (see shell-palette.spec.ts for why a child process)
let compiledCss: string;
const compiledGlobal = (): string => {
   if(compiledCss == null) {
      const web = join(__dirname, "..", "..", "..", "..");
      compiledCss = execFileSync(execPath, [
         join(web, "node_modules", "sass", "sass.js"),
         `--load-path=${web}`,
         `--load-path=${join(web, "node_modules")}`,
         "--no-source-map",
         "--quiet",
         join(__dirname, "..", "global.scss"),
      ], { encoding: "utf-8", maxBuffer: 64 * 1024 * 1024 }).replace(/\/\*[\s\S]*?\*\//g, "");
   }

   return compiledCss;
};

// the declarations of a flat rule body, as property -> value
const declarationsOf = (body: string): Map<string, string> => {
   const result = new Map<string, string>();

   for(const m of body.matchAll(/(?:^|[;{\s])(-{0,2}[\w-]+)\s*:\s*([^;]*?)\s*(?:;|$)/g)) {
      result.set(m[1], m[2].replace(/\s+/g, " "));
   }

   return result;
};

// every plain rule of the compiled stylesheet, at any nesting depth, as selector text and body
const compiledRules = (): { selector: string, body: string }[] =>
   [...compiledGlobal().matchAll(/([^{}]+)\{([^{}]*)\}/g)].map(m => ({
      selector: m[1].replace(/\s+/g, " ").trim(),
      body: m[2],
   }));

const MIX_GUARD = "@supports (color: color-mix(in srgb, red 50%, black))";
const MUTED_CHAIN = [
   "--inet-text-muted-color",
   "--inet-shell-text-muted",
   "--inet-table-heading-text-color",
   "--inet-ui-neutral-color",
   "--inet-button-default-text-color",
   "--bs-secondary-color",
];

// the part of a source file from `marker` to the end of the mixin or rule that opens there; the
// braces of a Sass interpolation, #{...}, are not blocks
const section = (text: string, marker: string): string => {
   const open = text.indexOf(marker);
   expect(open, `no ${marker}`).toBeGreaterThan(-1);
   const code = stripComments(text);
   const start = code.indexOf(marker);
   let depth = 0;

   for(let i = code.indexOf("{", start + marker.length - 1); i < code.length; i++) {
      if(code[i] === "#" && code[i + 1] === "{") {
         i = code.indexOf("}", i);
         continue;
      }

      depth += code[i] === "{" ? 1 : code[i] === "}" ? -1 : 0;

      if(depth === 0) {
         return code.slice(start, i + 1);
      }
   }

   throw new Error(`unterminated ${marker}`);
};

// every .scss file under a directory
const scssFiles = (dir: string): string[] =>
   readdirSync(dir).flatMap(f => {
      const p = join(dir, f);
      return statSync(p).isDirectory() ? scssFiles(p) : f.endsWith(".scss") ? [p] : [];
   });

// The full selector (nesting resolved, `&` replaced by the parent's) of every rule in a component
// stylesheet whose own declarations set a background from a selected-background token.
const SELECTED_FILL =
   /background(?:-color)?\s*:\s*var\(--inet-(?:(?:shell|list|table)-selected-bg-color|selected-item-bg-color)/;

function selectedFillSelectors(scss: string): string[] {
   const stack: string[] = [];
   const result: string[] = [];
   let head = "";
   let body = "";
   const flush = () => {
      if(stack.length > 0 && SELECTED_FILL.test(body)) {
         result.push(stack[stack.length - 1]);
      }
   };

   for(const c of stripComments(scss)) {
      if(c === "{") {
         flush();
         body = "";
         const parent = stack.length > 0 ? stack[stack.length - 1] : "";
         const resolved = head.split(",").map(s => s.trim()).filter(Boolean)
            .map(p => p.includes("&") ? p.replace(/&/g, parent) : parent ? `${parent} ${p}` : p);
         stack.push(resolved.join(", "));
         head = "";
      }
      else if(c === "}") {
         flush();
         stack.pop();
         body = "";
         head = "";
      }
      else if(c === ";") {
         head = "";
         body += c;
      }
      else {
         head += c;
         body += c;
      }
   }

   return result;
}

describe("themed pages: ring and danger hover derived from the theme (Bug #81984)", () => {
   const variables = () => read("scss/_variables.scss");
   const runtimeTokens = () => declarationsOf(section(variables(), "@mixin inet-runtime-tokens"));

   it("declares a solid ring and a danger hover fill from the theme's own colours, inside the guard", () => {
      const v = variables();
      const guard = v.indexOf(MIX_GUARD);

      expect(guard, "no color-mix @supports guard").toBeGreaterThan(-1);

      const rule = section(v.slice(guard), ":root:where(.custom-theme)");
      const decl = declarationsOf(rule);

      expect(decl.get("--inet-focus-ring-color"))
         .toBe("color-mix(in srgb, var(--inet-primary-color) 80%, black)");
      expect(decl.get("--inet-danger-hover-bg-color"))
         .toBe("color-mix(in srgb, var(--inet-danger-color) 85%, black)");
      // the rule is the guard's first and only content
      expect(v.slice(guard + MIX_GUARD.length).replace(/\s+/g, " ").startsWith(" { :root:where(.custom-theme) {"))
         .toBe(true);
   });

   it("comes after the :root and shell-state blocks, at the theme's :root specificity", () => {
      const v = variables();
      const at = v.indexOf(":root:where(.custom-theme) {");

      expect(at).toBeGreaterThan(v.indexOf(":root:where(:not(.custom-theme)) {"));
      expect(at).toBeGreaterThan(v.indexOf("  @include inet-runtime-tokens;\n}"));
      // :where() adds nothing, so the rule is (0,1,0), the same as a theme's own `:root`
      expect(":root:where(.custom-theme)".replace(/:where\([^)]*\)/g, "")).toBe(":root");
   });

   it("compiles to a guarded rule", () => {
      const css = compiledGlobal();
      const at = css.indexOf(":root:where(.custom-theme)");

      expect(at).toBeGreaterThan(-1);
      expect(css.slice(css.lastIndexOf("@supports", at), at)).toContain("color-mix(in srgb, red 50%, black)");
      expect(css.slice(css.lastIndexOf("}", at), at)).not.toMatch(/\}\s*\}/);
   });

   it("leaves the un-themed values alone", () => {
      const v = variables();
      const state = declarationsOf(section(v, "@mixin inet-shell-state-tokens"));

      expect(state.get("--inet-focus-ring-color")).toBe("#B4610F");
      expect(state.get("--inet-danger-hover-bg-color")).toBe("#B03A3A");
      expect(runtimeTokens().get("--inet-focus-ring-color")).toBe("rgba(217, 122, 26, 0.28)");
      expect(runtimeTokens().has("--inet-danger-hover-bg-color")).toBe(false);
   });

   it("keeps the un-themed danger hover rule and adds a separate guarded themed one", () => {
      const o = stripComments(read("scss/_bootstrap-override.scss"));
      const unthemed = o.indexOf(":where(:root:not(.custom-theme)) .btn.btn-danger#{$outside-content} {");
      const themedGuard = o.indexOf(MIX_GUARD, unthemed);
      const themed = o.indexOf(":where(:root.custom-theme) .btn.btn-danger#{$outside-content} {", unthemed);

      expect(unthemed).toBeGreaterThan(o.indexOf(".btn.btn-danger {"));
      // the themed rule is the guard's only content; the un-themed rule is outside any guard
      expect(themedGuard).toBeGreaterThan(unthemed);
      expect(o.slice(themedGuard + MIX_GUARD.length, themed).trim()).toBe("{");
      expect(o.slice(unthemed, themedGuard)).not.toContain("@supports");

      const unthemedRule = section(o, ":where(:root:not(.custom-theme)) .btn.btn-danger#{$outside-content} {");
      const themedRule = section(o, ":where(:root.custom-theme) .btn.btn-danger#{$outside-content} {");

      for(const rule of [unthemedRule, themedRule]) {
         expect(rule).toMatch(/color:\s*#FFFFFF;/);
         expect(rule).toMatch(/background-color:\s*var\(--inet-danger-hover-bg-color\);/);
      }

      // the same state list, so the two rules differ only in the kind of page they apply to
      const states = (rule: string) => rule.slice(rule.indexOf("&:hover"), rule.indexOf("{", rule.indexOf("&:hover")))
         .replace(/\s+/g, " ");
      expect(states(themedRule)).toBe(states(unthemedRule));
   });

   it("compiles the themed danger rule to the white label on the derived fill, content excluded", () => {
      const rule = compiledRules().find(r => r.selector.startsWith(":where(:root.custom-theme) .btn.btn-danger")
         && r.selector.includes(":hover"));

      expect(rule, "themed danger hover rule").toBeTruthy();
      expect(rule.selector).toContain(":where(:not(:is(");
      expect(declarationsOf(rule.body).get("color")).toBe("#FFFFFF");
      expect(declarationsOf(rule.body).get("background-color")).toBe("var(--inet-danger-hover-bg-color)");
   });

   it("keeps the chart canvas selection fill off the ring token", () => {
      const themeable = stripComments(read("scss/_themeable.scss"));
      const body = section(themeable, ".viz-modern .chart-object-canvas {");

      expect(body).toMatch(/color:\s*var\(--inet-focus-ring-canvas-color\);/);
      expect(body).not.toContain("--inet-focus-ring-color");
      // a plain rgba: this canvas's fillStyle drops a color-mix() value (see _viz-tokens.scss)
      expect(runtimeTokens().get("--inet-focus-ring-canvas-color")).toBe("rgba(217, 122, 26, 0.28)");
   });
});

describe("dark shell: secondary text on a selected row (Bug #81984)", () => {
   const dark = () => stripComments(read("scss/_shell-dark.scss"));
   const rowClasses = (): string[] => {
      const m = dark().match(/\$selected-rows:\s*([\s\S]*?);/);
      expect(m, "no $selected-rows list").not.toBeNull();
      return [...[...m[1].matchAll(/"([^"]*)"/g)].map(s => s[1]).join("").matchAll(/\.([\w-]+)/g)].map(c => c[1]);
   };

   it("re-declares the whole muted chain as the default text colour on those rows", () => {
      const rule = section(dark(), ":root.shell-dark :is(#{$selected-rows})#{$outside-content}");
      const decl = declarationsOf(rule);

      for(const name of MUTED_CHAIN) {
         expect(decl.get(name), name).toBe("var(--inet-text-color)");
      }

      expect(decl.size).toBe(MUTED_CHAIN.length);
   });

   it("compiles into the dark shell only, with dashboard content excluded", () => {
      const rules = compiledRules()
         .filter(r => declarationsOf(r.body).get("--bs-secondary-color") === "var(--inet-text-color)");

      expect(rules.length).toBe(1);
      expect(rules[0].selector.startsWith(":root.shell-dark :is(.selected, ")).toBe(true);
      expect(rules[0].selector).toMatch(/:where\(:not\(:is\(.*\) \*\)\)$/);
      // the light shell's muted text is never re-pointed
      expect(compiledRules().filter(r => !r.selector.startsWith(":root.shell-dark")
         && declarationsOf(r.body).get("--inet-text-muted-color") === "var(--inet-text-color)")).toEqual([]);
   });

   it("leaves the dark palette values themselves alone", () => {
      expect(dark()).toMatch(/\$dark-text-muted:\s*#ADA8B4;/i);
      expect(dark()).toMatch(/\$dark-text-default:\s*#CAC4D0;/i);
      expect(dark()).toMatch(/\$dark-selected-bg:\s*#4A4750;/i);
   });

   // every class of the list is named somewhere in the app: a rename leaves no dead entry
   it("names only classes that exist in the app", () => {
      const walk = (dir: string): string[] => readdirSync(dir).flatMap(f => {
         const p = join(dir, f);
         return statSync(p).isDirectory() ? walk(p)
            : /\.(scss|html|ts)$/.test(f) && !/\.spec\./.test(f) ? [p] : [];
      });
      // .table-active is Bootstrap's, restyled in the shared scss
      const all = [...walk(join(src, "app")), ...walk(join(src, "scss"))]
         .map(f => readFileSync(f, "utf-8")).join("\n");

      expect([...new Set(rowClasses())].filter(c => !all.includes(c))).toEqual([]);
   });

   // A component rule that fills a row from a selected-background token and is not in $selected-rows
   // leaves that row's secondary text at the low-contrast muted colour: add its class to the list, or
   // here with the reason it needs none (it holds no secondary text, or is not shown in the shell).
   const NO_SECONDARY_TEXT: Record<string, string> = {
      "app/binding/editor/chart/aesthetic/shape-pane.scss: .sp-btn:active:not(:disabled)":
         "a button's pressed state, not a selected row",
      "app/binding/editor/chart/aesthetic/static-shape-pane.component.scss: .sp-cell:active":
         "a tile's pressed state, not a selected row",
      "app/binding/editor/chart/aesthetic/static-shape-pane.component.scss: .sp-cell--selected, .sp-cell--selected:hover, .sp-cell--selected:active":
         "a shape glyph tile: it holds no secondary text",
      "app/composer/gui/ws/editor/ws-details-pane.component.scss: .bg-button-toggled":
         "a toggled toolbar button, not a row; its label is outside this fix's scope",
   };

   it("covers every component rule that fills a row from a selected-background token", () => {
      const covered = new Set(rowClasses());
      const uncovered: string[] = [];

      for(const file of scssFiles(join(src, "app"))) {
         for(const sel of selectedFillSelectors(readFileSync(file, "utf-8"))) {
            const classes = [...sel.matchAll(/\.([\w-]+)/g)].map(m => m[1]);
            const key = `${file.slice(src.length + 1).replace(/\\/g, "/")}: ${sel}`;

            if(!classes.some(c => covered.has(c)) && !(key in NO_SECONDARY_TEXT)) {
               uncovered.push(key);
            }
         }
      }

      expect([...new Set(uncovered)].sort()).toEqual([]);
   });
});
