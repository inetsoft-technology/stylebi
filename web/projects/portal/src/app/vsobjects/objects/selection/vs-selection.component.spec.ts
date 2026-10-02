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

   it("aNestedListDoesNotDoubleInset", async () => {
      const component = await createComponent({
         objectFormat: fmt(132, 202), titleFormat: fmt(132, 30), padding: inset,
         containerType: "VSSelectionContainer"
      });

      expect(component.inContainer).toBe(true);
      expect(component.getBodyHeight()).toBe(172);
      expect(component.getBodyWidth()).toBe(132 - 2);
   });

   it("aDropdownPanelIsUnchanged", async () => {
      const component = await createComponent({
         objectFormat: fmt(132, 202), titleFormat: fmt(132, 30), dropdown: true,
         maxMode: false, listHeight: 5, cellHeight: 28, padding: inset
      });

      expect(component.getBodyHeight()).toBe(140);
   });

   it("an assembly with no inset is unchanged", async () => {
      const component = await createComponent({
         objectFormat: fmt(100, 120), titleFormat: fmt(100, 20)
      });

      expect(component.getBodyHeight()).toBe(100);
      expect(component.getBodyWidth()).toBe(100);
   });
});
