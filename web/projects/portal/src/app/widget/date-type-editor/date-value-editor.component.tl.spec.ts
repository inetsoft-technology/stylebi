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
 * DateValueEditorComponent — single pass
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] — propagateDateChange: two-digit year expansion + onChange emit
 *   Group 2 [Risk 3] — change(): invalid string clears date
 *   Group 3 [Risk 3] — initCloseDatepickerListener: document mouseup listener removed after use
 *   Group 4 [Risk 2] — writeValue / model setter / setDisabledState
 *   Group 5 [Risk 2] — year boundary (0 / 99 / 100), model stale state, changeDate dedup
 *
 * toggleDatepicker / initDateValue empty-field → today: intentional; see class JSDoc.
 * Invalid free-text (e.g. "not-a-date"): no production effect; see class JSDoc — not tested here.
 *
 * Direct instantiation — no datepicker DOM.
 */

import { ElementRef, Renderer2 } from "@angular/core";
import { NgbDateParserFormatter, NgbDateStruct } from "@ng-bootstrap/ng-bootstrap";
import { DateTypeFormatter } from "../../../../../shared/util/date-type-formatter";
import { DateValueEditorComponent } from "./date-value-editor.component";

function createEditor() {
   const formatter: NgbDateParserFormatter = {
      format: (date: NgbDateStruct) =>
         `${date.year}-${String(date.month).padStart(2, "0")}-${String(date.day).padStart(2, "0")}`,
      parse: (value: string) => {
         const parts = value.split("-").map(Number);
         return parts.length === 3 ? { year: parts[0], month: parts[1], day: parts[2] } : null;
      },
   };
   const renderer = { listen: vi.fn(() => vi.fn()) };
   const element = { nativeElement: document.createElement("div") };
   const comp = new DateValueEditorComponent(
      formatter,
      renderer as unknown as Renderer2,
      element as ElementRef,
   );
   comp.ngbDatepicker = { close: vi.fn(), toggle: vi.fn() };
   return { comp, renderer };
}

describe("DateValueEditorComponent — propagateDateChange — year expansion [Group 1, Risk 3]", () => {

   it("should expand two-digit year before emitting onChange", () => {
      const { comp } = createEditor();
      const onChange = vi.fn();
      comp.registerOnChange(onChange);
      const century = Math.floor(new Date().getFullYear() / 100) * 100;

      comp.date = { year: 25, month: 6, day: 15 };
      comp.propagateDateChange(false);

      expect(comp.date.year).toBe(century + 25);
      expect(onChange).toHaveBeenCalled();
   });

   it("should emit null when date is cleared", () => {
      const { comp } = createEditor();
      const onChange = vi.fn();
      comp.registerOnChange(onChange);
      comp.date = { year: 2024, month: 1, day: 1 };

      comp.date = null;
      comp.propagateDateChange();

      expect(onChange).toHaveBeenCalledWith(null);
   });
});

describe("DateValueEditorComponent — change — invalid input [Group 2, Risk 3]", () => {

   it("should clear date when string cannot be parsed to a date struct", () => {
      const { comp } = createEditor();
      const onChange = vi.fn();
      comp.registerOnChange(onChange);
      comp.date = { year: 2024, month: 3, day: 10 };

      comp.change("invalid");

      expect(comp.date).toBeNull();
      expect(onChange).toHaveBeenCalledWith(null);
   });
});

describe("DateValueEditorComponent — document listener cleanup [Group 3, Risk 3]", () => {

   // 🔁 Regression-sensitive: leaked document mouseup listeners cause jank on every click
   it("should remove document listener after outside mouseup", () => {
      const { comp, renderer } = createEditor();
      const cleanup = vi.fn();
      vi.mocked(renderer.listen).mockReturnValue(cleanup);
      const outside = document.createElement("span");
      document.body.appendChild(outside);

      comp.initCloseDatepickerListener();
      const handler = (vi.mocked(renderer.listen).mock.calls[0] as unknown[])[2] as (e: MouseEvent) => void;
      handler({ target: outside } as unknown as MouseEvent);

      expect(comp.ngbDatepicker.close).toHaveBeenCalled();
      expect(cleanup).toHaveBeenCalled();
      expect(comp["closeDatepickerListener"]).toBeNull();
      outside.remove();
   });

   it("should not register a second document listener while one is active", () => {
      const { comp, renderer } = createEditor();
      vi.mocked(renderer.listen).mockReturnValue(vi.fn());

      comp.initCloseDatepickerListener();
      comp.initCloseDatepickerListener();

      expect(renderer.listen).toHaveBeenCalledTimes(1);
   });
});

describe("DateValueEditorComponent — CVA and inputs [Group 4, Risk 2]", () => {

   it("should parse writeValue using format transform", () => {
      const { comp } = createEditor();
      comp.format = DateTypeFormatter.ISO_8601_DATE_FORMAT;

      comp.writeValue("2024-05-20");

      expect(comp.date).toEqual({ year: 2024, month: 5, day: 20 });
   });

   it("should initialize date from model input setter", () => {
      const { comp } = createEditor();
      comp.model = "2023-12-01";

      expect(comp.date).toEqual({ year: 2023, month: 12, day: 1 });
      expect(comp.initDate).toEqual(comp.date);
   });

   it("should update disabled flag via setDisabledState", () => {
      const { comp } = createEditor();

      comp.setDisabledState(true);

      expect(comp.disabled).toBe(true);
   });
});

describe("DateValueEditorComponent — year boundary expansion [Group 5, Risk 2]", () => {

   it("should expand year 99 to current century", () => {
      const { comp } = createEditor();
      const century = Math.floor(new Date().getFullYear() / 100) * 100;

      comp.date = { year: 99, month: 1, day: 1 };
      comp.propagateDateChange(false);

      expect(comp.date.year).toBe(century + 99);
   });

   it("should not expand year 100", () => {
      const { comp } = createEditor();

      comp.date = { year: 100, month: 1, day: 1 };
      comp.propagateDateChange(false);

      expect(comp.date.year).toBe(100);
   });

   // Documents contract: year 0 is falsy in `this.date.year &&`, so expansion is skipped.
   it("should not expand year 0 due to falsy year guard", () => {
      const { comp } = createEditor();

      comp.date = { year: 0, month: 1, day: 1 };
      comp.propagateDateChange(false);

      expect(comp.date.year).toBe(0);
   });
});

describe("DateValueEditorComponent — model setter and changeDate [Group 6, Risk 2]", () => {

   it("should leave existing date when model receives an invalid string", () => {
      const { comp } = createEditor();
      const prior = { year: 2024, month: 3, day: 10 };
      comp.date = prior;
      comp.initDate = prior;

      comp.model = "not-a-valid-date";

      expect(comp.date).toEqual(prior);
   });

   it("should leave existing date when model receives an empty string", () => {
      const { comp } = createEditor();
      const prior = { year: 2024, month: 3, day: 10 };
      comp.date = prior;

      comp.model = "";

      expect(comp.date).toEqual(prior);
   });

   it("should not re-emit when changeDate receives the same struct", () => {
      const { comp } = createEditor();
      const onChange = vi.fn();
      comp.registerOnChange(onChange);
      const same = { year: 2024, month: 6, day: 1 };
      comp.date = same;

      comp.changeDate({ year: 2024, month: 6, day: 1 });

      expect(onChange).not.toHaveBeenCalled();
   });

   it("should parse writeValue using a custom display format", () => {
      const { comp } = createEditor();
      comp.format = "MM/DD/YYYY";

      comp.writeValue("05/20/2024");

      expect(comp.date).toEqual({ year: 2024, month: 5, day: 20 });
   });
});
