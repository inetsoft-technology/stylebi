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
 * The table cell calendar is a dynamic component attached to document.body, so it has no model and
 * VSTableCell passes the owning table's viz mark in (Feature #81379).
 */
import { TestBed } from "@angular/core/testing";
import { of } from "rxjs";
import { FirstDayOfWeekService } from "../../../common/services/first-day-of-week.service";
import { VSTableCellCalendar } from "./vs-table-cell-calendar.component";

describe("VSTableCellCalendar viz mark", () => {
   function render(setup: (c: VSTableCellCalendar) => void = () => {}): HTMLElement {
      TestBed.configureTestingModule({
         imports: [VSTableCellCalendar],
         providers: [
            { provide: FirstDayOfWeekService, useValue: { getFirstDay: vi.fn(() => of({ isoFirstDay: 1 })) } },
         ],
      });
      const fixture = TestBed.createComponent(VSTableCellCalendar);
      fixture.componentInstance.date = { year: 2024, month: 1, day: 1 };
      setup(fixture.componentInstance);
      fixture.detectChanges();
      return fixture.nativeElement.querySelector(".dropdown-datepicker");
   }

   it("carries viz-dark and viz-modern when the owning table is marked dark", () => {
      const popup = render(c => {
         c.vizModern = true;
         c.vizDark = true;
      });

      expect(popup.classList.contains("viz-dark")).toBe(true);
      expect(popup.classList.contains("viz-modern")).toBe(true);
   });

   it("carries only viz-modern when the owning table is marked light", () => {
      const popup = render(c => {
         c.vizModern = true;
      });

      expect(popup.classList.contains("viz-modern")).toBe(true);
      expect(popup.classList.contains("viz-dark")).toBe(false);
   });

   it("carries no viz class by default", () => {
      const popup = render();

      expect(popup.classList.contains("viz-modern")).toBe(false);
      expect(popup.classList.contains("viz-dark")).toBe(false);
   });
});
