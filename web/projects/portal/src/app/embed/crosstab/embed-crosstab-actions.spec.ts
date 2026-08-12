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
import { AssemblyActionGroup } from "../../common/action/assembly-action-group";
import { TableDataPathTypes } from "../../common/data/table-data-path-types";
import { TestUtils } from "../../common/test/test-utils";
import { DrillLevel } from "../../composer/data/vs/drill-level";
import { EmbedAssemblyContextProviderFactory } from "../../vsobjects/context-provider.service";
import { VSCrosstabModel } from "../../vsobjects/model/vs-crosstab-model";
import { EmbedCrosstabActions } from "./embed-crosstab-actions";

describe("EmbedCrosstabActions", () => {
   /** A crosstab with one row header and one column header, i.e. data starts at (1, 1). */
   const createModel: () => VSCrosstabModel = () => {
      const model = TestUtils.createMockVSCrosstabModel("Crosstab1");
      model.headerRowCount = 1;
      model.headerColCount = 1;
      model.runtimeRowHeaderCount = 1;
      model.runtimeColHeaderCount = 1;
      return model;
   };

   const createActions: (model: VSCrosstabModel) => EmbedCrosstabActions = (model) =>
      new EmbedCrosstabActions(model, EmbedAssemblyContextProviderFactory(), false, null, null,
         null, null, () => false, () => {});

   /** {@link TestUtils.findAction}, aliased to keep the assertions below on one line. */
   const find = TestUtils.findAction;

   // Bug #75951: show-details and export are toolbar buttons and used to be listed in the menu as
   // well, so "More" repeated them. Bug #75961 then added menu-only entries, so the menu is no
   // longer empty - but every entry is selection-gated, which is what keeps "More" hidden on an
   // untouched crosstab and what EmbedContextMenu.open's empty-menu guard relies on.
   it("shows no menu action until something is selected, so the More button hides itself", () => {
      const actions = createActions(createModel());

      expect(AssemblyActionGroup.anyVisible(actions.menuActions)).toBeFalsy();
      expect(find(actions.toolbarActions, "menu actions").visible()).toBeFalsy();
   });

   // Bug #75923
   it("hides show-details until a cell is selected", () => {
      const model = createModel();
      const actions = createActions(model);
      const showDetails = find(actions.toolbarActions, "crosstab show-details");

      expect(showDetails.visible()).toBeFalsy();

      model.selectedData = new Map<number, number[]>([[1, [1]]]);
      expect(showDetails.visible()).toBeTruthy();
   });

   // Unlike a plain table, a crosstab row/column header stands for a group whose details can be
   // shown, so selectedHeaders counts.
   it("counts a row header selection as something to show details for", () => {
      const model = createModel();
      const actions = createActions(model);

      model.selectedData = null;
      model.selectedHeaders = new Map<number, number[]>([[1, [0]]]);
      expect(find(actions.toolbarActions, "crosstab show-details").visible()).toBeTruthy();
   });

   // The corner block above the row headers / left of the column headers is not data and cannot
   // be drilled into - the rule this inherits from ShowDetailEvent.
   it("does not count a corner header cell", () => {
      const model = createModel();
      const actions = createActions(model);

      model.selectedData = null;
      model.selectedHeaders = new Map<number, number[]>([[0, [0]]]);
      expect(find(actions.toolbarActions, "crosstab show-details").visible()).toBeFalsy();
   });

   it("keeps export and the wiz fullscreen toggle on the toolbar", () => {
      const actions = createActions(createModel());

      expect(find(actions.toolbarActions, "crosstab export").visible()).toBeTruthy();
      expect(find(actions.toolbarActions, "crosstab wiz-fullscreen").visible()).toBeTruthy();
   });

   // ------------------------------------------------------------------ menu-only actions (#75961)
   //
   // These build their model with the bare TestUtils factory rather than createModel() above: the
   // drill assertions read cells/selectedRegions directly and must not also inherit createModel()'s
   // header counts, which would move which cell (0, 0) is.

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

   // ---------------------------------------------------------------- toolbar drill actions
   //
   // Drill Down/Up Filter were toolbar buttons on CrosstabActions that createToolbarActions's
   // full override had dropped - same shape as the menu-only gap above, just on the toolbar
   // instead of "More".

   it("hides drill down/up filter until a header cell with a drill level is selected", () => {
      const model = TestUtils.createMockVSCrosstabModel("Crosstab1");
      const actions = createActions(model);

      expect(find(actions.toolbarActions, "crosstab drilldown").visible()).toBeFalsy();
      expect(find(actions.toolbarActions, "crosstab drillup").visible()).toBeFalsy();
   });

   it("hides drill down/up filter for a plain data cell with no drill level", () => {
      const model = TestUtils.createMockVSCrosstabModel("Crosstab1");
      const actions = createActions(model);
      selectNonDrillableDataCell(model);

      expect(find(actions.toolbarActions, "crosstab drilldown").visible()).toBeFalsy();
      expect(find(actions.toolbarActions, "crosstab drillup").visible()).toBeFalsy();
   });

   it("shows drill down filter (not drill up) for a root-level drillable header cell", () => {
      const model = TestUtils.createMockVSCrosstabModel("Crosstab1");
      const actions = createActions(model);
      selectDrillableHeaderCell(model);
      model.cells[0][0].drillLevel = DrillLevel.Root;

      expect(find(actions.toolbarActions, "crosstab drilldown").visible()).toBeTruthy();
      expect(find(actions.toolbarActions, "crosstab drillup").visible()).toBeFalsy();
   });

   it("shows both drill down and drill up filter for a middle-level drillable header cell", () => {
      const model = TestUtils.createMockVSCrosstabModel("Crosstab1");
      const actions = createActions(model);
      selectDrillableHeaderCell(model);
      model.cells[0][0].drillLevel = DrillLevel.Middle;

      expect(find(actions.toolbarActions, "crosstab drilldown").visible()).toBeTruthy();
      expect(find(actions.toolbarActions, "crosstab drillup").visible()).toBeTruthy();
   });

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
