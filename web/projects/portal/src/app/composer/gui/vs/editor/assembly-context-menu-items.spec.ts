/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import { EventEmitter } from "@angular/core";
import { AssemblyContextMenuItemsComponent } from "./assembly-context-menu-items.component";

describe("AssemblyContextMenuItems tests", () => {
   let actionFactory: any;
   let actions: AssemblyContextMenuItemsComponent;

   beforeEach(() => {
      actionFactory = { createActions: jest.fn(() => ({ onAssemblyActionEvent: new EventEmitter() })) };
      actions = new AssemblyContextMenuItemsComponent(actionFactory);
   });

   // Bug #10895 Dont show group when there is a selection container selected
   it("should not show group option with selection container selected", () => {
      /*
      This test is bad and needs to be rewritten.
      1. It use internal implementation details of other classes (2 strikes).
      2. It is now an integration test that requires the interaction of VSPane,
         AssemblyActionFactory, the various implementations of AssemblyActions, etc.
      3. This is not the correct place to test this in the first place.
       */
      // let vs: Viewsheet = new Viewsheet();
      // let text1: VSTextModel = Object.assign({
      //    text: "foo",
      //    shadow: false,
      //    autoSize: false,
      //    hyperlinks: [],
      //    presenter: false
      // }, TestUtils.createMockVSObjectModel("VSText", "Text1"));
      // let text2: VSTextModel = Object.assign({
      //    text: "bar",
      //    shadow: false,
      //    autoSize: false,
      //    hyperlinks: [],
      //    presenter: false
      // }, TestUtils.createMockVSObjectModel("VSText", "Text2"));
      // let selectionContainer1: VSSelectionContainerModel = Object.assign({
      //    title: "",
      //    titleRatio: 0,
      //    dataRowHeight: 0,
      //    outerSelections: [],
      //    childObjects: [],
      //    childrenNames: [],
      //    cellHeight: 0,
      //    titleFormat: TestUtils.createMockVSFormatModel(),
      //    titleVisible: true,
      //    isDropTarget: false
      // }, TestUtils.createMockVSObjectModel("VSSelectionContainer", "SelectionContainer1"));
      //
      // vs.vsObjects = [text1, text2, selectionContainer1];
      // vs.currentFocusedAssemblies = [text1, text2];
      //
      // let groupVisible: boolean = actions["isGroupVisible"](vs);
      // expect(groupVisible).toBeTruthy();
      // vs.selectAssembly(selectionContainer1);
      // groupVisible = actions["isGroupVisible"](vs);
      // expect(groupVisible).toBeFalsy();
   });

   // Bug #16599 when checking for changelayervisible, return true if a container assembly is not found
   it("should update send to back/front enabled after adding object", () => {
      /*
      This test is bad and needs to be rewritten.
      1. It use internal implementation details of other classes (2 strikes).
      2. It is now an integration test that requires the interaction of VSPane,
         AssemblyActionFactory, the various implementations of AssemblyActions, etc.
      3. This is not the correct place to test this in the first place.
       */
      // let vs: Viewsheet = new Viewsheet();
      // let text1: VSTextModel = Object.assign({
      //    text: "foo",
      //    shadow: false,
      //    autoSize: false,
      //    hyperlinks: [],
      //    presenter: false
      // }, TestUtils.createMockVSObjectModel("VSText", "Text1"));
      // text1.container = "Foo1";
      // vs.vsObjects = [text1];
      //
      // let changeLayerVisible: boolean = actions["isChangeLayerVisible"](text1, vs);
      // expect(changeLayerVisible).toBeTruthy();
   });
});