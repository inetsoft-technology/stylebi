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
import { describe, expect, it } from "vitest";
import { ComposerObjectService } from "../composer-object.service";
import { LayoutObject } from "./layout-object.component";

/**
 * A rectangle/oval drop shadow is painted outside the assembly's own box, and
 * .object-container clips it away unless the host carries .shape-shadow. This
 * covers the predicate that adds that class.
 */
describe("LayoutObject shadow clipping", () => {
   function createComponent(objectModel: any): LayoutObject {
      const composerObjectService: any = {
         addLayoutObjectKeyEventAdapter: () => {}
      };

      const component = new LayoutObject(
         null, null, null, null, null, null, null,
         <ComposerObjectService> composerObjectService, null);
      component.model = <any> { name: "obj", objectModel };

      return component;
   }

   it("should report a shadow for a rectangle that has one", () => {
      const component = createComponent({ objectType: "VSRectangle", shadow: true });
      expect(component.hasShapeShadow()).toBe(true);
   });

   it("should report a shadow for an oval that has one", () => {
      const component = createComponent({ objectType: "VSOval", shadow: true });
      expect(component.hasShapeShadow()).toBe(true);
   });

   it("should not report a shadow for a shape with the shadow turned off", () => {
      const component = createComponent({ objectType: "VSRectangle", shadow: false });
      expect(component.hasShapeShadow()).toBe(false);
   });

   it("should not report a shadow for a non-shape object", () => {
      const component = createComponent({ objectType: "VSTable" });
      expect(component.hasShapeShadow()).toBe(false);
   });

   it("should not report a shadow before the object model is loaded", () => {
      const component = createComponent(null);
      expect(component.hasShapeShadow()).toBe(false);
   });
});
