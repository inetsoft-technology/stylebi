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
import { CalendarActions } from "./calendar-actions";

describe("CalendarActions", () => {
   const createModel = () => TestUtils.createMockVSCalendarModel("Calendar1");

   const popService: any = { getPopComponent: jest.fn() };
   popService.getPopComponent.mockImplementation(() => "");

   it("check status of menu actions and toolbar actions of calendar in composer", () => {
      const expectedMenu = [
         [
            { id: "calendar properties", visible: true },
            { id: "calendar show-format-pane", visible: true }
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
            { id: "calendar toggle-year", visible: true },
            { id: "calendar toggle-double-calendar", visible: true },
            { id: "calendar clear", visible: true },
            { id: "calendar toggle-range-comparison", visible: false },
            { id: "calendar multi-select", visible: false },
            { id: "calendar apply", visible: false }
         ]
      ];

      const model = createModel();
      const actions = new CalendarActions(model, ComposerContextProviderFactory(), false, null, null, popService);
      const menuActions = actions.menuActions;
      const toolbarActions = actions.toolbarActions;

      expect(menuActions).toMatchSnapshot();
      expect(toolbarActions).toMatchSnapshot();
      expect(TestUtils.toString(toolbarActions[1].actions[0].label())).toBe("Switch To Year View");
      expect(TestUtils.toString(toolbarActions[1].actions[1].label())).toBe("Switch To Range");
   });

   it("check status of menu actions and toolbar actions of calendar in viewer and preview", () => {
      const expectedMenu = [
         [
            { id: "calendar properties", visible: false },
            { id: "calendar show-format-pane", visible: false }
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
            { id: "calendar toggle-year", visible: true },
            { id: "calendar toggle-double-calendar", visible: true },
            { id: "calendar clear", visible: true },
            { id: "calendar toggle-range-comparison", visible: false },
            { id: "calendar multi-select", visible: false },
            { id: "calendar apply", visible: false }
         ]
      ];

      //check status in viewer
      const model = createModel();
      const actions1 = new CalendarActions(model, ViewerContextProviderFactory(false), false, null, null, popService);
      const menuActions1 = actions1.menuActions;
      const toolbarActions1 = actions1.toolbarActions;

      expect(menuActions1).toMatchSnapshot();
      expect(toolbarActions1).toMatchSnapshot();

      //check status in preview
      const model2 = createModel();
      const actions2 = new CalendarActions(model2, ViewerContextProviderFactory(true), false, null, null, popService);
      const menuActions2 = actions2.menuActions;
      const toolbarActions2 = actions2.toolbarActions;

      expect(menuActions2).toMatchSnapshot();
      expect(toolbarActions2).toMatchSnapshot();
   });

   it("check status of toolbar actions of calendar in composer when is dropdown or double calendar", () => {
      //check toolbar when is dropdown
      const expectedToolbar1 = [
         [
            { id: "calendar toggle-year", visible: false },
            { id: "calendar toggle-double-calendar", visible: false },
            { id: "calendar clear", visible: true },
            { id: "calendar toggle-range-comparison", visible: false },
            { id: "calendar multi-select", visible: false },
            { id: "calendar apply", visible: false }
         ]
      ];

      const model = createModel();
      const actions = new CalendarActions(model, ComposerContextProviderFactory(), false, null, null, popService);
      const toolbarActions = actions.toolbarActions;
      model.dropdownCalendar = true;
      model.calendarsShown = false;

      expect(toolbarActions).toMatchSnapshot();

      //check toolbar when is double year calendar
      const expectedToolbar2 = [
         [
            { id: "calendar toggle-year", visible: true },
            { id: "calendar toggle-double-calendar", visible: true },
            { id: "calendar clear", visible: true },
            { id: "calendar toggle-range-comparison", visible: true },
            { id: "calendar multi-select", visible: false },
            { id: "calendar apply", visible: true }
         ]
      ];
      model.dropdownCalendar = false;
      model.calendarsShown = true;
      model.yearView = true;
      model.doubleCalendar = true;

      expect(toolbarActions).toMatchSnapshot();
      expect(TestUtils.toString(toolbarActions[1].actions[0].label())).toBe("Switch To Month View");
      expect(TestUtils.toString(toolbarActions[1].actions[1].label())).toBe("Switch To Simple View");
      expect(TestUtils.toString(toolbarActions[1].actions[3].label())).toBe("Switch To Comparison Mode");
   });

   //Bug #19986 should not display menu action when as data tip component
   it("should not display menu action when as data tip component", () => {
      const dataTipService: any = { isDataTip: jest.fn() };
      dataTipService.isDataTip.mockImplementation(() => true);
      const model = createModel();
      const actions = new CalendarActions(model, ViewerContextProviderFactory(false), false, null, dataTipService, popService);
      const menuActions = actions.menuActions;

      expect(menuActions.length).toBe(0);
   });
});