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
import { AssemblyAction } from "../../common/action/assembly-action";
import { AssemblyActionGroup } from "../../common/action/assembly-action-group";
import { TestUtils } from "../../common/test/test-utils";
import { EmbedAssemblyContextProviderFactory } from "../../vsobjects/context-provider.service";
import { VSTableModel } from "../../vsobjects/model/vs-table-model";
import { EmbedTableActions } from "./embed-table-actions";

describe("EmbedTableActions", () => {
   const createModel: () => VSTableModel = () => TestUtils.createMockVSTableModel("Table1");

   const createActions: (model: VSTableModel) => EmbedTableActions = (model) =>
      new EmbedTableActions(model, EmbedAssemblyContextProviderFactory(), false, null, null, null,
         null, () => false, () => {});

   const find: (groups: AssemblyActionGroup[], id: string) => AssemblyAction =
      (groups, id) => groups.reduce((all, group) => all.concat(group.actions), [])
         .find((action) => action.id() === id);

   /** One data cell selected, as vs-table leaves the model after a left click on a detail cell. */
   const selectDataCell: (model: VSTableModel) => void = (model) => {
      model.selectedHeaders = null;
      model.selectedData = new Map<number, number[]>([[1, [3]]]);
   };

   /** A header click: vs-table records it in selectedHeaders and clears selectedData. */
   const selectHeaderCell: (model: VSTableModel) => void = (model) => {
      model.selectedHeaders = new Map<number, number[]>([[0, [0]]]);
      model.selectedData = null;
   };

   // Bug #75951: show-details and export are toolbar buttons, and used to be listed in the menu
   // as well, so "More" repeated them.
   it("offers no menu actions, so the toolbar's More button hides itself", () => {
      const actions = createActions(createModel());

      expect(actions.menuActions).toEqual([]);
      expect(find(actions.toolbarActions, "menu actions").visible()).toBeFalsy();
   });

   // Bug #75923: the action drills into the selected cells, so with nothing selected it has
   // nothing to act on.
   it("hides show-details until a data cell is selected", () => {
      const model = createModel();
      const actions = createActions(model);
      const showDetails = find(actions.toolbarActions, "table show-details");

      expect(showDetails.visible()).toBeFalsy();

      selectDataCell(model);
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

      selectDataCell(model);
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

      selectDataCell(model);
      expect(model.summary).toBeFalsy();
      expect(find(actions.toolbarActions, "table show-details").visible()).toBeTruthy();
   });

   it("keeps export and the wiz fullscreen toggle on the toolbar", () => {
      const actions = createActions(createModel());

      expect(find(actions.toolbarActions, "table export").visible()).toBeTruthy();
      expect(find(actions.toolbarActions, "table wiz-fullscreen").visible()).toBeTruthy();
   });
});
