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

import { EditableObjectContainer } from "./editable-object-container.component";
import { Viewsheet } from "../../../data/vs/viewsheet";
import { makeVsObjectModel } from "./editable-object-container.component.test-helpers";

// Bug #81981: the composer paints every assembly as a flat sibling with the server's absolute
// z-index, so a group child must not get its group's z-index added on top, or it covers a
// top-level text that the server stacks above it.
describe("EditableObjectContainer container child z-index (Bug #81981)", () => {
   function obj(type: string, name: string, zIndex: number, container: string = null): any {
      const model = makeVsObjectModel(type, name) as any;
      model.absoluteName = name;
      model.objectFormat.zIndex = zIndex;
      model.container = container;
      return model;
   }

   it("keeps a top-level text above a rectangle in a group below it", () => {
      const vs = new Viewsheet();
      const group = obj("VSGroupContainer", "GroupContainer2", 51);
      const rect = obj("VSRectangle", "Rectangle4", 52, "GroupContainer2");
      const text = obj("VSText", "Text2", 102);
      vs.vsObjects.push(group, rect, text);

      expect(EditableObjectContainer.calculateZIndex(rect, vs)).toBe(52);
      expect(EditableObjectContainer.calculateZIndex(rect, vs))
         .toBeLessThan(EditableObjectContainer.calculateZIndex(text, vs));
      expect(EditableObjectContainer.calculateZIndex(rect, vs))
         .toBeGreaterThan(EditableObjectContainer.calculateZIndex(group, vs));
   });

   it("keeps a top-level text above a component in a tab below it", () => {
      const vs = new Viewsheet();
      const tab = obj("VSTab", "Tab1", 51);
      const chart = obj("VSChart", "Chart1", 52, "Tab1");
      const text = obj("VSText", "Text1", 101);
      vs.vsObjects.push(tab, chart, text);

      expect(EditableObjectContainer.calculateZIndex(chart, vs))
         .toBeLessThan(EditableObjectContainer.calculateZIndex(text, vs));
   });
});
