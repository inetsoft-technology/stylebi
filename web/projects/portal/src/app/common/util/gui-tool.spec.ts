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

import { afterEach, describe, expect, it } from "vitest";
import { GuiTool } from "./gui-tool";

describe("GuiTool.getMiniToolbarHeight", () => {
   afterEach(() => {
      document.body.classList.remove("viz-modern");
   });

   it("returns the legacy height (28) when the modern gate is off", () => {
      expect(GuiTool.getMiniToolbarHeight()).toBe(GuiTool.MINI_TOOLBAR_HEIGHT);
      expect(GuiTool.getMiniToolbarHeight()).toBe(28);
   });

   it("returns the compact height (24) when .viz-modern is on the body", () => {
      document.body.classList.add("viz-modern");
      expect(GuiTool.getMiniToolbarHeight()).toBe(GuiTool.MINI_TOOLBAR_HEIGHT_MODERN);
      expect(GuiTool.getMiniToolbarHeight()).toBe(24);
   });
});

describe("GuiTool.isVizModern", () => {
   afterEach(() => {
      document.body.classList.remove("viz-modern");
   });

   it("returns false when the modern gate is off", () => {
      expect(GuiTool.isVizModern()).toBe(false);
   });

   it("returns true when .viz-modern is on the body", () => {
      document.body.classList.add("viz-modern");
      expect(GuiTool.isVizModern()).toBe(true);
   });
});
