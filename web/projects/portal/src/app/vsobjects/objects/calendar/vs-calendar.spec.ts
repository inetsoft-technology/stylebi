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
import { CommonModule } from "@angular/common";
import { HttpClientTestingModule } from "@angular/common/http/testing";
import { NO_ERRORS_SCHEMA, Renderer2 } from "@angular/core";
import { async, ComponentFixture, TestBed } from "@angular/core/testing";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { of as observableOf } from "rxjs";
import { FirstDayOfWeekService } from "../../../common/services/first-day-of-week.service";
import { TestUtils } from "../../../common/test/test-utils";
import { ViewsheetClientService } from "../../../common/viewsheet-client";
import { DefaultFocusDirective } from "../../../widget/directive/default-focus.directive";
import { FixedDropdownService } from "../../../widget/fixed-dropdown/fixed-dropdown.service";
import { InteractService } from "../../../widget/interact/interact.service";
import { InteractableDirective } from "../../../widget/interact/interactable.directive";
import { ContextProvider } from "../../context-provider.service";
import { SafeFontDirective } from "../../directives/safe-font.directive";
import { CheckFormDataService } from "../../util/check-form-data.service";
import { DataTipService } from "../data-tip/data-tip.service";
import { PopComponentService } from "../data-tip/pop-component.service";
import { VSPopComponentDirective } from "../data-tip/vs-pop-component.directive";
import { MiniToolbar } from "../mini-toolbar/mini-toolbar.component";
import { MonthCalendar } from "./month-calendar.component";
import { VSCalendar } from "./vs-calendar.component";
import { YearCalendar } from "./year-calendar.component";
import { GlobalSubmitService } from "../../util/global-submit.service";

let createModel = () => {
   return TestUtils.createMockVSCalendarModel("Calendar1");
};

describe("VSCalendar Unit Tests", () => {
   let viewsheetClientService: any;
   let fixture: ComponentFixture<VSCalendar>;
   let interactService: any;
   let dataTipService: any;
   let firstDayOfWeekService: any;
   let dropdownService: any;

   beforeEach(async(() => {
      viewsheetClientService = { sendEvent: jest.fn() };
      interactService = {
         addInteractable: jest.fn(),
         notify: jest.fn(),
         removeInteractable: jest.fn()
      };
      dataTipService = { isDataTip: jest.fn() };
      dropdownService = { };
      const contextProvider = {};
      const formDataService: any = {
         checkFormData: jest.fn(),
         removeObject: jest.fn(),
         addObject: jest.fn(),
         replaceObject: jest.fn()
      };
      firstDayOfWeekService = { getFirstDay: jest.fn() };
      firstDayOfWeekService.getFirstDay.mockImplementation(() => observableOf({}));

      TestBed.configureTestingModule({
         imports: [
            CommonModule,
            FormsModule,
            ReactiveFormsModule,
            HttpClientTestingModule,
            NgbModule
         ],
         declarations: [
            VSCalendar, MonthCalendar, YearCalendar, MiniToolbar, SafeFontDirective,
            VSPopComponentDirective, InteractableDirective, DefaultFocusDirective
         ],
         providers: [
            Renderer2,
            PopComponentService,
            GlobalSubmitService,
            { provide: ContextProvider, useValue: contextProvider },
            {provide: ViewsheetClientService, useValue: viewsheetClientService},
            {provide: InteractService, useValue: interactService},
            {provide: CheckFormDataService, useValue: formDataService},
            {provide: DataTipService, useValue: dataTipService},
            {provide: FirstDayOfWeekService, useValue: firstDayOfWeekService},
            {provide: FixedDropdownService, useValue: dropdownService},
            GlobalSubmitService
         ],
         schemas: [ NO_ERRORS_SCHEMA ]
      });
      TestBed.compileComponents();
      fixture = TestBed.createComponent(VSCalendar);
      fixture.componentInstance.model = createModel();
      fixture.detectChanges();
   }));

   // Bug #10035 change correct model when toggling calendar dropdown
   it("should change correct model when toggling dropdown", () => {
      fixture.componentInstance.toggleDropdown();
      expect(fixture.componentInstance.model.calendarsShown).toBeTruthy();
   });

   // Bug #16080 update the calendars selection string if the calendar is updated
   it("should change calendar title string after title is updated", () => {
      fixture.componentInstance.model.title = "Calendar02";
      fixture.componentInstance.updateTitle();
      fixture.detectChanges();

      fixture.whenStable().then(() => {
         expect(fixture.componentInstance.selectionTitle).toContain("Calendar02");
      });
   });
});
