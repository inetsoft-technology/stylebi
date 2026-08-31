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
   it("returns the legacy height (28) when vizModern is false", () => {
      expect(GuiTool.getMiniToolbarHeight(false)).toBe(GuiTool.MINI_TOOLBAR_HEIGHT);
      expect(GuiTool.getMiniToolbarHeight(false)).toBe(28);
   });

   it("returns the compact height (24) when vizModern is true", () => {
      expect(GuiTool.getMiniToolbarHeight(true)).toBe(GuiTool.MINI_TOOLBAR_HEIGHT_MODERN);
      expect(GuiTool.getMiniToolbarHeight(true)).toBe(24);
   });
});

describe("GuiTool retired global reads", () => {
   it("no longer exposes a global modern-visualization read", () => {
      // The gate is per-assembly from P5 on. A global read cannot answer "is THIS assembly modern",
      // and leaving one available invites a caller that silently regresses a mixed dashboard.
      expect((GuiTool as any).isVizModern).toBeUndefined();
   });
});

describe("GuiTool.isVizShell", () => {
   afterEach(() => {
      document.body.classList.remove("viz-shell");
   });

   it("returns false when the shell class is off the body", () => {
      expect(GuiTool.isVizShell()).toBe(false);
   });

   it("returns true when .viz-shell is on the body", () => {
      document.body.classList.add("viz-shell");
      expect(GuiTool.isVizShell()).toBe(true);
   });
});

describe("GuiTool density mode", () => {
   afterEach(() => {
      document.body.classList.remove(
         "viz-density-dense", "viz-density-compact", "viz-density-comfortable");
   });

   it("defaults to dense when no density class is present", () => {
      expect(GuiTool.vizDensityMode()).toBe("dense");
   });

   it("reads the density class when one is present", () => {
      document.body.classList.add("viz-density-compact");
      expect(GuiTool.vizDensityMode()).toBe("compact");
      document.body.classList.remove("viz-density-compact");
      document.body.classList.add("viz-density-comfortable");
      expect(GuiTool.vizDensityMode()).toBe("comfortable");
   });
});

describe("GuiTool.isVizModernElement", () => {
   let container: HTMLElement;

   afterEach(() => {
      if(container) {
         container.remove();
         container = null;
      }

      document.body.classList.remove("viz-shell");
   });

   it("returns true when the element itself carries viz-modern", () => {
      container = document.createElement("div");
      container.classList.add("viz-modern");
      document.body.appendChild(container);

      expect(GuiTool.isVizModernElement(container)).toBe(true);
   });

   it("returns true when an ancestor carries viz-modern", () => {
      container = document.createElement("div");
      container.classList.add("viz-modern");
      const child = document.createElement("div");
      const grandchild = document.createElement("span");
      child.appendChild(grandchild);
      container.appendChild(child);
      document.body.appendChild(container);

      // native closest matches self-or-ancestor, so a nested descendant still finds it
      expect(GuiTool.isVizModernElement(grandchild)).toBe(true);
   });

   it("returns false when neither the element nor any ancestor carries viz-modern", () => {
      container = document.createElement("div");
      const child = document.createElement("span");
      container.appendChild(child);
      document.body.appendChild(container);

      expect(GuiTool.isVizModernElement(child)).toBe(false);
   });

   it("returns false for a null element without throwing", () => {
      expect(GuiTool.isVizModernElement(null)).toBe(false);
   });

   it("ignores the org-level viz-shell class on the body", () => {
      document.body.classList.add("viz-shell");
      container = document.createElement("div");
      document.body.appendChild(container);

      // viz-shell is a body-level flag; the per-assembly mark must not read it
      expect(GuiTool.isVizModernElement(container)).toBe(false);
   });
});
