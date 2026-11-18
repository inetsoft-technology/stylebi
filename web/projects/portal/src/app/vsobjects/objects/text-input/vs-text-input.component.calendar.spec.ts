/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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

/**
 * The date calendar of a Date text input is opened through FixedDropdownService, which appends it
 * to document.body, outside the assembly wrapper. The popup must carry its own assembly's viz mark
 * (Feature #81379).
 */
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { ComponentFixture, TestBed, waitForAsync } from "@angular/core/testing";
import { FormsModule } from "@angular/forms";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { of, Subject } from "rxjs";
import { FirstDayOfWeekService } from "../../../common/services/first-day-of-week.service";
import { DropDownTestModule } from "../../../common/test/test-module";
import { TestUtils } from "../../../common/test/test-utils";
import { ViewsheetClientService } from "../../../common/viewsheet-client";
import { DebounceService } from "../../../widget/services/debounce.service";
import { ContextProvider } from "../../context-provider.service";
import { FormInputService } from "../../util/form-input.service";
import { DataTipService } from "../data-tip/data-tip.service";
import { PopComponentService } from "../data-tip/pop-component.service";
import { VSTextInput } from "./vs-text-input.component";

describe("VSTextInput calendar dark resolution", () => {
   let fixture: ComponentFixture<VSTextInput>;
   let textInput: VSTextInput;

   beforeEach(waitForAsync(() => {
      TestBed.configureTestingModule({
         imports: [FormsModule, VSTextInput, DropDownTestModule],
         providers: [
            PopComponentService,
            { provide: ViewsheetClientService, useValue: { sendEvent: vi.fn(), commands: of([]) } },
            { provide: FormInputService, useValue: { addPendingValue: vi.fn() } },
            { provide: DebounceService, useValue: { debounce: vi.fn() } },
            { provide: ContextProvider, useValue: { viewer: true, preview: false, binding: false,
               embedAssembly: false, vsWizard: false, vsWizardPreview: false } },
            { provide: DataTipService, useValue: { isDataTip: vi.fn(() => false), scrolled: new Subject<void>() } },
            { provide: NgbModal, useValue: {} },
            { provide: FirstDayOfWeekService, useValue: { getFirstDay: vi.fn(() => of({ isoFirstDay: 1 })) } },
         ],
         schemas: [NO_ERRORS_SCHEMA],
      });
      TestBed.compileComponents();

      fixture = TestBed.createComponent(VSTextInput);
      textInput = fixture.componentInstance;
      textInput.model = TestUtils.createMockVSTextInputModel("TextInput1");
      textInput.model.option = "Date";
   }));

   afterEach(() => {
      document.querySelectorAll("fixed-dropdown").forEach(e => e.remove());
   });

   function openCalendar(): HTMLElement {
      fixture.detectChanges();
      const trigger: HTMLElement = fixture.nativeElement.querySelector(".calendar-icon");
      trigger.dispatchEvent(new MouseEvent("click", { bubbles: true }));
      fixture.detectChanges();
      return document.querySelector(".dropdown-datepicker");
   }

   it("carries viz-dark and viz-modern from its own assembly", () => {
      textInput.model.vizModern = true;
      textInput.model.vizDark = true;

      const popup = openCalendar();

      expect(popup).not.toBeNull();
      expect(popup.classList.contains("viz-dark")).toBe(true);
      expect(popup.classList.contains("viz-modern")).toBe(true);
   });

   it("carries only viz-modern when its assembly is marked light", () => {
      textInput.model.vizModern = true;
      textInput.model.vizDark = false;

      const popup = openCalendar();

      expect(popup).not.toBeNull();
      expect(popup.classList.contains("viz-modern")).toBe(true);
      expect(popup.classList.contains("viz-dark")).toBe(false);
   });

   it("carries no viz class when its assembly is unmarked", () => {
      const popup = openCalendar();

      expect(popup).not.toBeNull();
      expect(popup.classList.contains("viz-modern")).toBe(false);
      expect(popup.classList.contains("viz-dark")).toBe(false);
   });
});
