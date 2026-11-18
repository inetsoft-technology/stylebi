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
 * Guards the SCSS half of Feature #81379: a dark-marked date calendar (combo box, text input, table
 * cell) is dark in both shells. Anchored on names, not values. Each assertion maps to a defect that
 * no class-binding spec can see (the button-light tokens in particular are invisible in a dark shell).
 */
import { readFileSync } from "fs";
import { dirname, join } from "path";
import { fileURLToPath } from "url";

const __dirname = dirname(fileURLToPath(import.meta.url));
const shellDark = readFileSync(join(__dirname, "_shell-dark.scss"), "utf-8");

describe("dark-marked date calendar styles", () => {
   const block = () => {
      // up to the first closing brace at line start; a [^}] class would stop at a #{...} interpolation
      const m = shellDark.match(/^\.dropdown-datepicker\.viz-dark\s*\{[\s\S]*?^\}/m);
      expect(m).not.toBeNull();
      return m[0];
   };

   it("declares the .dropdown-datepicker.viz-dark block outside :root.shell-dark", () => {
      expect(block()).toMatch(/color-scheme:\s*dark/);
   });

   it("declares the derived surface tokens directly", () => {
      for(const t of ["dropdown-bg-color", "toolbar-bg-color", "input-bg-color", "input-focus-bg-color"]) {
         expect(block()).toMatch(new RegExp("--inet-" + t + ":"));
      }
   });

   it("declares the six derived button-light tokens the day cells read", () => {
      for(const t of ["text-color", "bg-color", "border-color",
                      "hover-text-color", "hover-bg-color", "hover-border-color"]) {
         expect(block()).toMatch(new RegExp("--inet-button-light-" + t + ":"));
      }
   });

   it("exempts the dark-marked calendar from the three light resets", () => {
      expect(shellDark).toMatch(
         /\$content-popup-light:\s*"[^"]*\.dropdown-datepicker:not\(\.viz-dark\)[^"]*"/);
      for(const head of [
         ":root.shell-dark :is(#{$dashboard-content}, #{$annotation-tooltip}, #{$content-popup-light})",
         ":root.shell-dark :is(#{$dashboard-content}, #{$content-popup-light}):not(#{$viz-scopes})",
         ":where(.shell-dark) :where(#{$dashboard-content}, #{$content-popup-light})"
      ]) {
         expect(shellDark).toContain(head);
      }
   });

   it("keeps $outside-content on the full popup list", () => {
      expect(shellDark).toMatch(/\$outside-content:[^;]*#\{\$content-popup\}/);
   });
});
