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


import { readFileSync } from "fs";
import { resolve } from "path";

// Bug #82156: EM navbar icons must stay light on the dark navbar, and the dark text token must
// be a valid rgba() color.
describe("EM theme contrast tokens (Bug #82156)", () => {
   const read = (rel: string) => readFileSync(resolve(__dirname, "../..", rel), "utf8");

   it("dark primary text is a valid four-argument rgba", () => {
      expect(read("styles/_variables.scss"))
         .toContain("--inet-em-dark-primary-text: rgba(0,0,0,.87);");
   });

   it("accent contrast 500-900 use light text", () => {
      const vars = read("styles/_variables.scss");

      for(const n of [500, 600, 700, 800, 900]) {
         expect(vars).toContain(
            `--inet-em-accent-contrast-${n}: var(--inet-em-light-primary-text);`);
      }
   });

   it("navbar icons do not fall back to the accent contrast", () => {
      const theme = read("app/navbar/_navbar-theme.scss");
      expect(theme).not.toMatch(/\.mat-icon\s*\{[^}]*\$accent, '500-contrast'/);
      expect(theme).not.toContain("--inet-em-navbar-text-color, mat.m2-get-color-from-palette($accent");
   });
});
