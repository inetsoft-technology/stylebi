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
});

describe("binding field option icon", () => {
   const fieldEditIcon = () => block(read("app/binding/editor/fieldmc.component.scss"), ".fieldEditIcon");

   it("hovers with the neutral chip and a stronger glyph, like the sort and eye buttons", () => {
      const hover = fieldEditIcon().match(/&:hover\s*\{[\s\S]*?\n {2}\}/);

      expect(hover).not.toBeNull();
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
