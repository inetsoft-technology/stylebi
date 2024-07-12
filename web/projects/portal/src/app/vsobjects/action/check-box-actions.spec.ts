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
import { TestUtils } from "../../common/test/test-utils";
import { ComposerContextProviderFactory, ViewerContextProviderFactory } from "../context-provider.service";
import { CheckBoxActions } from "./check-box-actions";

describe("CheckBoxActions", () => {
   const createModel = () => TestUtils.createMockVSCheckBoxModel("Checkbox1");
   const popService: any = { getPopComponent: jest.fn() };
   popService.getPopComponent.mockImplementation(() => "");

   it("check status of menu actions of checkbox in composer", () => {
      const expectedMenu = [
         [
            { id: "checkbox properties", visible: true },
            { id: "checkbox show-format-pane", visible: true }
         ],
         [
            { id: "vs-object copy", visible: true },
            { id: "vs-object cut", visible: true },
            { id: "vs-object remove", visible: true },
            { id: "vs-object group", visible: true },
            { id: "vs-object ungroup", visible: true }
         ],
         [
            { id: "vs-object bring-forward", visible: true },
            { id: "vs-object bring-to-front", visible: true },
            { id: "vs-object send-backward", visible: true },
            { id: "vs-object send-to-back", visible: true }
         ]
      ];

      const model = createModel();
      const actions = new CheckBoxActions(model, ComposerContextProviderFactory(),
         false, null, null, popService);
      const menuActions = actions.menuActions;

      expect(menuActions).toMatchSnapshot();
   });

   it("check status of menu actions of checkbox in viewer and preview", () => {
      const expectedMenu = [
         [
            { id: "checkbox properties", visible: false },
            { id: "checkbox show-format-pane", visible: false }
         ],
         [
            { id: "vs-object copy", visible: false },
            { id: "vs-object cut", visible: false },
            { id: "vs-object remove", visible: false },
            { id: "vs-object group", visible: false },
            { id: "vs-object ungroup", visible: false }
         ],
         [
            { id: "vs-object bring-forward", visible: false },
            { id: "vs-object bring-to-front", visible: false },
            { id: "vs-object send-backward", visible: false },
            { id: "vs-object send-to-back", visible: false }
         ]
      ];

      //check status in viewer
      const model = createModel();
      const actions1 = new CheckBoxActions(model, ViewerContextProviderFactory(false),
         false, null, null, popService);
      const menuActions1 = actions1.menuActions;

      expect(menuActions1).toMatchSnapshot();

      //check status in preview
      const model2 = createModel();
      const actions2 = new CheckBoxActions(model2, ViewerContextProviderFactory(true),
         false, null, null, popService);
      const menuActions2 = actions2.menuActions;

      expect(menuActions2).toMatchSnapshot();
   });

   //Bug #19986 should not display menu action when as data tip component
   it("should not display menu action when as data tip component", () => {
      const dataTipService: any = { isDataTip: jest.fn() };
      dataTipService.isDataTip.mockImplementation(() => true);
      const model = createModel();
      const actions = new CheckBoxActions(model, ViewerContextProviderFactory(false), false, null, dataTipService, popService);
      const menuActions = actions.menuActions;

      expect(menuActions.length).toBe(0);
   });
});