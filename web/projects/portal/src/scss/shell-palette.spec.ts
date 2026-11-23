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
 * Guards the shell palette (Feature #80673): the shell's own state values live in a shell-only
 * token set, dashboard content keeps today's values, and custom-themed pages keep today's colours.
 * Anchored on selectors and token names, not on computed colours.
 */
import { execFileSync } from "node:child_process";
import { execPath } from "node:process";
import { readFileSync } from "fs";
import { dirname, join } from "path";
import { fileURLToPath } from "url";

const __dirname = dirname(fileURLToPath(import.meta.url));

// a file under web/projects/portal/src
const read = (relPath: string): string => readFileSync(join(__dirname, "..", relPath), "utf-8");

// a file under the repository root
const readRepo = (relPath: string): string =>
   readFileSync(join(__dirname, "..", "..", "..", "..", "..", relPath), "utf-8");

// the top-level block that opens with `selector {`, up to its closing brace at line start
const block = (src: string, selector: string): string => {
   const escaped = selector.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
   const m = src.match(new RegExp(`^${escaped}\\s*\\{[\\s\\S]*?^\\}`, "m"));
   expect(m, `no top-level ${selector} block`).not.toBeNull();
   return m[0];
};

// custom property declarations in a block, as name -> value
const declarations = (css: string): Map<string, string> => {
   const result = new Map<string, string>();

   for(const m of css.matchAll(/^\s*(--[\w-]+)\s*:\s*([^;]*?)\s*;/gm)) {
      result.set(m[1], m[2]);
   }

   return result;
};

const variables = () => read("scss/_variables.scss");
const runtimeTokens = () => declarations(block(variables(), "@mixin inet-runtime-tokens"));
// every token set emitted on :root: the runtime tokens, the Bootstrap bridge and the legacy viz state
const rootTokens = () => new Map([
   ...runtimeTokens(),
   ...declarations(block(read("scss/_bootstrap-override.scss"), "@mixin inet-bootstrap-bridge")),
   ...declarations(block(read("scss/_viz-tokens.scss"), "@mixin inet-viz-legacy-state-tokens")),
]);

// global.scss compiled, without comments, once per run. Compiled by the sass command line in a node
// child process: the project's test environment is jsdom, where sass's JS API refuses to compile (it
// checks process.release.name on the global), and the global process there has no execPath, so
// node:process supplies it.
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

// the bases and every token in `tokens` whose value reads one of them through var(), transitively
const chainOf = (tokens: Map<string, string>, bases: string[]): Set<string> => {
   const chain = new Set(bases);
   let grew = true;

   while(grew) {
      grew = false;

      for(const [name, value] of tokens) {
         const refs = [...value.matchAll(/var\((--[\w-]+)/g)].map(r => r[1]);

         if(!chain.has(name) && refs.some(r => chain.has(r))) {
            chain.add(name);
            grew = true;
         }
      }
   }

   return chain;
};

// the base tokens PR7 gives a shell-only value, and every runtime token that derives from them
const PR7_BASE_TOKENS = [
   "--inet-focus-ring-color",
   "--inet-shell-selected-bg-color",
   "--inet-text-muted-color",
   "--inet-shell-surface-hover",
];
const PR7_CHAIN = [
   ...PR7_BASE_TOKENS,
   // ring
   "--inet-focus-ring",
   // selected
   "--inet-selected-item-bg-color",
   "--inet-table-selected-bg-color",
   "--inet-list-selected-bg-color",
   // muted text
   "--inet-shell-text-muted",
   "--inet-table-heading-text-color",
   "--inet-ui-neutral-color",
   "--inet-button-default-text-color",
   // hover
   "--inet-ui-neutral-hover-bg-color",
   "--inet-hover-primary-bg-color",
   "--inet-hover-secondary-bg-color",
   "--inet-table-hover-bg-color",
   "--inet-list-hover-bg-color",
   "--inet-button-secondary-hover-bg-color",
   "--inet-button-default-hover-bg-color",
   "--inet-button-light-hover-bg-color",
];
// the same chain in the Bootstrap bridge (_bootstrap-override.scss), also declared on :root
const BRIDGE_CHAIN = [
   "--bs-secondary-color",
   "--bs-dropdown-link-hover-bg",
   "--bs-list-group-action-hover-bg",
   "--bs-list-group-active-bg",
];
// and in the legacy viz state tokens (_viz-tokens.scss), which a viz scope replaces
const VIZ_CHAIN = [
   "--inet-viz-selected-bg",
   "--inet-viz-sorted-color",
];
const CONTENT_CHAIN = [...PR7_CHAIN, ...BRIDGE_CHAIN];

const CONTENT_RULE = ":where(:root:not(.custom-theme)) " +
   ":where(#{$dashboard-content}, #{$content-popup}, #{$annotation-tooltip})" +
   ":where(:not(.dropdown-datepicker.viz-dark))";
// the same elements as the dark shell's legacy viz rule, which leaves the annotation tooltips out
const VIZ_RULE = ":where(:root:not(.custom-theme)) " +
   ":where(#{$dashboard-content}, #{$content-popup})" +
   ":where(:not(#{$viz-scopes}):not(:is(#{$viz-scopes}) *))";

describe("shell-only state tokens", () => {
   it("are a mixin of their own", () => {
      expect(variables()).toMatch(/^@mixin inet-shell-state-tokens\s*\{/m);
   });

   it("are included on the root of an unthemed page, after the runtime tokens", () => {
      const src = variables();
      const runtime = block(src, ":root");
      const state = block(src, ":root:where(:not(.custom-theme))");

      expect(runtime).toMatch(/@include inet-runtime-tokens;/);
      expect(state).toMatch(/@include inet-shell-state-tokens;/);
      // same specificity as :root, so it must come later to win
      expect(src.indexOf(state)).toBeGreaterThan(src.indexOf(runtime));
   });

   it("keep :root's own specificity: neither 0,0,0 nor 0,2,0 (which would tie :root.shell-dark)", () => {
      const src = variables();

      expect(src).not.toMatch(/^:where\(:root:not\(\.custom-theme\)\)\s*\{/m);
      expect(src).not.toMatch(/^:root:not\(\.custom-theme\)\s*\{/m);
   });
});

describe("light dashboard content", () => {
   const contentRule = () => block(variables(), CONTENT_RULE);

   it("has $annotation-tooltip in _variables.scss with the rest of the content boundary", () => {
      expect(variables()).toMatch(/^\$annotation-tooltip:\s*"\.hidden__annotation-tooltip, \.vs-annotation__tooltip";/m);
      expect(read("scss/_shell-dark.scss")).not.toMatch(/^\$annotation-tooltip:/m);
   });

   it("lists every :root token that derives from a PR7 base token", () => {
      const chain = chainOf(rootTokens(), PR7_BASE_TOKENS);

      expect([...chain].sort()).toEqual([...CONTENT_CHAIN, ...VIZ_CHAIN].sort());
   });

   it("re-declares the PR7 chain with the :root tokens' own expressions", () => {
      const tokens = rootTokens();
      const declared = declarations(contentRule());

      for(const name of CONTENT_CHAIN) {
         expect(declared.get(name), name).toBe(tokens.get(name));
      }
   });

   it("re-declares only the PR7 chain, never the whole runtime token set", () => {
      const css = contentRule();

      expect([...declarations(css).keys()].sort()).toEqual([...CONTENT_CHAIN].sort());
      expect(css).not.toMatch(/@include inet-runtime-tokens/);
   });

   it("does not take the shell-only state tokens", () => {
      expect(contentRule()).not.toMatch(/@include inet-shell-state-tokens/);
   });

   it("is gated off on custom-themed pages and leaves the dark-marked calendar to its own block", () => {
      // the gate and the exclusion are in the selector the block helper matches
      expect(contentRule()).toContain(CONTENT_RULE);
      expect(CONTENT_RULE).toMatch(/^:where\(:root:not\(\.custom-theme\)\) /);
      expect(CONTENT_RULE).toMatch(/:where\(:not\(\.dropdown-datepicker\.viz-dark\)\)$/);
   });

   it("re-declares the scroll bar color, inherited from <html> where it is computed from the muted token", () => {
      const scrollbar = /^\s*scrollbar-color:\s*([^;]*?)\s*;/m;
      const root = block(read("scss/internal/_html-override.scss"), "html").match(scrollbar);
      const content = contentRule().match(scrollbar);

      expect(root).not.toBeNull();
      expect(root[1]).toMatch(/var\(--inet-text-muted-color\)/);
      expect(content, "no scrollbar-color in the content rule").not.toBeNull();
      expect(content[1]).toBe(root[1]);
   });

   it("re-declares the legacy viz state chain outside viz scopes only, which set their own", () => {
      const tokens = rootTokens();
      const declared = declarations(block(variables(), VIZ_RULE));

      expect([...declared.keys()].sort()).toEqual([...VIZ_CHAIN].sort());

      for(const name of VIZ_CHAIN) {
         expect(declared.get(name), name).toBe(tokens.get(name));
      }
   });

   it("has $viz-scopes in _variables.scss, shared with the dark shell's legacy viz rule", () => {
      expect(variables()).toMatch(/^\$viz-scopes:\s*"\.viz-modern, \.viz-dark, \.viz-shell, \.viz-shell-dark, \[data-viz-theme=\\"v2\\"\]";/m);
      expect(read("scss/_shell-dark.scss")).not.toMatch(/^\$viz-scopes:/m);
   });
});

describe("dark-marked calendar in the light shell", () => {
   const CAL_RULE = ":where(:root:not(.custom-theme):not(.shell-dark)) .dropdown-datepicker.viz-dark";
   const SCROLLBAR = /^\s*scrollbar-color:\s*([^;]*?)\s*;/m;
   // the values :root gave these base tokens before PR7 (284e8fcee4)
   const PRE_PR7_BASES = {
      "--inet-focus-ring-color": "rgba(217, 122, 26, 0.28)",
      "--inet-shell-selected-bg-color": "#E7E2D8",
      "--inet-text-muted-color": "#6A685F",
      "--inet-shell-surface-hover": "#ECE9E2",
   };
   const calRule = () => block(variables(), CAL_RULE);
   const calendarBlock = () => block(read("scss/_shell-dark.scss"), ".dropdown-datepicker.viz-dark");
   // every declaration in a block (custom properties and scrollbar-color), as name -> value
   const props = (css: string): Map<string, string> => {
      const result = declarations(css);
      const scrollbar = css.match(SCROLLBAR);

      if(scrollbar) {
         result.set("scrollbar-color", scrollbar[1]);
      }

      return result;
   };
   // the content rule's value of `name`, with Sass variables and var() references resolved from the
   // same rule (the content values are the pre-PR7 ones); the ring width is unchanged by PR7, so it stays
   const resolved = (name: string): string => {
      const content = props(block(variables(), CONTENT_RULE));
      const resolve = (value: string): string => value
         .replace(/#\{\$([\w-]+)\}/g, (_, v) =>
            variables().match(new RegExp(`^\\$${v}:\\s*([^;]+);`, "m"))[1])
         .replace(/var\((--[\w-]+)\)/g, (ref, token) =>
            token === "--inet-focus-ring-width" ? ref : resolve(content.get(token)));

      return resolve(content.get(name));
   };
   // the content rule's properties the calendar block does not declare itself
   const expectedPins = (): string[] => {
      const own = props(calendarBlock());
      return [...props(block(variables(), CONTENT_RULE)).keys()].filter(name => !own.has(name));
   };

   it("is gated off on themed pages and in the dark shell, where the calendar follows the shell", () => {
      // every rule in _variables.scss whose selector ends in the dark calendar's class, whatever its
      // gate: exactly one, and its selector is exactly the gated one
      const selectors = [...variables().matchAll(/^([^\r\n{]*\.dropdown-datepicker\.viz-dark)\s*\{/gm)]
         .map(m => m[1]);
      expect(selectors).toEqual([CAL_RULE]);
   });

   it("pins every content-rule property the calendar block does not re-declare, and no other", () => {
      const pins = expectedPins();

      // the calendar declares its own muted text and button hovers, never the ring, selected or scroll bar
      expect(pins).toContain("--inet-focus-ring-color");
      expect(pins).toContain("--inet-shell-selected-bg-color");
      expect(pins).toContain("--inet-ui-neutral-color");
      expect(pins).toContain("scrollbar-color");
      expect(pins).not.toContain("--inet-text-muted-color");
      expect([...props(calRule()).keys()].sort()).toEqual([...pins].sort());
   });

   it("pins each to its pre-PR7 value as a literal, since the calendar's own muted token is dark", () => {
      const pinned = props(calRule());

      for(const [base, value] of Object.entries(PRE_PR7_BASES)) {
         expect(resolved(base), base).toBe(value);
      }

      for(const name of expectedPins()) {
         expect(pinned.get(name), name).not.toMatch(/#\{|var\((?!--inet-focus-ring-width\))/);
         expect(pinned.get(name), name).toBe(resolved(name));
      }

      expect(pinned.get("--inet-ui-neutral-color")).toBe("#6A685F");
      expect(pinned.get("--inet-focus-ring")).toBe("0 0 0 var(--inet-focus-ring-width) rgba(217, 122, 26, 0.28)");
      expect(pinned.get("scrollbar-color")).toBe("color-mix(in srgb, #6A685F 55%, transparent) transparent");
   });

   it("is not folded into the content rule, which still leaves the calendar out", () => {
      expect(CONTENT_RULE).toMatch(/:where\(:not\(\.dropdown-datepicker\.viz-dark\)\)$/);
      expect(block(variables(), CONTENT_RULE)).not.toContain(".dropdown-datepicker.viz-dark {");
   });

   it("is in the compiled global.scss with the same declarations", () => {
      const escaped = CAL_RULE.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
      const m = compiledGlobal().match(new RegExp(`(?:^|\\n)${escaped}\\s*\\{([^{}]*)\\}`));

      expect(m, "no compiled calendar rule").not.toBeNull();
      expect(props(m[1])).toEqual(props(calRule()));
   }, 60000);
});

describe("custom-theme class", () => {
   const html = (src: string): string => {
      const m = src.match(/<html\b[^>]*>/);
      expect(m).not.toBeNull();
      return m[0];
   };

   const templates = [
      "web/projects/portal/src/index.html",
      "core/src/main/resources/inetsoft/web/resources/login.html",
      "core/src/main/resources/inetsoft/web/resources/restricted.html",
      "core/src/main/resources/inetsoft/web/resources/signup.html",
      "core/src/main/resources/inetsoft/web/resources/signupDetail.html",
      "core/src/main/resources/inetsoft/web/resources/error/error-template.html",
   ];

   for(const path of templates) {
      it(`is set from the customTheme model attribute on ${path}`, () => {
         const tag = html(readRepo(path));
         const appends = tag.match(/\b(?:data-th-|th:)classappend="[^"]*"/g) ?? [];

         expect(appends.length).toBe(1);
         expect(appends[0]).toContain("${customTheme} == true ? 'custom-theme'");
      });
   }

   for(const path of templates.slice(0, 2)) {
      it(`shares one classappend with shell-dark on ${path}`, () => {
         const tag = html(readRepo(path));

         expect(tag).toMatch(/classappend="[^"]*\$\{shellDark\} \? 'shell-dark[^"]*custom-theme[^"]*"/);
      });
   }
});

describe("danger button state label", () => {
   const SHELL_RULE = ":where(:root:not(.custom-theme)) .btn.btn-danger#{$outside-content}";
   const override = () => read("scss/_bootstrap-override.scss");
   const shellRule = () => block(override(), SHELL_RULE);
   const baseRule = () => block(override(), ".btn.btn-danger");
   // the state selector list that opens a rule's body
   const states = (css: string) => css.match(/^\s*(&:hover:not[\s\S]*?)\s*\{/m)[1];

   it("a hovered or focused danger button has a white label on the darker danger fill", () => {
      const css = shellRule();

      expect(css).toMatch(/&:hover:not\(:disabled\)/);
      expect(css).toMatch(/&\.focus, &:focus-visible/);
      expect(css).toMatch(/color:\s*#FFFFFF;/);
      expect(css).toMatch(/background-color:\s*var\(--inet-danger-hover-bg-color\);/);
      expect(css).toMatch(/border:\s*1px solid var\(--inet-danger-hover-bg-color\);/);
      expect(variables()).toMatch(/--inet-danger-hover-bg-color:\s*#B03A3A/i);
   });

   it("keeps the hover fill in the shell-only token set, not the runtime tokens", () => {
      const state = declarations(block(variables(), "@mixin inet-shell-state-tokens"));

      expect(state.get("--inet-danger-hover-bg-color")).toBe("#B03A3A");
      expect(runtimeTokens().has("--inet-danger-hover-bg-color")).toBe(false);
   });

   it("is gated off custom-themed pages and leaves dashboard content to the base rule", () => {
      expect(SHELL_RULE).toMatch(/^:where\(:root:not\(\.custom-theme\)\) /);
      expect(shellRule()).toBeTruthy();
      expect(variables()).toContain("$outside-content: \":where(:not(:is(#{$dashboard-content}");
   });

   it("comes after the base rule with the same state selectors, to win on equal specificity", () => {
      const src = override();

      expect(src.indexOf(shellRule())).toBeGreaterThan(src.indexOf(baseRule()));
      expect(states(shellRule())).toBe(states(baseRule()));
   });

   it("keeps the base rule's content look and moves its focus branch to :focus-visible", () => {
      const css = baseRule();

      expect(css).toMatch(/&\.focus, &:focus-visible/);
      expect(css).not.toMatch(/&:focus(?!-visible)/);
      expect(css).toMatch(
         /color:\s*var\(--inet-danger-color-dark\);\s*background-color:\s*var\(--inet-danger-color\);/);
   });
});

describe("solid, keyboard-only focus ring", () => {
   const stateTokens = () => declarations(block(variables(), "@mixin inet-shell-state-tokens"));
   const buttonFocusBlock = () => {
      const m = read("scss/_bootstrap-override.scss").match(
         /^\.btn-secondary, \.btn-default, \.btn-primary\s*\{[\s\S]*?^\}/m);

      expect(m).not.toBeNull();
      return m[0];
   };
   const FILLED_RING = "0 0 0 2px var(--inet-focus-ring-gap-color), " +
      "0 0 0 calc(2px + var(--inet-focus-ring-width)) var(--inet-focus-ring-color)";

   it("is a solid orange in the light shell, and the content value stays at .28", () => {
      expect(stateTokens().get("--inet-focus-ring-color")).toBe("#B4610F");
      expect(runtimeTokens().get("--inet-focus-ring-color")).toBe("rgba(217, 122, 26, 0.28)");
      expect(declarations(block(variables(), CONTENT_RULE)).get("--inet-focus-ring-color"))
         .toBe("rgba(217, 122, 26, 0.28)");
   });

   it("is the dark shell's link orange in the dark shell", () => {
      const dark = declarations(block(read("scss/_shell-dark.scss"), ":root.shell-dark"));

      expect(dark.get("--inet-focus-ring-color")).toBe("#{$dark-link}");
   });

   it("draws on :focus-visible, not :focus", () => {
      const css = buttonFocusBlock();

      expect(css).toMatch(/&:focus-visible\s*\{/);
      expect(css).not.toMatch(/&:focus(?!-visible)/);
   });

   it("is not drawn by the press rule", () => {
      const press = read("scss/_bootstrap-override.scss").match(
         /^\.btn\.btn-primary, \.btn\.btn-secondary, \.btn\.btn-default, \.btn-light, \.btn\.btn-light,\s*\.btn\.btn-danger\s*\{[\s\S]*?^\}/m);

      expect(press).not.toBeNull();
      expect(press[0]).toMatch(/filter:\s*brightness\(0\.9\)/);
      expect(press[0]).not.toMatch(/box-shadow/);
   });

   it("leaves a 2px surface gap inside a filled button, outside dashboard content only", () => {
      const css = read("scss/_bootstrap-override.scss");

      expect(css).toMatch(
         /\.btn\.btn-primary#\{\$outside-content\}, \.btn\.btn-danger#\{\$outside-content\} \{\s*&:focus-visible\s*\{\s*box-shadow:\s*0 0 0 2px/);
      expect(css.replace(/\s+/g, " ")).toContain(FILLED_RING);
   });

   it("defaults the gap colour to the shell surface for every page", () => {
      expect(runtimeTokens().get("--inet-focus-ring-gap-color"))
         .toBe("var(--inet-shell-surface-default)");
      expect(stateTokens().has("--inet-focus-ring-gap-color")).toBe(false);
   });
});

describe("stronger selected fill and darker shell muted text", () => {
   const stateTokens = () => declarations(block(variables(), "@mixin inet-shell-state-tokens"));
   const sassVar = (src: string, name: string) =>
      src.match(new RegExp(`^\\$${name}:\\s*([^;]+);`, "m"))?.[1];

   it("fills a selected row in the shell with #DFD9CC, a Sass variable of its own", () => {
      expect(sassVar(variables(), "shell-selected-bg-strong")).toBe("#DFD9CC");
      expect(stateTokens().get("--inet-shell-selected-bg-color"))
         .toBe("#{$shell-selected-bg-strong}");
   });

   it("keeps $shell-selected-bg at #E7E2D8, so build-time aliases do not move", () => {
      expect(sassVar(variables(), "shell-selected-bg")).toBe("#E7E2D8");
   });

   it("darkens the shell's muted text to #5F5E56 (light only), and $shell-text-muted stays #6A685F", () => {
      expect(stateTokens().get("--inet-text-muted-color")).toBe("#5F5E56");
      expect(sassVar(variables(), "shell-text-muted")).toBe("#6A685F");
   });

   it("keeps the dark shell's muted text unchanged", () => {
      expect(sassVar(read("scss/_shell-dark.scss"), "dark-text-muted")).toBe("#ADA8B4");
   });

   it("fills a selected row in the dark shell with #4A4750", () => {
      expect(sassVar(read("scss/_shell-dark.scss"), "dark-selected-bg")).toBe("#4A4750");
   });

   it("leaves dashboard content on the old selected fill and muted chain", () => {
      const declared = declarations(block(variables(), CONTENT_RULE));

      expect(declared.get("--inet-shell-selected-bg-color")).toBe("#{$shell-selected-bg}");
      expect(declared.get("--inet-text-muted-color")).toBe("#{$shell-text-muted}");

      for(const name of ["--inet-shell-text-muted", "--inet-table-heading-text-color",
         "--inet-ui-neutral-color", "--inet-button-default-text-color"])
      {
         expect(declared.has(name), name).toBe(true);
      }
   });
});

describe("one hover grey per surface (D7)", () => {
   const dark = () => read("scss/_shell-dark.scss");
   const sassVar = (src: string, name: string) =>
      src.match(new RegExp(`^\\$${name}:\\s*([^;]+);`, "m"))?.[1];
   // a Sass map written one `key: value,` per line, as key -> value
   const sassMap = (src: string, name: string): Map<string, string> => {
      const m = src.match(new RegExp(`^\\$${name}:\\s*\\(([\\s\\S]*?)^\\);`, "m"));
      expect(m, `no $${name} map`).not.toBeNull();
      return new Map([...m[1].matchAll(/^\s*([\w-]+|"[^"]+"):\s*(.+?),?\s*$/gm)]
         .map(e => [e[1].replace(/^"|"$/g, ""), e[2]]));
   };
   const darkRootTokens = () => new Map([...rootTokens(), ...declarations(block(dark(), ":root.shell-dark"))]);
   const lightMixin = () => block(variables(), "@mixin inet-surface-hover-tokens($hover)");
   const darkMixin = () => block(dark(), "@mixin inet-dark-surface-hover-tokens($hover)");
   const HOVER = "--inet-shell-surface-hover";
   const LIGHT_HOVER = { default: "#E9E5DD", canvas: "#E9E5DD", subtle: "#E8E4DB" };
   const DARK_HOVER = { default: "#36343A", canvas: "#2C2A30", subtle: "#3E3B42" };
   // the D7 container table: selector -> the token that paints it, and its surface, in each shell
   const CONTAINERS: [string, string, keyof typeof LIGHT_HOVER, string, keyof typeof DARK_HOVER][] = [
      [".repository-tree-pane, .tab-body .data-tree-pane",
         "--inet-portal-side-panel-bg-color", "subtle", "--inet-portal-side-panel-bg-color", "subtle"],
      [".portal-task-list-items .data-tree-pane",
         "--inet-shell-surface-subtle", "subtle", "--inet-shell-surface-subtle", "subtle"],
      [".data-model-left-top-pane",
         "--inet-portal-side-panel-bg-color", "subtle", "--inet-portal-side-panel-bg-color", "subtle"],
      ["logical-model-property-pane .data-model-left-top-pane",
         "--inet-shell-surface-subtle", "subtle", "--inet-shell-surface-subtle", "subtle"],
      [".viewer-toolbar",
         "--inet-vs-toolbar-bg-color", "subtle", "--inet-vs-toolbar-bg-color", "subtle"],
      [".data-model-header-pane, .physical-graph-toolbar, .ws-composite-table-breadcrumb-container, " +
         ".slide-out-toggle-container, .chart-style-container",
         "--inet-toolbar-bg-color", "subtle", "--inet-toolbar-bg-color", "subtle"],
      [".ws-table-bar",
         "--inet-toolbar-bg-color", "subtle", "--inet-shell-surface-default", "default"],
      [".composer-bottom-tabs",
         "--inet-nav-tabs-bg-color", "subtle", "--inet-nav-tabs-bg-color", "subtle"],
      [".binding-editor .left-container:not(.modal *), .object-wizard-pane .left-container:not(.modal *)",
         "--inet-composer-side-panel-bg-color", "subtle", "--inet-composer-side-panel-bg-color", "subtle"],
   ];

   // a selector list split at its top-level commas, not those inside :is() or :where()
   const selectors = (list: string): string[] => {
      const result = [""];
      let depth = 0;

      for(const c of list) {
         depth += c === "(" ? 1 : c === ")" ? -1 : 0;

         if(c === "," && depth === 0) {
            result.push("");
         }
         else {
            result[result.length - 1] += c;
         }
      }

      return result.map(s => s.trim());
   };

   // the compiled stylesheet's top-level rules, as [selector list, declarations]
   let compiled: [string[], Map<string, string>][];

   beforeAll(() => {
      const css = compiledGlobal();

      compiled =[...css.matchAll(/(?:^|\n)([^{}\n@][^{}]*?)\s*\{([^{}]*)\}/g)]
         .map(r => [selectors(r[1]), declarations(r[2])]);
   }, 60000);

   // the compiled rules with a selector that starts with `prefix` and is followed by $outside-content
   const rulesFor = (prefix: string) =>
      compiled.filter(([sels]) => sels.some(s => s.startsWith(`${prefix}:where(:not(:is(.vs-pane`)));

   it("keeps $shell-surface-hover at #ECE9E2, so the build-time aliases do not move", () => {
      expect(sassVar(variables(), "shell-surface-hover")).toBe("#ECE9E2");
      expect(runtimeTokens().get(HOVER)).toBe("#{$shell-surface-hover}");
   });

   it("sets the light shell's hover to #E9E5DD on white and canvas, and #E8E4DB on beige", () => {
      expect(sassVar(variables(), "shell-hover-default")).toBe(LIGHT_HOVER.default);
      expect(sassVar(variables(), "shell-hover-subtle")).toBe(LIGHT_HOVER.subtle);
      expect(declarations(block(variables(), "@mixin inet-shell-state-tokens")).get(HOVER))
         .toBe("#{$shell-hover-default}");
      expect([...sassMap(variables(), "shell-surface-hovers")]).toEqual([
         ["canvas", "$shell-hover-default"],
         ["default", "$shell-hover-default"],
         ["subtle", "$shell-hover-subtle"],
      ]);
   });

   it("sets the dark shell's hover to #36343A on the default surface, #2C2A30 on canvas, #3E3B42 on subtle", () => {
      expect(sassVar(dark(), "dark-surface-hover")).toBe(DARK_HOVER.default);
      expect(sassVar(dark(), "dark-surface-hover-canvas")).toBe(DARK_HOVER.canvas);
      expect(sassVar(dark(), "dark-surface-hover-subtle")).toBe(DARK_HOVER.subtle);
      expect(declarations(block(dark(), ":root.shell-dark")).get(HOVER)).toBe("#{$dark-surface-hover}");
      expect([...sassMap(dark(), "dark-surface-hovers")]).toEqual([
         ["canvas", "$dark-surface-hover-canvas"],
         ["default", "$dark-surface-hover"],
         ["subtle", "$dark-surface-hover-subtle"],
      ]);
   });

   it("leaves dashboard content on #ECE9E2 in the light shell", () => {
      expect(declarations(block(variables(), CONTENT_RULE)).get(HOVER)).toBe("#{$shell-surface-hover}");
   });

   it("re-declares, in the surface mixin, every :root token in the hover chain with its :root expression", () => {
      const tokens = rootTokens();
      const chain = chainOf(tokens, [HOVER]);
      const declared = declarations(lightMixin());

      expect([...declared.keys()].sort()).toEqual([...chain].sort());
      expect(declared.get(HOVER)).toBe("#{$hover}");

      for(const name of chain) {
         if(name !== HOVER) {
            expect(declared.get(name), name).toBe(tokens.get(name));
         }
      }
   });

   it("adds the dark shell's own hover readers in the dark surface mixin", () => {
      const tokens = darkRootTokens();
      const lightChain = chainOf(rootTokens(), [HOVER]);
      const extras = [...chainOf(tokens, [HOVER])].filter(name => !lightChain.has(name));
      const css = darkMixin();

      expect(css).toMatch(/@include inet-surface-hover-tokens\(\$hover\);/);
      expect(extras.length).toBeGreaterThan(0);
      expect([...declarations(css).keys()].sort()).toEqual([...extras].sort());

      for(const name of extras) {
         expect(declarations(css).get(name), name).toBe(tokens.get(name));
      }
   });

   it("lists the container table in $surface-hover-containers", () => {
      const map = sassMap(variables(), "surface-hover-containers");

      expect([...map]).toEqual(CONTAINERS.map(([sel, light, lightLevel, darkToken, darkLevel]) =>
         [sel, `(light: "${light}" ${lightLevel}, dark: "${darkToken}" ${darkLevel})`]));
   });

   for(const [sel, light, lightLevel, darkToken, darkLevel] of CONTAINERS) {
      describe(sel, () => {
         const sels = sel.split(", ");

         it(`hovers with ${LIGHT_HOVER[lightLevel]} in the light shell, off custom-themed pages`, () => {
            for(const s of sels) {
               const rules = rulesFor(`:where(:root:not(.custom-theme)) ${s}`);

               expect(rules.length, s).toBe(1);
               expect(rules[0][1].get(HOVER), s).toBe(LIGHT_HOVER[lightLevel]);
               expect(rules[0][1].get("--inet-ui-neutral-hover-bg-color"), s).toBe(`var(${HOVER})`);
               expect(rules[0][1].has("--inet-focus-ring-gap-color"), s).toBe(false);
            }
         });

         it(`sets the ring gap to var(${light}) on every page`, () => {
            for(const s of sels) {
               const rules = rulesFor(s);

               expect(rules.length, s).toBe(1);
               expect([...rules[0][1]], s).toEqual([["--inet-focus-ring-gap-color", `var(${light})`]]);
            }
         });

         it(`hovers with ${DARK_HOVER[darkLevel]} in the dark shell, with the gap on var(${darkToken})`, () => {
            for(const s of sels) {
               const rules = rulesFor(`:root.shell-dark ${s}`);

               expect(rules.length, s).toBe(1);
               expect(rules[0][1].get(HOVER), s).toBe(DARK_HOVER[darkLevel]);
               expect(rules[0][1].get("--inet-hover-bg-color"), s).toBe(`var(${HOVER})`);
               expect(rules[0][1].get("--inet-focus-ring-gap-color"), s).toBe(`var(${darkToken})`);
            }
         });
      });
   }

   it("declares the hover nowhere else outside :root and dashboard content", () => {
      const hoverRules = compiled.filter(([, d]) => d.has(HOVER));
      const containerRules = CONTAINERS.length * 2;
      // :root, the shell-state root, the dark root, the light and dark content rules, and the
      // dark-marked calendar's light-shell pins
      const others = hoverRules.filter(([sels]) => !sels.some(s => /:where\(:not\(:is\(\.vs-pane/.test(s)));

      expect(hoverRules.length - others.length).toBe(containerRules);
      expect(others.map(([sels]) => sels[0])).toEqual([
         ":root",
         ":root:where(:not(.custom-theme))",
         ":where(:root:not(.custom-theme)) :where(.vs-pane, .viewer-root, .wizard-preview-container .assembly-container, .object-view .vs-object-container, .vs-combo-box__list, .dropdown-datepicker, .hidden__annotation-tooltip, .vs-annotation__tooltip):where(:not(.dropdown-datepicker.viz-dark))",
         ":where(:root:not(.custom-theme):not(.shell-dark)) .dropdown-datepicker.viz-dark",
         ":root.shell-dark",
         ":root.shell-dark :is(.vs-pane, .viewer-root, .wizard-preview-container .assembly-container, .object-view .vs-object-container, .hidden__annotation-tooltip, .vs-annotation__tooltip, .vs-combo-box__list, .dropdown-datepicker:not(.viz-dark))",
      ]);
   });
});

describe("static pages (Task 6)", () => {
   const CSS = "core/src/main/resources/inetsoft/web/resources/css/";
   const layout = () => readRepo(CSS + "layout.css");
   const login = () => readRepo(CSS + "login.css");

   it("layout.css light root takes the PR7 light values, ungated", () => {
      const light = declarations(block(layout(), ":root"));

      expect(light.get("--inet-focus-ring-color")).toBe("#B4610F");
      expect(light.get("--inet-hover-primary-bg-color")).toBe("#E9E5DD");
      expect(light.get("--inet-ui-neutral-hover-bg-color")).toBe("#E9E5DD");
      expect(light.get("--inet-shell-selected-bg-color")).toBe("#DFD9CC");
      expect(light.get("--inet-text-muted-color")).toBe("#5F5E56");
   });

   it("layout.css dark root takes the PR7 dark values and keeps muted", () => {
      const dark = declarations(block(layout(), ":root.shell-dark"));

      expect(dark.get("--inet-focus-ring-color")).toBe("#F0A75A");
      expect(dark.get("--inet-hover-primary-bg-color")).toBe("#36343A");
      expect(dark.get("--inet-ui-neutral-hover-bg-color")).toBe("#36343A");
      expect(dark.get("--inet-shell-selected-bg-color")).toBe("#4A4750");
      expect(dark.get("--inet-text-muted-color")).toBe("#ADA8B4");
   });

   it("layout.css carries no translucent ring and the tile ring reads the token", () => {
      expect(layout()).not.toMatch(/--inet-focus-ring-color:\s*rgba/);
      expect(layout()).toMatch(
         /\.module-link:focus-visible \.module-tile\s*\{\s*box-shadow: 0 0 0 2px var\(--inet-focus-ring-color\)/);
   });

   it("login.css applies the new values only under :root:not(.custom-theme)", () => {
      const gated = declarations(block(login(), ":root:not(.custom-theme)"));

      expect(gated.get("--ln-focus")).toBe("var(--inet-focus-ring-color, #B4610F)");
      expect(gated.get("--ln-hover")).toBe("var(--inet-ui-neutral-hover-bg-color, #E9E5DD)");
      expect(gated.get("--ln-text-muted")).toBe("var(--inet-text-muted-color, #5F5E56)");
   });

   it("login.css themed-page fallbacks keep .28, #ECE9E2 and #6A685F", () => {
      const base = declarations(block(login(), ":root"));

      expect(base.get("--ln-focus")).toBe("var(--inet-focus-ring-color, rgba(217, 122, 26, 0.28))");
      expect(base.get("--ln-hover")).toBe("var(--inet-ui-neutral-hover-bg-color, #ECE9E2)");
      expect(base.get("--ln-text-muted")).toBe("var(--inet-text-muted-color, #6A685F)");
   });
});
