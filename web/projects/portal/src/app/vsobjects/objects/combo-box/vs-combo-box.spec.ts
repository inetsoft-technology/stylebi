/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { async, ComponentFixture, TestBed } from "@angular/core/testing";
import { FormsModule } from "@angular/forms";
import { of as observableOf } from "rxjs";
import { XSchema } from "../../../common/data/xschema";
import { FirstDayOfWeekService } from "../../../common/services/first-day-of-week.service";
import { TestUtils } from "../../../common/test/test-utils";
import { ViewsheetClientService } from "../../../common/viewsheet-client";
import { DebounceService } from "../../../widget/services/debounce.service";
import { ContextProvider } from "../../context-provider.service";
import { CheckFormDataService } from "../../util/check-form-data.service";
import { FormInputService } from "../../util/form-input.service";
import { DataTipService } from "../data-tip/data-tip.service";
import { PopComponentService } from "../data-tip/pop-component.service";
import { VSPopComponentDirective } from "../data-tip/vs-pop-component.directive";
import { VSComboBox } from "./vs-combo-box.component";

describe("VS Combo Box Test", () => {
   let fixture: ComponentFixture<VSComboBox>;
   let comboBox: VSComboBox;
   let viewsheetClientService: any;
   let debounceService: any;
   let dataTipService: any;
   let firstDayOfWeekService: any;

   beforeEach(async(() => {
      const formDataService: any = {
         checkFormData: jest.fn(),
         removeObject: jest.fn(),
         addObject: jest.fn(),
         replaceObject: jest.fn()
      };
      debounceService = { debounce: jest.fn((key, fn, delay, args) => fn(...args)) };
      dataTipService = { isDataTip: jest.fn() };
      const contextProvider = {};
      firstDayOfWeekService = { getFirstDay: jest.fn(() => observableOf({})) };

      TestBed.configureTestingModule({
         imports: [
            FormsModule
         ],
         declarations: [
            VSComboBox, VSPopComponentDirective
         ],
         providers: [
            PopComponentService,
            FormInputService,
            { provide: ContextProvider, useValue: contextProvider },
            {provide: ViewsheetClientService, useValue: viewsheetClientService},
            {provide: CheckFormDataService, useValue: formDataService},
            {provide: DebounceService, useValue: debounceService},
            {provide: DataTipService, useValue: dataTipService},
            {provide: FirstDayOfWeekService, useValue: firstDayOfWeekService}
         ],
         schemas: [NO_ERRORS_SCHEMA]
      });
      TestBed.compileComponents();

      viewsheetClientService = {
         sendEvent: jest.fn(),
         commands: observableOf([])
      };
      fixture = TestBed.createComponent(VSComboBox);
      comboBox = <VSComboBox> fixture.componentInstance;
      comboBox.model = TestUtils.createMockVSComboBoxModel("Combo1");
   }));

   // Bug #17282 should show date format for date combobox
   it("should show date format for date combobox", () => {
      comboBox.model.dataType = XSchema.DATE;
      comboBox.model.editable = true;
      fixture.detectChanges();
      let inputField: HTMLElement = fixture.nativeElement.querySelector("input.standard-input");
      expect(inputField.getAttribute("placeholder")).toBe("yyyy-MM-dd");

      comboBox.model.dataType = XSchema.TIME;
      fixture.detectChanges();
      expect(inputField.getAttribute("placeholder")).toBe("HH:mm:ss AM[PM]");

      comboBox.model.dataType = XSchema.TIME_INSTANT;
      fixture.detectChanges();
      expect(inputField.getAttribute("placeholder")).toBe("yyyy-MM-dd");
   });

   //Bug #18439 should apply H aligment
   //TODO, dropdpwn pane  did not apply aligment and border attribute
   it("should apply H aligment on combobox", () => {
      let vsformat = TestUtils.createMockVSFormatModel();
      vsformat.hAlign = "right";
      comboBox.model.objectFormat = vsformat;

      fixture.detectChanges();
      let select = fixture.nativeElement.querySelector("select");
      expect(select.style["text-align-last"]).toEqual("right");
   });
});
