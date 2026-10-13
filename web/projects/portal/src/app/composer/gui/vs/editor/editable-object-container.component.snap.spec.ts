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
import { dirname, join } from "path";
import { fileURLToPath } from "url";

const __dirname = dirname(fileURLToPath(import.meta.url));

// Bug #76370: since PR #4930 the composer highlight ring is an outline, not a 2px border, so
// the main .object-editor interactable must not subtract the directive's default
// resizeAdjustment (2) when snapping, or moved/resized objects land 2px off the grid.
// The component's specs instantiate the class directly, so this reads the real template.
describe("EditableObjectContainer — snap resize adjustment", () => {
   const template = readFileSync(
      join(__dirname, "editable-object-container.component.html"), "utf-8");

   it("binds [resizeAdjustment]=0 on the main .object-editor interactable", () => {
      const start = template.indexOf("<div #objectEditor class=\"object-editor\"");
      expect(start).toBeGreaterThan(-1);
      const end = template.indexOf(">", template.indexOf("wInteractable", start));
      const tag = template.substring(start, end);
      expect(tag).toMatch(/\bwInteractable\b/);
      expect(tag).toMatch(/\[resizeAdjustment]\s*=\s*"0"/);
   });
});
