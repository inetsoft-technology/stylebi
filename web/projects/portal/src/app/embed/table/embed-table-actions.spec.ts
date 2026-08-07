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
import { EmbedAssemblyContextProviderFactory } from "../../vsobjects/context-provider.service";
import { VSTableModel } from "../../vsobjects/model/vs-table-model";
import { EmbedTableActions } from "./embed-table-actions";

describe("EmbedTableActions", () => {
   const createModel: () => VSTableModel = () => TestUtils.createMockVSTableModel("Table1");

   const createActions: (model: VSTableModel) => EmbedTableActions = (model) =>
      new EmbedTableActions(model, EmbedAssemblyContextProviderFactory(), false, null, null, null,
         null, () => false, () => {});

   /** {@link TestUtils.findAction}, aliased to keep the assertions below on one line. */
   const find = TestUtils.findAction;

   /**
    * One data cell selected, as vs-table leaves the model after a left click on a detail cell.
    * table-actions.ts has no row/column grouping concept, so unlike crosstab there is no Hide
    * Column/Show Columns/drill hierarchy to reuse here - Set Cell Size is the only menu entry.
    */
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

   /** A header click: vs-table records it in selectedHeaders and clears selectedData. */
   const selectHeaderCell: (model: VSTableModel) => void = (model) => {
      model.selectedHeaders = new Map<number, number[]>([[0, [0]]]);
      model.selectedData = null;
   };

   // Bug #75951: show-details and export are toolbar buttons and used to be listed in the menu as
   // well, so "More" repeated them. Bug #75961 then added menu-only entries, so the menu is no
   // longer empty - but every entry is selection-gated, which is what keeps "More" hidden on an
   // untouched table and what EmbedContextMenu.open's empty-menu guard relies on.
   it("shows no menu action until something is selected, so the More button hides itself", () => {
      const actions = createActions(createModel());

      expect(AssemblyActionGroup.anyVisible(actions.menuActions)).toBeFalsy();
      expect(find(actions.toolbarActions, "menu actions").visible()).toBeFalsy();
   });

   // The other half of Bug #75951: these two are on the toolbar, so they must not be in the menu
   // at all - not merely hidden there.
   it("should not show Show Details or Export in the context menu", () => {
      const model = createModel();
      const actions = createActions(model);
      const menuActions = actions.menuActions;
      selectDetailCell(model);

      const allIds = menuActions.flatMap(g => g.actions.map(a => a.id()));
      expect(allIds).not.toContain("table show-details");
      expect(allIds).not.toContain("table export");
   });

   // Bug #75961: menu-only, so it has to be in the menu rather than on the toolbar.
   it("should show Set Cell Size when a single cell is selected", () => {
      const model = createModel();
      const actions = createActions(model);
      const menuActions = actions.menuActions;

      const setCellSize = menuActions[0].actions[0];
      expect(setCellSize.id()).toBe("table cell size");
      expect(setCellSize.visible()).toBeFalsy();

      selectDetailCell(model);
      expect(setCellSize.visible()).toBeTruthy();
   });

   // Bug #75923: the action drills into the selected cells, so with nothing selected it has
   // nothing to act on.
   it("hides show-details until a data cell is selected", () => {
      const model = createModel();
      const actions = createActions(model);
      const showDetails = find(actions.toolbarActions, "table show-details");

      expect(showDetails.visible()).toBeFalsy();

      selectDetailCell(model);
      expect(showDetails.visible()).toBeTruthy();
   });

   it("does not count a header selection as something to show details for", () => {
      const model = createModel();
      const actions = createActions(model);

      selectHeaderCell(model);
      expect(find(actions.toolbarActions, "table show-details").visible()).toBeFalsy();
   });

   it("hides show-details on a form table even with a cell selected", () => {
      const model = createModel();
      const actions = createActions(model);

      selectDetailCell(model);
      model.form = true;
      expect(find(actions.toolbarActions, "table show-details").visible()).toBeFalsy();
   });

   // model.summary is assembly.isSummaryTable() on the server and false for the detail tables the
   // wiz generates. TableActions.showDetailsVisible requires it; the embed deliberately does not,
   // and this pins that difference so a future "align with the base class" edit cannot silently
   // hide the action for every wiz table.
   it("does not require a summary table, unlike the viewer", () => {
      const model = createModel();
      const actions = createActions(model);

      selectDetailCell(model);
      expect(model.summary).toBeFalsy();
      expect(find(actions.toolbarActions, "table show-details").visible()).toBeTruthy();
   });

   it("keeps export and the wiz fullscreen toggle on the toolbar", () => {
      const actions = createActions(createModel());

      expect(find(actions.toolbarActions, "table export").visible()).toBeTruthy();
      expect(find(actions.toolbarActions, "table wiz-fullscreen").visible()).toBeTruthy();
   });
});
