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

import { VSObjectModel } from "../model/vs-object-model";
import {
   makeComponent, makeContextProvider, makeObjectFormat, makeVSObject, makeVsInfo
} from "./vs-object-container.component.test-helpers";

// Bug #78478: in the viewer every assembly sits in its own stacking context
// (.vs-object-parent-container), so an open dropdown calendar must raise that wrapper,
// or a component with a higher z-index paints over the calendar's date grid.
describe("VSObjectContainer open dropdown calendar z-index (Bug #78478)", () => {
   function calendar(zIndex: number, shown: boolean, extra: any = {}): VSObjectModel {
      return makeVSObject({
         absoluteName: "Calendar1", objectType: "VSCalendar",
         objectFormat: makeObjectFormat({ zIndex }),
         dropdownCalendar: true, calendarsShown: shown, ...extra
      } as any);
   }

   function chart(zIndex: number): VSObjectModel {
      return makeVSObject({ absoluteName: "Chart1", objectFormat: makeObjectFormat({ zIndex }) });
   }

   function make(viewer: boolean, objs: VSObjectModel[]) {
      const context = makeContextProvider();
      context.viewer = viewer;
      return makeComponent({ context, vsInfo: makeVsInfo(objs) }).comp;
   }

   it("raises an open dropdown calendar above a later component in the viewer", () => {
      const cal = calendar(1, true);
      const comp = make(true, [cal, chart(2)]);

      expect(comp.getContainerZIndex(cal)).toBe(1 + 9999);
      expect(comp.getContainerZIndex(comp.vsInfo.vsObjects[1])).toBe(2);
   });

   it("keeps a closed dropdown calendar at its own z-index and raises it again on reopen", () => {
      const cal = calendar(1, false);
      const comp = make(true, [cal, chart(2)]);
      expect(comp.getContainerZIndex(cal)).toBe(1);

      (<any> cal).calendarsShown = true;
      expect(comp.getContainerZIndex(cal)).toBe(1 + 9999);

      (<any> cal).calendarsShown = false;
      expect(comp.getContainerZIndex(cal)).toBe(1);
   });

   it("raises an open calendar inside a tab above a component outside the tab", () => {
      const tab = makeVSObject({
         absoluteName: "Tab1", objectType: "VSTab", objectFormat: makeObjectFormat({ zIndex: 5 })
      });
      const cal = calendar(1, true, { container: "Tab1" });
      const comp = make(true, [tab, cal, chart(20)]);

      expect(comp.getContainerZIndex(cal)).toBe(5 + 1 + 9999);
   });

   it("does not raise an open calendar outside the viewer, or a non-dropdown calendar", () => {
      const cal = calendar(1, true);
      expect(make(false, [cal, chart(2)]).getContainerZIndex(cal)).toBe(1);

      const plain = calendar(1, true, { dropdownCalendar: false });
      expect(make(true, [plain, chart(2)]).getContainerZIndex(plain)).toBe(1);
   });
});
