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
import { DebugElement, NO_ERRORS_SCHEMA } from "@angular/core";
import { async, ComponentFixture, TestBed } from "@angular/core/testing";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { By } from "@angular/platform-browser";
import { NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { of as observableOf } from "rxjs";
import { FirstDayOfWeekService } from "../../../common/services/first-day-of-week.service";
import { DropDownTestModule } from "../../../common/test/test-module";
import { TestUtils } from "../../../common/test/test-utils";
import { ComponentTool } from "../../../common/util/component-tool";
import { ViewsheetClientService } from "../../../common/viewsheet-client";
import { DefaultFocusDirective } from "../../../widget/directive/default-focus.directive";
import { FixedDropdownDirective } from "../../../widget/fixed-dropdown/fixed-dropdown.directive";
import { DebounceService } from "../../../widget/services/debounce.service";
import { ContextProvider } from "../../context-provider.service";
import { SafeFontDirective } from "../../directives/safe-font.directive";
import { VSTextInputModel } from "../../model/vs-text-input-model";
import { FormInputService } from "../../util/form-input.service";
import { AppErrorMessage } from "../app-error-message.component";
import { DataTipService } from "../data-tip/data-tip.service";
import { PopComponentService } from "../data-tip/pop-component.service";
import { VSPopComponentDirective } from "../data-tip/vs-pop-component.directive";
import { VSTextInput } from "./vs-text-input.component";

const createModel: () => VSTextInputModel = () => {
   return Object.assign({
      active: true,
      container: null,
      message: null,
      pattern: null,
      prompt: "TextInput",
      text: "",
      multiLine: false,
      option: "Text",
      max: "",
      min: "",
      insetStyle: false,
      refresh: true,
      defaultText: ""
   }, TestUtils.createMockVSObjectModel("VSTextInput", "TextInput1"));
};

describe("VS Text Input Component Unit Test", () => {
   let fixture: ComponentFixture<VSTextInput>;
   let textInput: VSTextInput;
   let input: DebugElement;
   let textarea: DebugElement;
   let debounceService: any;
   let dataTipService: any;
   let firstDayOfWeekService: any;

   beforeEach(async(() => {
      const viewsheetClientService = {};
      debounceService = { debounce: jest.fn((key, fn, delay, args) => fn(...args)) };
      dataTipService = { isDataTip: jest.fn() };
      const contextProvider = {};
      firstDayOfWeekService = { getFirstDay: jest.fn() };
      firstDayOfWeekService.getFirstDay.mockImplementation(() => observableOf({}));

      TestBed.configureTestingModule({
         imports: [ ReactiveFormsModule, FormsModule, NgbModule, DropDownTestModule ],
         declarations: [
            VSTextInput, AppErrorMessage, VSPopComponentDirective,
            FixedDropdownDirective, SafeFontDirective, DefaultFocusDirective
         ],
         schemas: [NO_ERRORS_SCHEMA],
         providers: [
            PopComponentService,
            FormInputService,
            { provide: ContextProvider, useValue: contextProvider },
            { provide: ViewsheetClientService, useValue: viewsheetClientService },
            { provide: DebounceService, useValue: debounceService },
            { provide: DataTipService, useValue: dataTipService },
            { provide: FirstDayOfWeekService, useValue: firstDayOfWeekService }
         ],
      });
      TestBed.compileComponents();

      fixture = TestBed.createComponent(VSTextInput);
      textInput = fixture.componentInstance;
      textInput.model = createModel();
      fixture.detectChanges();
   }));

   it("should render input element when multiple line checkbox is not checked", () => {
      input = fixture.debugElement.query(By.css("input"));
      expect(input).not.toBe(null);
      textarea = fixture.debugElement.query(By.css("textarea"));
      expect(textarea).toBe(null);
   });

   //Bug #19100 manual input for date type
   it("manual input for date type", () => {
      textInput.model.option = "Date";
      fixture.detectChanges();
      let dateInput = fixture.debugElement.query(By.css("input")).nativeElement;
      dateInput.value = "2017-01-01";
      dateInput.dispatchEvent(new Event("input"));
      fixture.detectChanges();

      let showMessageDialog = jest.spyOn(ComponentTool, "showMessageDialog");
      showMessageDialog.mockImplementation(() => Promise.resolve("ok"));

      expect(showMessageDialog).not.toHaveBeenCalled();
      expect(textInput.model.text).toBe("2017-01-01");
   });

   //Bug #20297 text input border
   xit("check text input border", () => { // broken test
      textInput.model.objectFormat.border = {
         bottom: "3px solid #339966", top: "0px none #dadada",
         left: "0px none #ff0000", right: "2px solid #ff0000"};
      fixture.detectChanges();

      let tInput = fixture.debugElement.query(By.css("div.TextInput input"));
      expect(tInput.styles["border-style"]).toBe("none solid solid none");
      expect(tInput.styles["border-width"]).toBe("0px 2px 3px 0px");
      expect(tInput.styles["border-color"]).toBe("rgb(218, 218, 218) rgb(255, 0, 0) rgb(51, 153, 102)");
   });
});
