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
import { TestUtils } from "../../common/test/test-utils";
import { VSObjectModel } from "../model/vs-object-model";
import { DateTipHelper } from "./data-tip/date-tip-helper";
import { VSObjectContainer } from "./vs-object-container.component";

describe("VSObjectContainer", () => {
   function createContainer(
      vsObjects: VSObjectModel[],
      popSource: boolean = false,
      dataTipSource: boolean = false
   ): VSObjectContainer
   {
      const container = Object.create(VSObjectContainer.prototype) as VSObjectContainer;

      (container as any).vsInfo = { vsObjects: vsObjects };
      (container as any).popService = { isPopSource: vi.fn(() => popSource) };
      (container as any).dataTipService = { isDataTipSource: vi.fn(() => dataTipSource) };

      return container;
   }

   describe("zIndex", () => {
      it("should use popup source z-index for non-adhoc popup sources", () => {
         const object = TestUtils.createMockVSObjectModel("VSText", "Text1");
         object.objectFormat.zIndex = 25;

         const container = createContainer([object], true);

         expect(container.zIndex(object)).toBe(DateTipHelper.getPopUpSourceZIndex());
      });

      it("should boost adhoc filters after adding parent container z-index", () => {
         const parent = TestUtils.createMockVSObjectModel("VSTab", "Tab1");
         parent.objectFormat.zIndex = 7;

         const filter = TestUtils.createMockVSSelectionListModel("Filter1");
         filter.objectFormat.zIndex = 3;
         filter.container = parent.absoluteName;
         filter.adhocFilter = true;

         const container = createContainer([parent, filter]);

         expect(container.zIndex(filter)).toBe(
            parent.objectFormat.zIndex +
            filter.objectFormat.zIndex +
            DateTipHelper.getPopUpContentBoostZIndex()
         );
      });

      it("should not collapse adhoc filters to the popup source z-index", () => {
         const filter = TestUtils.createMockVSSelectionListModel("Filter1");
         filter.objectFormat.zIndex = 5;
         filter.adhocFilter = true;

         const container = createContainer([filter], true);

         expect(container.zIndex(filter)).toBe(
            filter.objectFormat.zIndex + DateTipHelper.getPopUpContentBoostZIndex()
         );
      });
   });
});
