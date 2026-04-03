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
import { ChangeDetectorRef, NO_ERRORS_SCHEMA } from "@angular/core";
import { async, ComponentFixture, TestBed } from "@angular/core/testing";
import { TestUtils } from "../../../common/test/test-utils";
import { ViewsheetClientService } from "../../../common/viewsheet-client";
import { FixedDropdownService } from "../../../widget/fixed-dropdown/fixed-dropdown.service";
import { AssemblyActionFactory } from "../../action/assembly-action-factory.service";
import { ContextProvider } from "../../context-provider.service";
import { DataTipService } from "../data-tip/data-tip.service";
import { PopComponentService } from "../data-tip/pop-component.service";
import { SelectionMobileService } from "../selection/services/selection-mobile.service";
import { VSViewsheet } from "./vs-viewsheet.component";
import { VSSelectionListModel } from "../../model/vs-selection-list-model";
import { VSCalendarModel } from "../../model/calendar/vs-calendar-model";
import { VSTabModel } from "../../model/vs-tab-model";

describe("VSViewsheet Unit Tests", () => {
   let fixture: ComponentFixture<VSViewsheet>;
   let component: VSViewsheet;

   beforeEach(async(() => {
      const viewsheetClientService = { sendEvent: jest.fn() };
      const dataTipService = {
         isDataTip: jest.fn(),
         hasDataTipShowing: jest.fn(() => false)
      };
      const contextProvider = { viewer: true, preview: false, composer: false, binding: false };

      TestBed.configureTestingModule({
         declarations: [VSViewsheet],
         providers: [
            { provide: ViewsheetClientService, useValue: viewsheetClientService },
            { provide: AssemblyActionFactory, useValue: {} },
            { provide: FixedDropdownService, useValue: {} },
            { provide: ContextProvider, useValue: contextProvider },
            { provide: DataTipService, useValue: dataTipService },
            { provide: PopComponentService, useValue: { isCurrentPopComponent: jest.fn(() => false), hasPopUpComponentShowing: jest.fn(() => false) } },
            { provide: SelectionMobileService, useValue: {} },
            { provide: ChangeDetectorRef, useValue: { detectChanges: jest.fn() } }
         ],
         schemas: [NO_ERRORS_SCHEMA]
      });
      TestBed.compileComponents();

      fixture = TestBed.createComponent(VSViewsheet);
      component = fixture.componentInstance;

      const model = TestUtils.createMockVSViewsheetModel("Viewsheet1");
      model.embeddedOpenIconVisible = true;
      model.iconHeight = 15;
      model.objectFormat.top = 100;
      model.objectFormat.left = 0;
      component.model = model;
   }));

   function createSelectionInBottomTab(name: string, overrides: Partial<VSSelectionListModel> = {}): VSSelectionListModel {
      const sel = TestUtils.createMockVSSelectionListModel(name);
      sel.container = "Tab1";
      sel.containerType = "VSTab";
      sel.dropdown = true;
      sel.hidden = false;
      sel.cellHeight = 18;
      sel.listHeight = 10;
      sel.objectFormat.top = 200;
      sel.objectFormat.left = 0;
      sel.objectFormat.width = 150;
      return Object.assign(sel, overrides);
   }

   function createBottomTab(name: string): VSTabModel {
      const tab = TestUtils.createMockVSTabModel(name);
      tab.absoluteName = name;
      tab.bottomTabs = true;
      return tab;
   }

   function createCalendarInBottomTab(name: string, overrides: Partial<VSCalendarModel> = {}): VSCalendarModel {
      const cal = TestUtils.createMockVSCalendarModel(name);
      cal.container = "Tab1";
      cal.containerType = "VSTab";
      cal.dropdownCalendar = true;
      cal.calendarsShown = true;
      cal.objectFormat.top = 200;
      cal.objectFormat.left = 0;
      cal.objectFormat.width = 150;
      return Object.assign(cal, overrides);
   }

   describe("showIconContainer with child dropdown expansion", () => {
      it("should show icon when selection dropdown is not expanded (hidden=true)", () => {
         const tab = createBottomTab("Tab1");
         const sel = createSelectionInBottomTab("Sel1", { hidden: true });
         component.vsObjects = [tab, sel];
         expect(component.showIconContainer).toBeTruthy();
      });

      it("should show icon when selection is not a dropdown", () => {
         const tab = createBottomTab("Tab1");
         const sel = createSelectionInBottomTab("Sel1", { dropdown: false });
         component.vsObjects = [tab, sel];
         expect(component.showIconContainer).toBeTruthy();
      });

      it("should hide icon when expanded dropdown overlaps vertically", () => {
         const tab = createBottomTab("Tab1");
         // expandedTop = 200 - 18*10 = 20; 20 > iconBottom(5), so still no overlap
         // use a taller list to force overlap: expandedTop = 200 - 18*12 = -16 < 5
         const sel = createSelectionInBottomTab("Sel1", { listHeight: 12 });
         component.vsObjects = [tab, sel];
         expect(component.showIconContainer).toBeFalsy();
      });

      it("should show icon when expanded dropdown does not reach icon vertically", () => {
         const tab = createBottomTab("Tab1");
         // expandedTop = 200 - 18*10 = 20; 20 >= iconBottom(5), no overlap
         const sel = createSelectionInBottomTab("Sel1", { listHeight: 10 });
         component.vsObjects = [tab, sel];
         expect(component.showIconContainer).toBeTruthy();
      });

      it("should show icon when expanded dropdown is to the right of icon", () => {
         const tab = createBottomTab("Tab1");
         const sel = createSelectionInBottomTab("Sel1", {
            listHeight: 12,
            objectFormat: Object.assign(TestUtils.createMockVSFormatModel(), {
               top: 200, left: 100, width: 150, height: 200,
               border: TestUtils.createMockVSFormatModel().border
            })
         } as any);
         sel.objectFormat.left = 100;
         component.vsObjects = [tab, sel];
         expect(component.showIconContainer).toBeTruthy();
      });

      it("should show icon when selection is not in a bottom tab container", () => {
         const tab = TestUtils.createMockVSTabModel("Tab1");
         tab.absoluteName = "Tab1";
         tab.bottomTabs = false;
         const sel = createSelectionInBottomTab("Sel1", { listHeight: 12 });
         component.vsObjects = [tab, sel];
         expect(component.showIconContainer).toBeTruthy();
      });

      it("should hide icon when calendar dropdown overlaps", () => {
         const tab = createBottomTab("Tab1");
         // CALENDAR_BODY_HEIGHT = 144; expandedTop = 100 - 144 = -44 < 5
         const cal = createCalendarInBottomTab("Cal1", { objectFormat: Object.assign(TestUtils.createMockVSFormatModel(), { top: 100, left: 0, width: 150, height: 20, border: TestUtils.createMockVSFormatModel().border }) } as any);
         component.vsObjects = [tab, cal];
         expect(component.showIconContainer).toBeFalsy();
      });

      it("should show icon when calendar dropdown is not shown", () => {
         const tab = createBottomTab("Tab1");
         const cal = createCalendarInBottomTab("Cal1", { calendarsShown: false });
         component.vsObjects = [tab, cal];
         expect(component.showIconContainer).toBeTruthy();
      });

      it("should show icon when no child objects exist", () => {
         component.vsObjects = [];
         expect(component.showIconContainer).toBeTruthy();
      });

      it("should account for mobile cell height", () => {
         const tab = createBottomTab("Tab1");
         // desktop: expandedTop = 200 - 18*10 = 20, no overlap
         // mobile: expandedTop = 200 - 40*10 = -200, overlap
         const sel = createSelectionInBottomTab("Sel1", { cellHeight: 18, listHeight: 10 });
         component.vsObjects = [tab, sel];

         (component as any).mobileDevice = false;
         expect(component.showIconContainer).toBeTruthy();

         (component as any).mobileDevice = true;
         expect(component.showIconContainer).toBeFalsy();
      });
   });
});
