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
import { VSCrosstabModel } from "../../vsobjects/model/vs-crosstab-model";
import { EmbedCrosstabActions } from "./embed-crosstab-actions";

describe("EmbedCrosstabActions", () => {
   const createActions: (model: VSCrosstabModel) => EmbedCrosstabActions = (model) => {
      return new EmbedCrosstabActions(model, EmbedAssemblyContextProviderFactory(),
         false, null, null, null, null, () => false, () => {});
   };

   // Plain data/summary cell, no drill hierarchy defined on the field - e.g. a measure value,
   // not a date field grouped by Year/Month/Day.
   const selectNonDrillableDataCell: (model: VSCrosstabModel) => void = (model) => {
      model.selectedHeaders = null;
      model.selectedData = new Map<number, number[]>();
      model.selectedData.set(0, [0]);
      model.selectedRegions = [Object.assign(TestUtils.createMockselectedRegion(), {
         path: ["Sum(Discount)"],
         type: TableDataPathTypes.SUMMARY
      })];
      model.firstSelectedRow = 0;
      model.firstSelectedColumn = 0;
      model.cells = [[{
         cellData: "16",
         cellLabel: null,
         row: 0,
         col: 0,
         vsFormatModel: TestUtils.createMockVSFormatModel(),
         hyperlinks: [],
         grouped: false,
         dataPath: {
            row: false, col: false, colIndex: -1, index: 0, level: -1,
            dataType: "double", path: ["Sum(Discount)"], type: TableDataPathTypes.SUMMARY
         },
         presenter: null,
         isImage: false
      }]];
   };

   // Row header cell for a field with a drill hierarchy defined - e.g. Month(ORDER_DATE)
   // grouped by Year/Quarter/Month, matching bugs/img_2.png.
   const selectDrillableHeaderCell: (model: VSCrosstabModel) => void = (model) => {
      model.selectedData = null;
      model.selectedHeaders = new Map<number, number[]>();
      model.selectedHeaders.set(0, [0]);
      model.selectedRegions = [Object.assign(TestUtils.createMockselectedRegion(), {
         path: ["Month(ORDER_DATE)"],
         type: TableDataPathTypes.GROUP_HEADER
      })];
      model.firstSelectedRow = 0;
      model.firstSelectedColumn = 0;
      model.cells = [[{
         cellData: "2025 10月",
         cellLabel: "2025 10月",
         row: 0,
         col: 0,
         vsFormatModel: TestUtils.createMockVSFormatModel(),
         hyperlinks: [],
         grouped: true,
         drillOp: "+",
         field: "Month(ORDER_DATE)",
         dataPath: {
            row: true, col: false, colIndex: -1, index: 0, level: 0,
            dataType: "date", path: ["Month(ORDER_DATE)"], type: TableDataPathTypes.GROUP_HEADER
         },
         presenter: null,
         isImage: false
      }]];
   };

   it("should not show Show Details or Export in the context menu", () => {
      const model = TestUtils.createMockVSCrosstabModel("Crosstab1");
      const actions = createActions(model);
      const menuActions = actions.menuActions;
      selectNonDrillableDataCell(model);

      const allIds = menuActions.flatMap(g => g.actions.map(a => a.id()));
      expect(allIds).not.toContain("crosstab show-details");
      expect(allIds).not.toContain("crosstab export");
   });

   it("should show Set Cell Size and Hide Column, but not the drill actions, for a plain data cell", () => {
      const model = TestUtils.createMockVSCrosstabModel("Crosstab1");
      const actions = createActions(model);
      const menuActions = actions.menuActions;
      selectNonDrillableDataCell(model);

      const setCellSize = menuActions[0].actions[0];
      const hideColumn = menuActions[1].actions[0];
      const showColumns = menuActions[1].actions[1];
      const expandAll = menuActions[2].actions[0];
      const collapseAll = menuActions[2].actions[1];
      const expandField = menuActions[2].actions[2];
      const collapseField = menuActions[2].actions[3];

      expect(setCellSize.id()).toBe("table cell size");
      expect(setCellSize.visible()).toBeTruthy();
      expect(hideColumn.id()).toBe("crosstab hide column");
      expect(hideColumn.visible()).toBeTruthy();
      expect(showColumns.id()).toBe("crosstab show columns");
      expect(showColumns.visible()).toBeFalsy();
      expect(expandAll.id()).toBe("expand all");
      expect(expandAll.visible()).toBeFalsy();
      expect(collapseAll.id()).toBe("collapse all");
      expect(collapseAll.visible()).toBeFalsy();
      expect(expandField.id()).toBe("expand field");
      expect(expandField.visible()).toBeFalsy();
      expect(collapseField.id()).toBe("collapse field");
      expect(collapseField.visible()).toBeFalsy();
   });

   it("should show Set Cell Size, Hide Column and the drill actions for a header cell with a defined drill hierarchy", () => {
      const model = TestUtils.createMockVSCrosstabModel("Crosstab1");
      const actions = createActions(model);
      const menuActions = actions.menuActions;
      selectDrillableHeaderCell(model);

      const setCellSize = menuActions[0].actions[0];
      const hideColumn = menuActions[1].actions[0];
      const expandAll = menuActions[2].actions[0];
      const collapseAll = menuActions[2].actions[1];
      const expandField = menuActions[2].actions[2];
      const collapseField = menuActions[2].actions[3];

      expect(setCellSize.visible()).toBeTruthy();
      expect(hideColumn.visible()).toBeTruthy();
      expect(expandAll.visible()).toBeTruthy();
      expect(collapseAll.visible()).toBeTruthy();
      expect(expandField.visible()).toBeTruthy();
      expect(collapseField.visible()).toBeTruthy();
   });

   it("should show Show Columns when the model has a hidden column, regardless of cell selection", () => {
      const model = TestUtils.createMockVSCrosstabModel("Crosstab1");
      const actions = createActions(model);
      const menuActions = actions.menuActions;
      const showColumns = menuActions[1].actions[1];

      expect(showColumns.visible()).toBeFalsy();

      (model as any).hasHiddenColumn = true;
      expect(showColumns.visible()).toBeTruthy();
   });
});
