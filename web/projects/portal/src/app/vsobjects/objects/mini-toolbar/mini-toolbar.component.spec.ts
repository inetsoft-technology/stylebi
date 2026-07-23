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
import { MiniToolbar } from "./mini-toolbar.component";

// The topY math is pure and depends only on `top`, the context flags, and the pop-component check,
// so we construct the component directly with light mocks rather than through TestBed.
function makeToolbar(): MiniToolbar {
   const contextProvider: any = { composer: false, vsWizard: false, binding: false };
   const element: any = { nativeElement: {} };
   const miniToolbarService: any = {};
   const popComponentService: any = { isPopComponentShow: () => false };
   const comp = new MiniToolbar(contextProvider, element, miniToolbarService, popComponentService);
   comp.assembly = "Chart1";
   return comp;
}

describe("MiniToolbar.topY", () => {
   afterEach(() => {
      document.body.classList.remove("viz-modern");
   });

   it("positions the toolbar 28px above the object when the gate is off", () => {
      const comp = makeToolbar();
      comp.top = 100;
      expect(comp.topY).toBe(72); // 100 - 28 - 0
   });

   it("positions the toolbar 24px above the object under .viz-modern", () => {
      document.body.classList.add("viz-modern");
      const comp = makeToolbar();
      comp.top = 100;
      expect(comp.topY).toBe(76); // 100 - 24 - 0
   });
});
