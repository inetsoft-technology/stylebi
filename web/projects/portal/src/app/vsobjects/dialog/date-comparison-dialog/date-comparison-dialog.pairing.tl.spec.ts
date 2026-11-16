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
 * The date comparison dialog hands its assembly and runtime id down to the Expression editors
 * of its dynamic fields (bug #80459, PR D). Pinned through the real dialog and real panes:
 * each field's formula editor receives the pairing with the field's OWN name
 * (`dateComparison:<key>`), and with no registered client (the viewer) none is offered.
 */
import { By } from "@angular/platform-browser";
import { HttpClient } from "@angular/common/http";
import { render } from "@testing-library/angular";
import { of } from "rxjs";
import { ComponentTool } from "../../../common/util/component-tool";
import { FirstDayOfWeekService } from "../../../common/services/first-day-of-week.service";
import { SheetClientRegistry } from "../../../common/viewsheet-client/sheet-client-registry.service";
import { XConstants } from "../../../common/util/xconstants";
import { DynamicComboBox } from "../../../widget/dynamic-combo-box/dynamic-combo-box.component";
import { ModelService } from "../../../widget/services/model.service";
import { DateComparisonDialog } from "./date-comparison-dialog.component";
import { DateComparisonCustomPeriodsComponent } from "./date-comparison-custom-periods.component";
import { IntervalLevel } from "../../model/interval-pane-model";
import { ValueTypes } from "../../model/dynamic-value-model";

const RUNTIME = "rt-77";
const ASSEMBLY = "Chart1";

function dv(value: string): any {
   return { value, type: ValueTypes.VALUE as string };
}

function dialogModel(custom: boolean, periods?: any[]): any {
   return {
      dateComparisonPaneModel: {
         periodPaneModel: {
            custom,
            customPeriodPaneModel: {
               datePeriods: periods ?? [
                  { start: dv("2020-01-01"), end: dv("2020-02-01") },
                  { start: dv("2021-01-01"), end: dv("2021-02-01") }
               ]
            },
            standardPeriodPaneModel: {
               preCount: dv("1"), dateLevel: dv(XConstants.YEAR_DATE_GROUP + ""),
               toDate: true, endDay: dv("2020-06-01"), toDayAsEndDay: false, inclusive: false
            }
         },
         intervalPaneModel: {
            level: dv(IntervalLevel.YEAR_TO_DATE + ""), granularity: dv(IntervalLevel.MONTH + ""),
            endDayAsToDate: false, intervalEndDate: dv("2020-06-01"), inclusive: false,
            contextLevel: dv(XConstants.YEAR_DATE_GROUP + "")
         },
         comparisonOption: 1, useFacet: false, onlyShowMostRecentDate: false,
         visualFrameModel: {}
      },
      shareFromAssembly: null, shareFromAvailableAssemblies: []
   };
}

function registry(register: boolean): SheetClientRegistry {
   const registry = new SheetClientRegistry();

   if(register) {
      registry.register({ runtimeId: RUNTIME } as any, "VIEWSHEET");
   }

   return registry;
}

async function renderFixture(custom: boolean, registered: boolean, periods?: any[]) {
   const { fixture } = await render(DateComparisonDialog, {
      providers: [
         { provide: SheetClientRegistry, useValue: registry(registered) },
         { provide: ModelService, useValue: { getModel: vi.fn(() => of({})) } },
         { provide: HttpClient, useValue: { get: vi.fn(() => of({})) } },
         {
            provide: FirstDayOfWeekService,
            useValue: { getFirstDay: () => of({ javaFirstDay: 1, jsFirstDay: 0 }) }
         }
      ],
      componentProperties: {
         dateComparisonDialogModel: dialogModel(custom, periods), variableValues: [],
         scriptTreeModel: null, runtimeId: RUNTIME, assemblyName: ASSEMBLY, assemblyType: "VSChart"
      },
   });
   fixture.detectChanges();
   return fixture;
}

async function renderDialog(custom: boolean, registered: boolean, periods?: any[])
   : Promise<DynamicComboBox[]>
{
   const fixture = await renderFixture(custom, registered, periods);

   return fixture.debugElement.queryAll(By.directive(DynamicComboBox))
      .map(d => d.componentInstance);
}

function openFormulaEditor(combo: DynamicComboBox): any {
   const dialog: any = {};
   // some panes bind a numeric level to the combo; the formula editor reads it as text
   (combo as any)._value = String(combo.value ?? "");
   vi.spyOn(ComponentTool, "showDialog").mockReturnValue(dialog);
   combo.showFormulaEditor();
   return dialog;
}

afterEach(() => vi.restoreAllMocks());

describe("date comparison dialog pairs its dynamic fields", () => {
   it("standard periods and interval: every field resolves its own dateComparison name", async () => {
      const combos = await renderDialog(false, true);
      const names = combos.map(c => c.dynamicProperty).filter(n => !!n);

      expect(names.sort()).toEqual([
         "dateComparison:endDate", "dateComparison:interval", "dateComparison:intervalContextLevel",
         "dateComparison:intervalEndDate", "dateComparison:intervalGranularity",
         "dateComparison:level", "dateComparison:periodsBack"
      ].sort());

      for(const combo of combos.filter(c => !!c.dynamicProperty)) {
         const dialog = openFormulaEditor(combo);
         expect(dialog.dynamicProperty, combo.dynamicProperty).toBe(combo.dynamicProperty);
         expect(dialog.contextTable).toBe(ASSEMBLY);
         expect(dialog.runtimeId).toBe(RUNTIME);
         vi.restoreAllMocks();
      }
   });

   it("custom periods: each row names its own index", async () => {
      const combos = await renderDialog(true, true);
      const names = combos.map(c => c.dynamicProperty).filter(n => !!n);

      expect(names).toEqual(expect.arrayContaining([
         "dateComparison:customPeriods[0].start", "dateComparison:customPeriods[0].end",
         "dateComparison:customPeriods[1].start", "dateComparison:customPeriods[1].end",
         "dateComparison:intervalGranularity"
      ]));

      const start1 = combos.find(c => c.dynamicProperty === "dateComparison:customPeriods[1].start");
      const dialog = openFormulaEditor(start1);
      expect(dialog.dynamicProperty).toBe("dateComparison:customPeriods[1].start");
      expect(dialog.contextTable).toBe(ASSEMBLY);
      expect(dialog.runtimeId).toBe(RUNTIME);
   });

   /** The (name, value) of every custom period combo, in the order shown. */
   function customCombos(combos: DynamicComboBox[]): [string, string][] {
      return combos.filter(c => c.dynamicProperty?.startsWith("dateComparison:customPeriods"))
         .map(c => [c.dynamicProperty, c.value]);
   }

   it("custom periods stored descending: names follow the STORED index, not the sorted row", async () => {
      // stored [2026, 2025]: the server and plugin call 2026 index 0; the pane shows 2025 first
      const combos = await renderDialog(true, true, [
         { start: dv("2026-01-01"), end: dv("2026-02-01") },
         { start: dv("2025-01-01"), end: dv("2025-02-01") }
      ]);

      expect(customCombos(combos)).toEqual([
         ["dateComparison:customPeriods[1].start", "2025-01-01"],
         ["dateComparison:customPeriods[1].end", "2025-02-01"],
         ["dateComparison:customPeriods[0].start", "2026-01-01"],
         ["dateComparison:customPeriods[0].end", "2026-02-01"]
      ]);
   });

   it("a period with a dynamic start keeps its stored index however the sort orders it", async () => {
      const combos = await renderDialog(true, true, [
         { start: dv("2026-01-01"), end: dv("2026-02-01") },
         { start: { value: "=today()", type: ValueTypes.EXPRESSION as string }, end: dv("2025-02-01") },
         { start: dv("2025-01-01"), end: dv("2025-02-01") }
      ]);
      const starts = Object.fromEntries(customCombos(combos).filter(([n]) => n.endsWith(".start"))
         .map(([n, v]) => [v, n]));

      expect(starts["2026-01-01"]).toBe("dateComparison:customPeriods[0].start");
      expect(starts["=today()"]).toBe("dateComparison:customPeriods[1].start");
      expect(starts["2025-01-01"]).toBe("dateComparison:customPeriods[2].start");
   });

   it("a row added in the open dialog has no stored index and is not paired", async () => {
      const fixture = await renderFixture(true, true);
      const pane = fixture.debugElement.query(By.directive(DateComparisonCustomPeriodsComponent))
         .componentInstance as DateComparisonCustomPeriodsComponent;
      pane.addDatePeriod();
      fixture.detectChanges();

      const combos = fixture.debugElement.queryAll(By.directive(DynamicComboBox))
         .map(d => d.componentInstance as DynamicComboBox);
      const props = combos.map(c => c.dynamicProperty);

      expect(props.filter(p => p?.startsWith("dateComparison:customPeriods")).length).toBe(4);
      expect(combos.length - combos.filter(c => !!c.dynamicProperty).length).toBeGreaterThanOrEqual(2);
      expect(pane.storedIndexOf(pane.customPeriodPaneModel.datePeriods[2])).toBe(-1);
   });

   for(const custom of [false, true]) {
      it("viewer (empty registry, custom=" + custom + "): no pairing is offered for any field", async () => {
         const combos = await renderDialog(custom, false);
         const named = combos.filter(c => !!c.dynamicProperty);
         expect(named.length).toBeGreaterThan(0);

         for(const combo of named) {
            const dialog = openFormulaEditor(combo);
            expect(dialog.runtimeId, combo.dynamicProperty).toBeUndefined();
            expect(dialog.socketConnection).toBeUndefined();
            vi.restoreAllMocks();
         }
      });
   }
});
