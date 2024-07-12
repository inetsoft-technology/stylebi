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
import { TextActions } from "./text-actions";

describe("TextActions", () => {
   const createModel = () => TestUtils.createMockVSTextModel("Text1");

   it("should create composer toolbar actions", () => {
      const actions = new TextActions(createModel(), ComposerContextProviderFactory());
      const toolbarActions = actions.toolbarActions;
      expect(toolbarActions).toBeTruthy();
      expect(toolbarActions.length).toBe(0);
   });

   it("should composer create menu actions", () => {
      const expected = [
         [
            { id: "text properties", visible: true },
            { id: "text show-format-pane", visible: true },
            { id: "text conditions", visible: true }
         ],
         [
            { id: "text hyperlink", visible: true },
            { id: "text highlight", visible: true }
         ],
         [
            { id: "text annotate", visible: false }
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
         ],
         [
            { id: "annotation edit", visible: false },
            { id: "annotation format", visible: false },
            { id: "annotation remove", visible: false }
         ]
      ];

      const actions = new TextActions(createModel(), ComposerContextProviderFactory());
      const menuActions = actions.menuActions;

      expect(menuActions).toMatchSnapshot();
   });

   // Bug #17187 should display annotation action when in viewer and has security
   it("check status of menu actions in viewer and preview when has security", () => {
      const expectedMenu = [
         [
            { id: "text properties", visible: false },
            { id: "text show-format-pane", visible: false },
            { id: "text conditions", visible: false }
         ],
         [
            { id: "text hyperlink", visible: false },
            { id: "text highlight", visible: false }
         ],
         [
            { id: "text annotate", visible: true }
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
         ],
         [
            { id: "annotation edit", visible: false },
            { id: "annotation format", visible: false },
            { id: "annotation remove", visible: false }
         ]
      ];

      //check status in viewer
      const model = createModel();
      const actions1 = new TextActions(model, ViewerContextProviderFactory(false), true);
      const menuActions1 = actions1.menuActions;

      expect(menuActions1).toMatchSnapshot();

      //check status in preview
      const model2 = createModel();
      const actions2 = new TextActions(model2, ViewerContextProviderFactory(true), true);
      const menuActions2 = actions2.menuActions;

      expect(menuActions2).toMatchSnapshot();

      // Bug #21151 should not display annotate action when assembly is not enabled
      model.enabled = false;
      expect(menuActions1[2].actions[0].visible()).toBeFalsy();
   });

   // Bug #17445 text click action
   it("should display show hyperlink in viewer when has hyperlink", () => {
      const model = createModel();
      model.hyperlinks = TestUtils.createMockHyperlinkModel();
      const actions = new TextActions(model, ViewerContextProviderFactory(false));
      const clickAction = actions.clickAction;

      expect(TestUtils.toString(clickAction.label())).toBe("Show Hyperlinks");
      expect(clickAction.visible()).toBeTruthy();
   });

   //Bug #19986 should not display menu action when as data tip component
   it("should not display menu action when as data tip component", () => {
      const dataTipService: any = { isDataTip: jest.fn() };
      dataTipService.isDataTip.mockImplementation(() => true);
      const model = createModel();
      const actions = new TextActions(model, ViewerContextProviderFactory(false), false, null, dataTipService);
      const menuActions = actions.menuActions;

      expect(menuActions.length).toBe(0);
   });
});