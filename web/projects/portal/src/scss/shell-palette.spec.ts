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

const CONTENT_RULE = ":where(:root:not(.custom-theme)) " +
   ":where(#{$dashboard-content}, #{$content-popup}, #{$annotation-tooltip})" +
   ":where(:not(.dropdown-datepicker.viz-dark))";

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

   it("lists every runtime token that derives from a PR7 base token", () => {
      const tokens = runtimeTokens();
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

      expect([...chain].sort()).toEqual([...PR7_CHAIN].sort());
   });

   it("re-declares the PR7 chain with the runtime tokens' own expressions", () => {
      const tokens = runtimeTokens();
      const declared = declarations(contentRule());

      for(const name of PR7_CHAIN) {
         expect(declared.get(name), name).toBe(tokens.get(name));
      }
   });

   it("re-declares only the PR7 chain, never the whole runtime token set", () => {
      const css = contentRule();

      expect([...declarations(css).keys()].sort()).toEqual([...PR7_CHAIN].sort());
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
