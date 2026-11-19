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
 * Guards the shell's hover language (Feature #80673) on controls that had none or a different one:
 * icon buttons take the neutral hover chip, rows take the neutral hover fill, and the dark shell's
 * own state values (disabled, pressed, focus ring). Anchored on class and token names, not on
 * computed colours: each assertion names the template or rule that a regression would remove.
 */
import { readFileSync } from "fs";
import { dirname, join } from "path";
import { fileURLToPath } from "url";

const __dirname = dirname(fileURLToPath(import.meta.url));

// a file under web/projects/portal/src
const read = (relPath: string): string => readFileSync(join(__dirname, "..", relPath), "utf-8");

// the top-level block that opens with `selector {`, up to its closing brace at line start
const block = (src: string, selector: string): string => {
   const escaped = selector.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
   const m = src.match(new RegExp(`^${escaped}\\s*\\{[\\s\\S]*?^\\}`, "m"));
   expect(m, `no top-level ${selector} block`).not.toBeNull();
   return m[0];
};

describe("Data tab search clear", () => {
   it("hovers like the other tree search clears (icon-btn-ghost glyph chip)", () => {
      const html = read("app/portal/data/data-navigation-tree/data-sources-tree-view.component.html");
      const clear = html.match(/<i\b[^>]*\bclose-icon\b[^>]*>/);

      expect(clear).not.toBeNull();
      expect(clear[0]).toMatch(/\bicon-btn-ghost\b/);
      expect(clear[0]).toMatch(/\bicon-btn-ghost--glyph\b/);
   });

   it("lets the glyph strengthen on hover (no !important icon-color-default pinning its colour)", () => {
      const html = read("app/portal/data/data-navigation-tree/data-sources-tree-view.component.html");
      const clear = html.match(/<i\b[^>]*\bclose-icon\b[^>]*>/);

      expect(clear[0]).not.toMatch(/\bicon-color-default\b/);
   });
});

describe("binding field option icon", () => {
   const fieldEditIcon = () => block(read("app/binding/editor/fieldmc.component.scss"), ".fieldEditIcon");

   it("hovers with the neutral chip and a stronger glyph, like the sort and eye buttons", () => {
      const hover = fieldEditIcon().match(/&:hover[^{]*\{[\s\S]*?\n {2}\}/);

      expect(hover).not.toBeNull();
      // a disabled icon (the aesthetic Color/Shape/Size panes) stays inert: no chip, no stronger glyph
      expect(hover[0]).toMatch(/^&:hover:not\(:has\(> i\.icon-disabled\)\)\s*\{/);
      expect(hover[0]).toMatch(/background-color:\s*var\(--inet-ui-neutral-hover-bg-color\)/);
      expect(hover[0]).toMatch(/\bi\s*\{\s*color:\s*var\(--inet-hover-text-color\)/);
   });

   it("keeps the chip under the keyboard focus ring and takes the icon-button radius", () => {
      const css = fieldEditIcon();
      const focus = css.match(/&:focus,[\s\S]*?\{[\s\S]*?\}/);

      expect(focus).not.toBeNull();
      expect(focus[0]).toMatch(/background-color:\s*var\(--inet-ui-neutral-hover-bg-color\)/);
      expect(focus[0]).toMatch(/box-shadow:\s*var\(--inet-focus-ring\)/);
      expect(css).toMatch(/border-radius:\s*var\(--inet-radius-lg\)/);
   });
});

describe("alignment toggles", () => {
   const toggle = () => block(read("app/format/objects/binding-alignment-pane.component.scss"), ".toggleButton");

   it("take the icon-button radius, selected state included", () => {
      expect(toggle()).toMatch(/^ {2}border-radius:\s*var\(--inet-radius-lg\)/m);
   });

   it("keep the selected fill on hover and focus, and the ring on keyboard focus", () => {
      const css = toggle();

      expect(css).toMatch(/&:not\(\.bg-selected\):hover\s*\{/);
      expect(css).toMatch(/&:not\(\.bg-selected\):focus-visible\s*\{/);
      expect(css).toMatch(/&:focus-visible\s*\{[^}]*box-shadow:\s*var\(--inet-focus-ring\)/);
   });
});

describe("Advanced tab flyover rows", () => {
   it("hover as an inset rounded row inside the padded list box", () => {
      const css = read("app/vsobjects/dialog/graph/tip-pane.component.scss");
      const hover = css.match(/^\.flyover-item:has\(\.form-check-input:not\(:disabled\)\):hover\s*\{[^}]*\}/m);

      expect(hover).not.toBeNull();
      expect(hover[0]).toMatch(/background-color:\s*var\(--inet-list-hover-bg-color\)/);
      expect(hover[0]).toMatch(/border-radius:\s*var\(--inet-radius-sm\)/);
   });
});

describe("worksheet detail rows", () => {
   it("hover with the shell's neutral fill in both shells, keeping their text and a highlighted row's fill", () => {
      const themeable = read("scss/_themeable.scss");

      expect(themeable).toMatch(/^\.table-data__row:hover\s*\{\s*color:\s*inherit;\s*\}/m);
      expect(themeable).toMatch(
         /^\.table-data__row:not\(\.highlighted\):hover\s*\{\s*background-color:\s*var\(--inet-ui-neutral-hover-bg-color\);\s*\}/m);
      // the viz hover token is for charts and tables in dashboards, not the composer's own rows
      expect(themeable).not.toMatch(/^\.table-data__row:hover\s*\{[^}]*--inet-viz-hover/m);
   });

   it("need no dark-only override once the shared rule is neutral", () => {
      expect(read("scss/_shell-dark.scss")).not.toMatch(/\.table-data__row/);
   });
});

describe("dark shell disabled primary button", () => {
   const shellDark = () => read("scss/_shell-dark.scss");

   it("declares Material 3's disabled values (on-surface at 12% and 38%) for the dark shell", () => {
      const root = block(shellDark(), ":root.shell-dark");

      expect(root).toMatch(/--inet-dark-disabled-primary-bg:\s*rgba\(230, 224, 233, 0\.12\);/);
      expect(root).toMatch(/--inet-dark-disabled-primary-text:\s*rgba\(230, 224, 233, 0\.38\);/);
   });

   it("draws a disabled primary with them at full opacity, outside dashboard content", () => {
      const css = shellDark();
      // the selector list interpolates #{...}, so the declarations start at the " {" that ends a line
      const rule = css.match(/\.btn\.btn-primary:is\(:disabled, \.disabled\)#\{\$outside-content\}[\s\S]*? \{\r?\n[^}]*\}/);

      expect(rule).not.toBeNull();
      expect(rule[0]).toMatch(/opacity:\s*1;/);
      expect(rule[0]).toMatch(/color:\s*var\(--inet-dark-disabled-primary-text\);/);
      expect(rule[0]).toMatch(/background-color:\s*var\(--inet-dark-disabled-primary-bg\);/);
      expect(rule[0]).toMatch(/border-color:\s*var\(--inet-dark-disabled-primary-bg\);/);
   });
});

describe("dark shell button press", () => {
   it("lightens past the hover, as hover lightens past rest in the dark shell", () => {
      const css = read("scss/_shell-dark.scss");
      const press = css.match(
         /\.btn\.btn-primary, \.btn\.btn-secondary, \.btn\.btn-default, \.btn-light, \.btn\.btn-light,\s*\.btn\.btn-danger\s*\{\s*&:not\(:disabled\):not\(\.disabled\):not\(\.condition-field-combo-toggle\.disabled\):active:focus#\{\$outside-content\}\s*\{[^}]*\}/);

      expect(press).not.toBeNull();
      expect(press[0]).toMatch(/filter:\s*brightness\(1\.12\);/);
   });
});

describe("dark-only token reset list", () => {
   it("names no token the dark shell no longer declares", () => {
      const css = read("scss/_shell-dark.scss");
      const list = css.match(/\$dark-only-tokens:([^;]*);/);

      expect(list).not.toBeNull();
      expect(list[1]).not.toMatch(/\bhover-ring-color\b/);
      expect(css).not.toMatch(/--inet-dark-hover-ring-color\s*:/);
   });
});
