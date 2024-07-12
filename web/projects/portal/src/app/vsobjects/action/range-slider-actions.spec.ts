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
import { RangeSliderActions } from "./range-slider-actions";

describe("RangeSliderActions", () => {
   const createModel = () => TestUtils.createMockVSRangeSliderModel("VSRangeSlider1");

   //#10281
   it("check status of menu actions and toolbar actions of range slider in composer", () => {
      const expectedMenu = [
         [
            { id: "range-slider properties", visible: true },
            { id: "range-slider viewer-advanced-pane", visible: false },
            { id: "range-slider show-format-pane", visible: true },
            { id: "range-slider edit-range", visible: true },
            { id: "range-slider convert-to-selection-list", visible: false }
         ],
         [
            { id: "vs-object remove", visible: false },
            { id: "range-slider viewer-remove-from-container", visible: false }
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

      const expectedToolbar = [
         [
            //need update later
            { id: "range-slider unselect", visible: true },
         ]
      ];

      const model = createModel();
      const actions = new RangeSliderActions(model, ComposerContextProviderFactory());
      const menuActions = actions.menuActions;
      const toolbarActions = actions.toolbarActions;

      expect(menuActions).toMatchSnapshot();
      expect(toolbarActions).toMatchSnapshot();

      //check composite value
      model.composite = true;
      expect(menuActions[0].actions[3].visible()).toBeFalsy();
   });

   //#17058
   it("check status of menu actions and toolbar actions of range slider in viewer and preview", () => {
      const expectedMenu = [
         [
            { id: "range-slider properties", visible: false },
            { id: "range-slider viewer-advanced-pane", visible: false },
            { id: "range-slider show-format-pane", visible: false },
            { id: "range-slider edit-range", visible: true },
            { id: "range-slider convert-to-selection-list", visible: false }
         ],
         [
            { id: "vs-object remove", visible: false },
            { id: "range-slider viewer-remove-from-container", visible: false }
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

      const expectedToolbar = [
         [
            //need update later
            { id: "range-slider unselect", visible: true },
         ]
      ];

      //check status in viewer
      const model = createModel();
      const actions1 = new RangeSliderActions(model, ViewerContextProviderFactory(false));
      const menuActions1 = actions1.menuActions;
      const toolbarActions1 = actions1.toolbarActions;

      expect(menuActions1).toMatchSnapshot();
      expect(toolbarActions1).toMatchSnapshot();

      //check status in preview
      const model2 = createModel();
      const actions2 = new RangeSliderActions(model2, ViewerContextProviderFactory(true));
      const menuActions2 = actions2.menuActions;
      const toolbarActions2 = actions2.toolbarActions;

      expect(menuActions2).toMatchSnapshot();
      expect(toolbarActions2).toMatchSnapshot();

      //#18021, check status in preview when triggered by adhoc filter
      const expectedToolbar2 = [
         [
            { id: "range-slider unselect", visible: true },
         ]
      ];
      model2.adhocFilter = true;

      expect(toolbarActions2).toMatchSnapshot();

      //#18141, check edit action when in container
      model2.adhocFilter = false;
      model2.container = "aa";
      model2.containerType = "VSSelectionContainer";
      expect(menuActions2[0].actions[3].visible()).toBeTruthy();
      // TODO fix spec
      // expect(menuActions2[0].actions[1].visible()).toBeTruthy();
      // expect(menuActions2[1].actions[1].visible()).toBeTruthy();

      //check status when container not support remove child
      model2.supportRemoveChild = false;
      expect(menuActions2[0].actions[3].visible()).toBeTruthy();
      expect(menuActions2[0].actions[2].visible()).toBeFalsy();
      expect(menuActions2[1].actions[1].visible()).toBeFalsy();
   });

   //bug #18022, #18105, Bug #20859
   it("check status of menu actions and toolbar actions of range slider in composer when in selection container", () => {
      const expectedMenu = [
         [
            { id: "range-slider properties", visible: true },
            { id: "range-slider viewer-advanced-pane", visible: false },
            { id: "range-slider show-format-pane", visible: true },
            { id: "range-slider edit-range", visible: true },
            { id: "range-slider convert-to-selection-list", visible: true }
         ],
         [
            { id: "vs-object remove", visible: true },
            { id: "range-slider viewer-remove-from-container", visible: false }
         ]
      ];

      const expectedToolbar = [
         [
            { id: "range-slider unselect", visible: true },
         ]
      ];

      const model = createModel();
      model.container = "aa";
      model.containerType = "VSSelectionContainer";
      const actions = new RangeSliderActions(model, ComposerContextProviderFactory());
      const menuActions = actions.menuActions;
      const toolbarActions = actions.toolbarActions;

      expect(menuActions).toMatchSnapshot();
      expect(toolbarActions).toMatchSnapshot();

      //bug#18046, viewer remove action
      const actions2 = new RangeSliderActions(model, ViewerContextProviderFactory(false));
      const menuActions2 = actions2.menuActions;
      expect(menuActions2[1].actions[0].visible()).toBeFalsy();
      expect(menuActions2[1].actions[1].visible()).toBeTruthy();

      //check composite value
      model.composite = true;
      expect(menuActions[0].actions[3].visible()).toBeFalsy();
      //expect(menuActions[1].actions[3].visible()).toBeFalsy();
   });

   //Bug #19986 should not display menu action when as data tip component
   it("should not display menu action when as data tip component", () => {
      const dataTipService: any = { isDataTip: jest.fn() };
      dataTipService.isDataTip.mockImplementation(() => true);
      const model = createModel();
      const actions = new RangeSliderActions(model, ViewerContextProviderFactory(false), false, null, dataTipService);
      const menuActions = actions.menuActions;

      expect(menuActions.length).toBe(0);
   });
});