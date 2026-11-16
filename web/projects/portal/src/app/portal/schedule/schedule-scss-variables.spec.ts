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
import { readFileSync, readdirSync } from "fs";
import { dirname, join } from "path";
import { fileURLToPath } from "url";

const __dirname = dirname(fileURLToPath(import.meta.url));
const SCSS_ROOT = join(__dirname, "..", "..", "..", "scss");

// Bug #81340: these declarations read variables no theme defines, so the whole declaration
// (the 1px border / panel fill) was invalid and nothing was drawn.
const FILES = [
   "schedule/schedule-task-editor/schedule-task-editor.component.scss",
   "schedule/schedule-task-editor/actions/task-action-pane.component.scss",
   "schedule/schedule-task-editor/conditions/task-condition-pane.component.scss",
   "schedule/schedule-task-editor/select-dashboard-dialog/select-dashboard-dialog.component.scss",
   "data/input-name-desc-dialog/input-name-desc-dialog.component.scss"
];

function definedVariables(): string {
   return readdirSync(SCSS_ROOT).filter(f => f.endsWith(".scss"))
      .map(f => readFileSync(join(SCSS_ROOT, f), "utf-8")).join("\n");
}

describe("schedule/dialog scss custom properties", () => {
   it("only reads fallback-less variables that the portal theme defines", () => {
      const defined = definedVariables();

      for(const file of FILES) {
         const src = readFileSync(join(__dirname, "..", file), "utf-8");
         const used = [...src.matchAll(/var\(\s*(--[\w-]+)\s*\)/g)].map(m => m[1]);

         for(const name of used) {
            expect(defined.includes(name + ":"), `${name} in ${file} is undefined`).toBe(true);
         }
      }
   });
});
