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

import { ValueTypes } from "../../model/dynamic-value-model";
import { DatePeriodModel } from "../../model/date-period-model";
import { DateComparisonService } from "../../util/date-comparison.service";
import { DateComparisonCustomPeriodsComponent } from "./date-comparison-custom-periods.component";

describe("DateComparisonCustomPeriodsComponent overlap validation", () => {
   const period = (start: string, end: string): DatePeriodModel => ({
      start: { value: start, type: ValueTypes.VALUE },
      end: { value: end, type: ValueTypes.VALUE }
   });

   function create(periods: DatePeriodModel[]): {
      comp: DateComparisonCustomPeriodsComponent, emitted: boolean[]
   } {
      const comp = new DateComparisonCustomPeriodsComponent(new DateComparisonService());
      comp.customPeriodPaneModel = { datePeriods: periods };
      const emitted: boolean[] = [];
      comp.validChange.subscribe((v: boolean) => emitted.push(v));
      return { comp, emitted };
   }

   it("should emit invalid on init when fixed periods share a boundary day", () => {
      const { comp, emitted } = create([
         period("2020-01-01", "2020-03-31"), period("2020-03-31", "2020-05-15")]);

      comp.ngOnInit();

      expect(comp.isOverlapping).toBe(true);
      expect(emitted).toEqual([false]);
   });

   it("should emit valid for disjoint fixed periods", () => {
      const { comp, emitted } = create([
         period("2020-01-01", "2020-03-31"), period("2020-04-01", "2020-05-15")]);

      comp.ngOnInit();

      expect(comp.isOverlapping).toBe(false);
      expect(emitted).toEqual([true]);
   });

   it("should re-evaluate on edit and on delete", () => {
      const { comp, emitted } = create([
         period("2020-01-01", "2020-03-31"), period("2020-04-01", "2020-05-15")]);
      comp.ngOnInit();

      comp.customPeriodPaneModel.datePeriods[1].start.value = "2020-03-01";
      comp.onValueChange();
      comp.delDatePeriod(1);

      expect(emitted).toEqual([true, false, true]);
   });
});
