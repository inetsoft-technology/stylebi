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
import { TestUtils } from "../../common/test/test-utils";
import { ComposerContextProviderFactory, ViewerContextProviderFactory } from "../context-provider.service";
import { GaugeActions } from "./gauge-actions";

describe("Gauge actions", () => {
   const createModel = () => TestUtils.createMockVSGaugeModel("Gauge1");

   it("should create composer toolbar actions", () => {
      const actions = new GaugeActions(createModel(), ComposerContextProviderFactory());
      const toolbarActions = actions.toolbarActions;
      expect(toolbarActions).toBeTruthy();
      expect(toolbarActions.length).toBe(0);
   });

   // Bug #10057 add condition dialog and hyperlink dialog to gauge actions.
   it("should create composer menu actions", () => {
      const expected = [
         [
            { id: "gauge properties", visible: true },
            { id: "gauge show-format-pane", visible: true },
            { id: "gauge conditions", visible: true }
         ],
         [
            { id: "gauge hyperlink", visible: true }
         ],
         [
            { id: "gauge annotate", visible: false }
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
      const actions = new GaugeActions(createModel(), ComposerContextProviderFactory());
      const menuActions = actions.menuActions;

      expect(menuActions).toMatchSnapshot();
   });

   // Bug #17187 should display annotation action when in viewer and has security
   it("check status of menu actions in viewer and preview when has security", () => {
      const expectedMenu = [
         [
            { id: "gauge properties", visible: false },
            { id: "gauge show-format-pane", visible: false },
            { id: "gauge conditions", visible: false }
         ],
         [
            { id: "gauge hyperlink", visible: false }
         ],
         [
            { id: "gauge annotate", visible: true }
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
      const actions1 = new GaugeActions(model, ViewerContextProviderFactory(false), true);
      const menuActions1 = actions1.menuActions;

      expect(menuActions1).toMatchSnapshot();

      //check status in preview
      const model2 = createModel();
      const actions2 = new GaugeActions(model2, ViewerContextProviderFactory(true), true);
      const menuActions2 = actions2.menuActions;

      expect(menuActions2).toMatchSnapshot();

      // Bug #21151 should not display annotate action when assembly is not enabled
      model.enabled = false;
      expect(menuActions1[2].actions[0].visible()).toBeFalsy();
   });

   // Bug #17442 gauge click action
   it("should display show hyperlink in viewer when has hyperlink", () => {
      const model = createModel();
      model.hyperlinks = TestUtils.createMockHyperlinkModel();
      const actions = new GaugeActions(model, ViewerContextProviderFactory(false));
      const clickAction = actions.clickAction;

      expect(TestUtils.toString(clickAction.label())).toBe("Show Hyperlinks");
      expect(clickAction.visible()).toBeTruthy();
   });

   //Bug #19986 should not display menu action when as data tip component
   it("should not display menu action when as data tip component", () => {
      const dataTipService: any = { isDataTip: jest.fn() };
      dataTipService.isDataTip.mockImplementation(() => true);
      const model = createModel();
      const actions = new GaugeActions(model, ViewerContextProviderFactory(false), false, null, dataTipService);
      const menuActions = actions.menuActions;

      expect(menuActions.length).toBe(0);
   });
});