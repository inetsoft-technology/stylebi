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
 * The chart target dialog hands its assembly and runtime id down to every Value / Label combo
 * and to the computation dialog (bug #80459, PR E), each with the chartTarget:* name of its own
 * field. Pinned through the real dialog: each tab, the date variants, and the computation
 * dialog reached through the computation combo box. A formula editor opened from a combo must
 * receive the pairing (assembly, name, runtime id).
 */
import { By } from "@angular/platform-browser";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { render } from "@testing-library/angular";
import { ComponentTool } from "../../common/util/component-tool";
import { SheetClientRegistry } from "../../common/viewsheet-client/sheet-client-registry.service";
import { UIContextService } from "../../common/services/ui-context.service";
import { ComputationComboBox } from "../dialog/computation-combo-box/computation-combo-box.component";
import { ComputationDialog } from "../dialog/computation-combo-box/computation-dialog.component";
import { DynamicComboBox } from "../dynamic-combo-box/dynamic-combo-box.component";
import { ChartTargetDialog } from "./chart-target-dialog.component";
import { TargetInfo } from "./target-info";

const RUNTIME = "rt-80459";
const ASSEMBLY = "Chart1";

function target(tabFlag: number, dateField: boolean): TargetInfo {
   const color = { type: "", color: "" };
   return {
      measure: { name: "m", label: "Total", dateField },
      fieldLabel: "", genericLabel: "", value: "", label: "", toValue: "", toLabel: "",
      labelFormats: "", lineStyle: 0, lineColor: color, fillAboveColor: color,
      fillBelowColor: color, alpha: "", fillBandColor: color, chartScope: false, index: 0,
      tabFlag, changed: false, targetString: "",
      strategyInfo: { name: "Average", label: "Average", value: "", percentageAggregateVal: "",
                      standardIsSample: false },
      bandFill: {
         name: "", field: "", summary: false, changed: false, clazz: "", colors: [],
         cssColors: [], defaultColors: [], colorMaps: [], globalColorMaps: [], useGlobal: true,
         shareColors: true, dateFormat: 0
      } as any,
      supportFill: false
   };
}

function registry(): SheetClientRegistry {
   const registry = new SheetClientRegistry();
   registry.register({ runtimeId: RUNTIME } as any, "VIEWSHEET");
   return registry;
}

function providers(): any[] {
   return [
      { provide: NgbModal, useValue: { open: vi.fn() } },
      { provide: SheetClientRegistry, useValue: registry() },
      { provide: UIContextService, useValue: { isAdhoc: vi.fn(), isVS: vi.fn() } }
   ];
}

/** The combos of a rendered component, as [name, assembly, vsId] triples. */
function combos(fixture: any): DynamicComboBox[] {
   return fixture.debugElement.queryAll(By.directive(DynamicComboBox))
      .map(d => d.componentInstance);
}

function triples(list: DynamicComboBox[]): string[][] {
   return list.map(c => [c.dynamicProperty, c.assemblyName, c.vsId]);
}

/** What the combo's formula editor receives. */
function openFormulaEditor(combo: DynamicComboBox): any {
   const dialog: any = {};
   vi.spyOn(ComponentTool, "showDialog").mockReturnValue(dialog);
   combo.showFormulaEditor();
   return dialog;
}

async function renderDialog(tabFlag: number, dateField: boolean): Promise<any> {
   const { fixture } = await render(ChartTargetDialog, {
      providers: providers(),
      componentProperties: {
         chartTarget: target(tabFlag, dateField), availableFields: [], variables: [],
         hideDcombox: false, vsId: RUNTIME, assemblyName: ASSEMBLY, assetId: "a", chartType: 1
      },
   });
   fixture.detectChanges();
   return fixture;
}

afterEach(() => vi.restoreAllMocks());

describe("chart target dialog threads the assembly and runtime id to its dynamic combos", () => {
   it("line tab: value and label name line.value / line.label", async () => {
      const fixture = await renderDialog(0, false);

      expect(triples(combos(fixture))).toEqual([
         ["chartTarget:line.value", ASSEMBLY, RUNTIME],
         ["chartTarget:line.label", ASSEMBLY, RUNTIME]]);
   });

   it("line tab, date field: the date variant names line.value too", async () => {
      const fixture = await renderDialog(0, true);

      expect(triples(combos(fixture))).toEqual([
         ["chartTarget:line.value", ASSEMBLY, RUNTIME],
         ["chartTarget:line.label", ASSEMBLY, RUNTIME]]);
   });

   it("band tab: from / to values and labels each name their own field", async () => {
      const fixture = await renderDialog(1, false);

      expect(triples(combos(fixture))).toEqual([
         ["chartTarget:band.from", ASSEMBLY, RUNTIME],
         ["chartTarget:band.fromLabel", ASSEMBLY, RUNTIME],
         ["chartTarget:band.to", ASSEMBLY, RUNTIME],
         ["chartTarget:band.toLabel", ASSEMBLY, RUNTIME]]);
   });

   it("band tab, date field: From and To date variants keep their own names, in order", async () => {
      const fixture = await renderDialog(1, true);

      expect(triples(combos(fixture))).toEqual([
         ["chartTarget:band.from", ASSEMBLY, RUNTIME],
         ["chartTarget:band.fromLabel", ASSEMBLY, RUNTIME],
         ["chartTarget:band.to", ASSEMBLY, RUNTIME],
         ["chartTarget:band.toLabel", ASSEMBLY, RUNTIME]]);
   });

   it("statistics tab: the label names statistics.label", async () => {
      const fixture = await renderDialog(2, false);

      expect(triples(combos(fixture))).toEqual([
         ["chartTarget:statistics.label", ASSEMBLY, RUNTIME]]);
   });

   it.each([[0, false], [0, true], [1, false], [1, true], [2, false]])(
      "tab %i, date field %s: every formula editor resolves the pairing for its own name",
      async (tabFlag: number, dateField: boolean) => {
         const fixture = await renderDialog(tabFlag, dateField);

         expect(combos(fixture).length).toBeGreaterThan(0);

         for(const combo of combos(fixture)) {
            const dialog = openFormulaEditor(combo);
            expect(dialog.dynamicProperty).toBe(combo.dynamicProperty);
            expect(dialog.contextTable).toBe(ASSEMBLY);
            expect(dialog.runtimeId).toBe(RUNTIME);
         }
      });
});

describe("computation dialog of the statistics target", () => {
   it("names statistics.statisticValue and, for Percentage, statistics.percentageOf", async () => {
      const { fixture } = await render(ComputationDialog, {
         providers: providers(),
         componentProperties: {
            model: { name: "Percentage", label: "Percentage", value: "", percentageAggregateVal: "",
                     standardIsSample: false },
            selectedIndex: 1, variables: [], hideDcombox: false, vsId: RUNTIME,
            assemblyName: ASSEMBLY
         },
      });
      fixture.detectChanges();

      expect(triples(combos(fixture))).toEqual([
         ["chartTarget:statistics.statisticValue", ASSEMBLY, RUNTIME],
         ["chartTarget:statistics.percentageOf", ASSEMBLY, RUNTIME]]);

      for(const combo of combos(fixture)) {
         const dialog = openFormulaEditor(combo);
         expect(dialog.dynamicProperty).toBe(combo.dynamicProperty);
         expect(dialog.runtimeId).toBe(RUNTIME);
         expect(dialog.contextTable).toBe(ASSEMBLY);
      }
   });

   it("the computation combo box passes the assembly on to the dialog it opens", async () => {
      let template: any = null;
      const modal = { open: vi.fn((tpl: any) => {
         template = tpl;
         return { result: new Promise(() => {}) };
      }) };
      const { fixture } = await render(ComputationComboBox, {
         providers: [...providers().filter(p => p.provide !== NgbModal),
                     { provide: NgbModal, useValue: modal }],
         componentProperties: {
            model: { name: "Average", label: "Average", value: "", percentageAggregateVal: "",
                     standardIsSample: false },
            variables: [], hideDcombox: false, vsId: RUNTIME, assemblyName: ASSEMBLY
         },
      });
      fixture.detectChanges();
      fixture.componentInstance.editComputation();
      expect(template).toBeTruthy();

      const view = template.createEmbeddedView({ close: vi.fn(), dismiss: vi.fn() });
      view.detectChanges();
      const dialog = view.rootNodes
         .map((n: any) => n)
         .filter((n: any) => n.nodeType === 1)[0];
      expect(dialog).toBeTruthy();
      const ng = (window as any).ng;
      const instance: ComputationDialog = ng?.getComponent?.(dialog);

      expect(instance.assemblyName).toBe(ASSEMBLY);
      expect(instance.vsId).toBe(RUNTIME);
   });
});
