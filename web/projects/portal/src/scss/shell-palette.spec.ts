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
      const tokens = rootTokens();
      const chain = new Set(PR7_BASE_TOKENS);
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
