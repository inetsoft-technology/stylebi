/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
import { readFileSync } from "fs";
import { dirname, join } from "path";
import { fileURLToPath } from "url";

const __dirname = dirname(fileURLToPath(import.meta.url));

// The sort button's opaque fill masks the header text, so a marked table's button takes the
// header band VSTableStructureDefaults seeds, or it shows as a box on the header.
describe("table header sort-button mask", () => {
   const themeable = readFileSync(join(__dirname, "_themeable.scss"), "utf-8");
   const rule = (scope: string) =>
      themeable.match(new RegExp(`\\.${scope} \\.vs-header-cell-button-sort\\s*\\{[^}]*\\}`))?.[0];

   it("matches the modern light header band", () => {
      expect(rule("viz-modern")).toMatch(/background-color:\s*#DEDCD7/i);
   });

   it("matches the dark header band, after the modern rule so it wins", () => {
      expect(rule("viz-dark")).toMatch(/background-color:\s*#38363C/i);
      expect(themeable.indexOf(rule("viz-dark"))).toBeGreaterThan(themeable.indexOf(rule("viz-modern")));
   });

   it("keeps the toolbar surface on an unmarked table", () => {
      expect(themeable).toMatch(
         /\n\.vs-header-cell-button-sort\s*\{[^}]*background-color:\s*var\(--inet-toolbar-bg-color\)/);
   });
});
