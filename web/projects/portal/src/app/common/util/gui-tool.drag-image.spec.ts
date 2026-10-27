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

// Bug #79407 (hardening): drag image labels are names, never markup.
describe("GuiTool.createDragImage", () => {
   beforeEach(() => document.querySelectorAll(".show-drag-field").forEach(e => e.remove()));
   afterEach(() => vi.restoreAllMocks());
   afterEach(() => {
      document.querySelectorAll(".show-drag-field").forEach(e => e.remove());
   });

   it("should render labels as text with br separators and not parse markup", () => {
      vi.spyOn(GuiTool, "isSafari").mockReturnValue(false);
      const elem = GuiTool.createDragImage(["a<img src=x onerror=alert(1)>", "b"]);

      expect(elem.querySelector("img")).toBeNull();
      expect(elem.querySelectorAll("br").length).toBe(1);
      expect(elem.textContent).toBe("a<img src=x onerror=alert(1)>b");
   });

   it("should replace previous content when the drag image element is reused", () => {
      vi.spyOn(GuiTool, "isSafari").mockReturnValue(false);
      GuiTool.createDragImage(["one", "two"]);
      const elem = GuiTool.createDragImage(["three"]);

      expect(elem.textContent).toBe("three");
      expect(elem.querySelectorAll("br").length).toBe(0);
   });
});
