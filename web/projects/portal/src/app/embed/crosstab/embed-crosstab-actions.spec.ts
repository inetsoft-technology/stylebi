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

   const find: (groups: AssemblyActionGroup[], id: string) => AssemblyAction =
      (groups, id) => groups.reduce((all, group) => all.concat(group.actions), [])
         .find((action) => action.id() === id);

   // Bug #75951: show-details and export are toolbar buttons, and used to be listed in the menu
   // as well, so "More" repeated them.
   it("offers no menu actions, so the toolbar's More button hides itself", () => {
      const actions = createActions(createModel());

      expect(actions.menuActions).toEqual([]);
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
});
