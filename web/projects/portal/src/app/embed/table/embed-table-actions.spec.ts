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
import { TableDataPathTypes } from "../../common/data/table-data-path-types";
import { TestUtils } from "../../common/test/test-utils";
import { EmbedAssemblyContextProviderFactory } from "../../vsobjects/context-provider.service";
import { VSTableModel } from "../../vsobjects/model/vs-table-model";
import { EmbedTableActions } from "./embed-table-actions";

describe("EmbedTableActions", () => {
   const createActions: (model: VSTableModel) => EmbedTableActions = (model) => {
      return new EmbedTableActions(model, EmbedAssemblyContextProviderFactory(),
         false, null, null, null, null, () => false, () => {});
   };

   // A plain detail/data cell - table-actions.ts has no row/column grouping concept, so unlike
   // crosstab there is no Hide Column/Show Columns/drill hierarchy to reuse here, only Set Cell
   // Size exists on both.
   const selectDetailCell: (model: VSTableModel) => void = (model) => {
      let region = TestUtils.createMockselectedRegion();
      region.path = ["City"];
      region.type = TableDataPathTypes.DETAIL;

      model.titleSelected = false;
      model.firstSelectedRow = 1;
      model.firstSelectedColumn = 3;
      model.selectedHeaders = null;
      model.selectedData = new Map<number, number[]>();
      model.selectedData.set(1, [3]);
      model.selectedRegions = [region];
   };

   it("should not show Show Details or Export in the context menu", () => {
      const model = TestUtils.createMockVSTableModel("Table1");
      const actions = createActions(model);
      const menuActions = actions.menuActions;
      selectDetailCell(model);

      const allIds = menuActions.flatMap(g => g.actions.map(a => a.id()));
      expect(allIds).not.toContain("table show-details");
      expect(allIds).not.toContain("table export");
   });

   it("should show Set Cell Size when a single cell is selected", () => {
      const model = TestUtils.createMockVSTableModel("Table1");
      const actions = createActions(model);
      const menuActions = actions.menuActions;

      const setCellSize = menuActions[0].actions[0];
      expect(setCellSize.id()).toBe("table cell size");
      expect(setCellSize.visible()).toBeFalsy();

      selectDetailCell(model);
      expect(setCellSize.visible()).toBeTruthy();
   });
});
