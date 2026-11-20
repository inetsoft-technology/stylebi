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

// Bug #81981: the server sends absolute z-indexes for container children (a container's
// children are numbered container + 1, ...), and the viewer paints every assembly as a flat
// sibling. Adding the ancestors' z-index again lifted a group child over a top-level text that
// the server stacks above it, so the text disappeared in the viewer.
describe("VSObjectContainer container child z-index (Bug #81981)", () => {
   function obj(name: string, zIndex: number, extra: any = {}): VSObjectModel {
      return makeVSObject({
         absoluteName: name, objectFormat: makeObjectFormat({ zIndex }), ...extra
      } as any);
   }

   function make(objs: VSObjectModel[]) {
      const context = makeContextProvider();
      context.viewer = true;
      return makeComponent({ context, vsInfo: makeVsInfo(objs) }).comp;
   }

   it("keeps a top-level text above a rectangle in a group below it", () => {
      // the values the server sends after opening the reported viewsheet
      const group = obj("GroupContainer2", 51, { objectType: "VSGroupContainer" });
      const rect = obj("Rectangle4", 52, { objectType: "VSRectangle", container: "GroupContainer2" });
      const text = obj("Text2", 102, { objectType: "VSText" });
      const comp = make([group, rect, text]);

      expect(comp.zIndex(rect)).toBe(52);
      expect(comp.getContainerZIndex(rect)).toBeLessThan(comp.getContainerZIndex(text));
      expect(comp.getContainerZIndex(rect)).toBeGreaterThan(comp.getContainerZIndex(group));
   });

   it("keeps a top-level text above a component in a tab below it", () => {
      const tab = obj("Tab1", 51, { objectType: "VSTab" });
      const chart = obj("Chart1", 52, { objectType: "VSChart", container: "Tab1" });
      const text = obj("Text1", 101, { objectType: "VSText" });
      const comp = make([tab, chart, text]);

      expect(comp.zIndex(chart)).toBe(52);
      expect(comp.getContainerZIndex(chart)).toBeLessThan(comp.getContainerZIndex(text));
   });

   it("stacks a group in a tab below its children and both below a later text", () => {
      // the values the server sends for a group in a tab: each child is numbered from its
      // own container's z-index
      const tab = obj("Tab1", 2, { objectType: "VSTab" });
      const group = obj("Group1", 3, { objectType: "VSGroupContainer", container: "Tab1" });
      const c1 = obj("Text1", 4, { objectType: "VSText", container: "Group1" });
      const c2 = obj("Text2", 5, { objectType: "VSText", container: "Group1" });
      const later = obj("Text3", 52, { objectType: "VSText" });
      const comp = make([tab, group, c1, c2, later]);

      for(const child of [c1, c2]) {
         expect(comp.zIndex(child)).toBeGreaterThan(comp.zIndex(group));
         expect(comp.zIndex(child)).toBeLessThan(comp.zIndex(later));
      }

      expect(comp.zIndex(c2)).toBeGreaterThan(comp.zIndex(c1));
   });
});
