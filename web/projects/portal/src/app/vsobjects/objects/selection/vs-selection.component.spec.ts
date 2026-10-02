/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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

import { TestUtils } from "../../../common/test/test-utils";
import { VSFormatModel } from "../../model/vs-format-model";
import { VSSelection } from "./vs-selection.component";
import {
   createSelectionComponent,
   makeMockListModel
} from "./vs-selection.component.test-helpers";

function fmt(width: number, height: number): VSFormatModel {
   const format = TestUtils.createMockVSFormatModel();
   format.width = width;
   format.height = height;
   return format;
}

async function createComponent(fields: any = {}): Promise<VSSelection> {
   const model = Object.assign(makeMockListModel(), fields);
   const { comp } = await createSelectionComponent({ model });
   return comp;
}

describe("VSSelection card inset", () => {
   const inset = { top: 16, left: 16, bottom: 16, right: 16 };

   it("takes the inset out of the body height", async () => {
      const component = await createComponent({
         objectFormat: fmt(132, 202), titleFormat: fmt(132, 30), padding: inset
      });

      expect(component.getBodyHeight()).toBe(140);
   });

   it("takes the inset out of the body width", async () => {
      const component = await createComponent({
         objectFormat: fmt(132, 202), titleFormat: fmt(132, 30), padding: inset
      });

      expect(component.getBodyWidth()).toBe(100);
   });

   it("aNestedListInsetsLikeAStandaloneOne", async () => {
      const component = await createComponent({
         objectFormat: fmt(132, 202), titleFormat: fmt(132, 30), padding: inset,
         containerType: "VSSelectionContainer"
      });

      // the container takes no inset of its own, so a child carries its own, and the 2px is the
      // contained chrome allowance that predates the inset
      expect(component.inContainer).toBe(true);
      expect(component.getBodyHeight()).toBe(140);
      expect(component.getBodyWidth()).toBe(132 - 2 - 32);
   });

   it("aNestedListTakesTheOffsetTooNotJustTheShrink", async () => {
      const component = await createComponent({
         objectFormat: fmt(132, 202), titleFormat: fmt(132, 30), padding: inset,
         containerType: "VSSelectionContainer"
      });

      expect(component.inContainer).toBe(true);
      expect(component.getContentLeft()).toBe(16);
      expect(component.getContentTop()).toBe(16);
   });

   it("aDropdownPanelIsUnchanged", async () => {
      // cellHeight 30 so the panel's 150 cannot be confused with the final branch's 202 - 30 - 32
      const component = await createComponent({
         objectFormat: fmt(132, 202), titleFormat: fmt(132, 30), dropdown: true,
         maxMode: false, listHeight: 5, cellHeight: 30, padding: inset
      });

      expect(component.getBodyHeight()).toBe(150);
   });

   it("an assembly with no inset is unchanged", async () => {
      const component = await createComponent({
         objectFormat: fmt(100, 120), titleFormat: fmt(100, 20)
      });

      expect(component.getBodyHeight()).toBe(100);
      expect(component.getBodyWidth()).toBe(100);
      expect(component.getContentLeft()).toBe(0);
      expect(component.getContentTop()).toBe(0);
   });

   it("moves the body in by the inset as well as shrinking it", async () => {
      const component = await createComponent({
         objectFormat: fmt(132, 202), titleFormat: fmt(132, 30), padding: inset
      });

      expect(component.getContentLeft()).toBe(16);
      expect(component.getContentTop()).toBe(16);
   });

   it("leaves the inset band on the far edge of the card, not dead space", async () => {
      const component = await createComponent({
         objectFormat: fmt(132, 202), titleFormat: fmt(132, 30), padding: inset
      });

      expect(component.getContentTop() + 30 + component.getBodyHeight()).toBe(202 - 16);
      expect(component.getContentLeft() + component.getBodyWidth()).toBe(132 - 16);
   });

   it("drops the scroll track into the inset with the rows it scrolls", async () => {
      const component = await createComponent({
         objectFormat: fmt(132, 202), titleFormat: fmt(132, 30), padding: inset,
         titleVisible: true
      });

      expect(component.verticalScrollbarTop).toBe(30 + 16);
   });

   it("aDropdownPanelTakesNoVerticalOffset", async () => {
      const component = await createComponent({
         objectFormat: fmt(132, 202), titleFormat: fmt(132, 30), dropdown: true,
         maxMode: false, listHeight: 5, cellHeight: 30, padding: inset
      });

      // the panel's height comes off the cell height, so there is no band above it to move into;
      // its width still comes off the card, so the horizontal offset mirrors that subtraction
      expect(component.getContentTop()).toBe(0);
      expect(component.getContentLeft()).toBe(16);
      expect(component.getContentLeft() + component.getBodyWidth()).toBe(132 - 16);
   });
});
