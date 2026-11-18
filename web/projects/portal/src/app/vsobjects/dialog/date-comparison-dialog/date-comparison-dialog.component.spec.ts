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
 * DateComparisonDialog - change detection stability of the footer (Bug #80818).
 *
 * The dialog is rendered with its real child panes. vitest-setup.ts swallows NG0100 in
 * ComponentFixture.detectChanges and ApplicationRef.tick, so these tests attach the
 * component to the ApplicationRef the way NgbModal does and run detectChanges followed by
 * checkNoChanges on the host view directly, which surfaces NG0100.
 */

import {
   ApplicationRef,
   ComponentRef,
   createComponent,
   EnvironmentInjector,
   provideZoneChangeDetection
} from "@angular/core";
import { TestBed } from "@angular/core/testing";
import { of } from "rxjs";
import { DateComparisonDialog } from "./date-comparison-dialog.component";
import { ModelService } from "../../../widget/services/model.service";
import { FirstDayOfWeekService } from "../../../common/services/first-day-of-week.service";
import { DateComparisonDialogModel } from "../../model/date-comparison-dialog-model";
import { DatePeriodModel } from "../../model/date-period-model";
import { DynamicValueModel, ValueTypes } from "../../model/dynamic-value-model";
import { IntervalLevel } from "../../model/interval-pane-model";
import { XConstants } from "../../../common/util/xconstants";

function value(v: any): DynamicValueModel {
   return { value: v, type: ValueTypes.VALUE };
}

function period(start: string, end: string): DatePeriodModel {
   return { start: value(start), end: value(end) };
}

// Same Week is not offered for custom periods (the interval pane resets it while rendering),
// so the custom-period cases use "All".
const ALL_INTERVAL = "0";

function makeDialogModel(custom: boolean, preCount: string = "1",
                         intervalLevel: string = IntervalLevel.SAME_WEEK + ""): DateComparisonDialogModel
{
   return {
      dateComparisonPaneModel: {
         periodPaneModel: {
            custom,
            standardPeriodPaneModel: {
               preCount: value(preCount),
               dateLevel: value(XConstants.QUARTER_DATE_GROUP + ""),
               toDate: false,
               endDay: value(null),
               toDayAsEndDay: true,
               inclusive: false
            },
            customPeriodPaneModel: {
               datePeriods: [period("2024-01-01", "2024-01-31"), period("2024-03-01", "2024-03-31")]
            }
         },
         intervalPaneModel: {
            level: value(intervalLevel),
            granularity: value(IntervalLevel.DAY + ""),
            endDayAsToDate: true,
            intervalEndDate: value(null),
            inclusive: false,
            contextLevel: value(XConstants.QUARTER_DATE_GROUP + "")
         },
         comparisonOption: 1,
         useFacet: false,
         onlyShowMostRecentDate: false,
         visualFrameModel: null
      },
      shareFromAssembly: null,
      shareFromAvailableAssemblies: []
   };
}

describe("DateComparisonDialog - footer validity is stable on every change detection pass", () => {
   let ref: ComponentRef<DateComparisonDialog>;

   beforeEach(() => {
      TestBed.configureTestingModule({
         providers: [
            provideZoneChangeDetection(),
            { provide: ModelService, useValue: { getModel: vi.fn(() => of(null)) } },
            {
               provide: FirstDayOfWeekService,
               useValue: { getFirstDay: vi.fn(() => of({ javaFirstDay: 1, isoFirstDay: 1 })) }
            }
         ]
      });
   });

   afterEach(() => {
      if(ref) {
         TestBed.inject(ApplicationRef).detachView(ref.hostView);
         ref.destroy();
         ref = null;
      }
   });

   function open(model: DateComparisonDialogModel): void {
      const hostElement = document.createElement("div");
      document.body.appendChild(hostElement);
      ref = createComponent(DateComparisonDialog, {
         environmentInjector: TestBed.inject(EnvironmentInjector),
         hostElement
      });
      ref.setInput("dateComparisonDialogModel", model);
      ref.setInput("assemblyType", "VSChart");
      TestBed.inject(ApplicationRef).attachView(ref.hostView);
      render();
   }

   // one CD pass followed by the dev-mode check pass; throws NG0100 if a binding changed
   function render(): void {
      ref.changeDetectorRef.detectChanges();
      ref.changeDetectorRef.checkNoChanges();
   }

   function okButton(): HTMLButtonElement {
      return ref.location.nativeElement.querySelector(".modal-footer .btn-primary");
   }

   it("should not change the OK/Apply disabled state after it was checked when opened with standard periods", () => {
      expect(() => open(makeDialogModel(false))).not.toThrow();
      expect(okButton().disabled).toBe(false);
   });

   it("should not change the OK/Apply disabled state after it was checked when opened with custom periods", () => {
      expect(() => open(makeDialogModel(true, "1", ALL_INTERVAL))).not.toThrow();
      expect(okButton().disabled).toBe(false);
   });

   it("should not change the OK/Apply disabled state after it was checked when switching to standard periods", () => {
      const model = makeDialogModel(true, "1", ALL_INTERVAL);
      open(model);

      model.dateComparisonPaneModel.periodPaneModel.custom = false;

      expect(() => render()).not.toThrow();
      expect(okButton().disabled).toBe(false);
   });

   it("should not change the OK/Apply disabled state after it was checked when switching to custom periods", () => {
      const model = makeDialogModel(false, "1", ALL_INTERVAL);
      open(model);

      model.dateComparisonPaneModel.periodPaneModel.custom = true;

      expect(() => render()).not.toThrow();
      expect(okButton().disabled).toBe(false);
   });

   it("should keep OK disabled for an invalid standard period", () => {
      expect(() => open(makeDialogModel(false, "-1"))).not.toThrow();
      expect(okButton().disabled).toBe(true);
   });
});
