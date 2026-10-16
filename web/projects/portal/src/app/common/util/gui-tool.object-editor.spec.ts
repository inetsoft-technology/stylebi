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
import { GuiTool } from "./gui-tool";

// Bug #76370: the composer object editor no longer has a 2px border (its ring is an outline), so
// its rect must not be shrunk; other object editors (e.g. the layout pane's) still have one.
describe("GuiTool.getElementRect — object editor border", () => {
   function makeElement(className: string): HTMLElement {
      const element = document.createElement("div");
      element.className = className;
      const rect = { left: 100, top: 50, right: 300, bottom: 150, width: 200, height: 100 };
      vi.spyOn(element, "getClientRects").mockReturnValue([rect] as any);
      return element;
   }

   afterEach(() => vi.restoreAllMocks());

   it("returns the full box of an outline-highlight object editor", () => {
      const rect = GuiTool.getElementRect(makeElement("object-editor outline-highlight"));
      expect(rect.left).toBe(100);
      expect(rect.top).toBe(50);
      expect(rect.width).toBe(200);
      expect(rect.height).toBe(100);
   });

   it("still excludes the 2px border of a bordered object editor", () => {
      const rect = GuiTool.getElementRect(makeElement("layout-object object-editor"));
      expect(rect.left).toBe(102);
      expect(rect.top).toBe(52);
      expect(rect.width).toBe(196);
      expect(rect.height).toBe(96);
   });
});
