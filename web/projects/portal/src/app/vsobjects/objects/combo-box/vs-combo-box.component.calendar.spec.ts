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
 * The date calendar of a Date/Calendar combo box is opened through FixedDropdownService, which
 * appends it to document.body, outside the assembly wrapper. Like the value list, it must carry its
 * own assembly's viz mark (Feature #81379).
 */
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { ComponentFixture, TestBed, waitForAsync } from "@angular/core/testing";
import { FormsModule } from "@angular/forms";
import { of, Subject } from "rxjs";
import { XSchema } from "../../../common/data/xschema";
import { FirstDayOfWeekService } from "../../../common/services/first-day-of-week.service";
import { DropDownTestModule } from "../../../common/test/test-module";
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

describe("VSComboBox calendar dark resolution", () => {
   let fixture: ComponentFixture<VSComboBox>;
   let comboBox: VSComboBox;

   beforeEach(waitForAsync(() => {
      const formDataService: any = { checkFormData: vi.fn() };
      const debounceService: any = { debounce: vi.fn((key, fn, delay, args) => fn(...args)) };
      const dataTipService: any = { isDataTip: vi.fn(), scrolled: new Subject<void>() };
      const firstDayOfWeekService: any = { getFirstDay: vi.fn(() => of({})) };

      TestBed.configureTestingModule({
         imports: [FormsModule, VSComboBox, VSPopComponentDirective, DropDownTestModule],
         providers: [
            PopComponentService,
            FormInputService,
            { provide: ContextProvider, useValue: {} },
            { provide: ViewsheetClientService, useValue: { sendEvent: vi.fn(), commands: of([]) } },
            { provide: CheckFormDataService, useValue: formDataService },
            { provide: DebounceService, useValue: debounceService },
            { provide: DataTipService, useValue: dataTipService },
            { provide: FirstDayOfWeekService, useValue: firstDayOfWeekService },
         ],
         schemas: [NO_ERRORS_SCHEMA],
      });
      TestBed.compileComponents();

      fixture = TestBed.createComponent(VSComboBox);
      comboBox = fixture.componentInstance;
      comboBox.model = TestUtils.createMockVSComboBoxModel("Combo1");
      comboBox.model.calendar = true;
      comboBox.model.dataType = XSchema.DATE;
   }));

   afterEach(() => {
      document.querySelectorAll("fixed-dropdown").forEach(e => e.remove());
      document.body.classList.remove("viz-shell-dark");
   });

   function openCalendar(): HTMLElement {
      fixture.detectChanges();
      const trigger: HTMLElement = fixture.nativeElement.querySelector(".calendar-icon");
      trigger.dispatchEvent(new MouseEvent("click", { bubbles: true }));
      fixture.detectChanges();
      return document.querySelector(".dropdown-datepicker");
   }

   it("carries viz-dark and viz-modern from its own assembly", () => {
      comboBox.model.vizModern = true;
      comboBox.model.vizDark = true;

      const popup = openCalendar();

      expect(popup).not.toBeNull();
      expect(popup.classList.contains("viz-dark")).toBe(true);
      expect(popup.classList.contains("viz-modern")).toBe(true);
   });

   it("carries only viz-modern when its assembly is marked light", () => {
      comboBox.model.vizModern = true;
      comboBox.model.vizDark = false;

      const popup = openCalendar();

      expect(popup).not.toBeNull();
      expect(popup.classList.contains("viz-modern")).toBe(true);
      expect(popup.classList.contains("viz-dark")).toBe(false);
   });

   it("carries no viz class when its assembly is unmarked, whatever the org shell", () => {
      document.body.classList.add("viz-shell-dark");

      const popup = openCalendar();

      expect(popup).not.toBeNull();
      expect(popup.classList.contains("viz-modern")).toBe(false);
      expect(popup.classList.contains("viz-dark")).toBe(false);
   });
});
