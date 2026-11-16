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
 * The chart title and legend format dialogs thread the chart's assembly name, runtime id and the
 * axis / legend index to their title combo (bug #80459, PR F). Pinned through the real dialogs:
 * the combo names exactly the axis or legend the dialog was opened for, the viewer keeps its
 * value-only combo, and a dialog opened with no valid target pairs nothing.
 */
import { By } from "@angular/platform-browser";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { render } from "@testing-library/angular";
import { ComponentTool } from "../../common/util/component-tool";
import { SheetClientRegistry } from "../../common/viewsheet-client/sheet-client-registry.service";
import { UIContextService } from "../../common/services/ui-context.service";
import { DynamicComboBox } from "../../widget/dynamic-combo-box/dynamic-combo-box.component";
import { LegendFormatDialog } from "./legend-format-dialog.component";
import { TitleFormatDialog } from "./title-format-dialog.component";

const RUNTIME = "rt-80459-f";
const ASSEMBLY = "Viewsheet1.Chart1";

function registry(): SheetClientRegistry {
   const registry = new SheetClientRegistry();
   registry.register({ runtimeId: RUNTIME } as any, "VIEWSHEET");
   return registry;
}

function providers(): any[] {
   return [
      { provide: NgbModal, useValue: { open: vi.fn() } },
      { provide: SheetClientRegistry, useValue: registry() },
      {
         provide: UIContextService,
         useValue: {
            isVS: vi.fn(), isAdhoc: vi.fn(), getDefaultTab: vi.fn(() => undefined),
            setDefaultTab: vi.fn()
         }
      }
   ];
}

function combos(fixture: any): DynamicComboBox[] {
   return fixture.debugElement.queryAll(By.directive(DynamicComboBox))
      .map(d => d.componentInstance);
}

function openFormulaEditor(combo: DynamicComboBox): any {
   const dialog: any = {};
   vi.spyOn(ComponentTool, "showDialog").mockReturnValue(dialog);
   combo.showFormulaEditor();
   return dialog;
}

async function renderTitle(props: any): Promise<any> {
   const { fixture } = await render(TitleFormatDialog, {
      providers: providers(),
      componentProperties: {
         model: {
            titleFormatPaneModel: {
               title: "T", currentTitle: "T", rotationRadioGroupModel: { rotation: "" }
            },
            oldTitle: ""
         },
         variableValues: [], vsId: RUNTIME, assemblyName: ASSEMBLY, ...props
      },
   });
   fixture.detectChanges();
   return fixture;
}

async function renderLegend(props: any): Promise<any> {
   const { fixture } = await render(LegendFormatDialog, {
      providers: providers(),
      componentProperties: {
         model: {
            legendFormatGeneralPaneModel: {
               title: "L", titleValue: "L", visible: true, position: 4, style: 0, fillColor: "#000000",
               symbolSize: 10, positionChoices: [], positionLabels: []
            },
            dimension: true, time: false, aliasPaneModel: { aliasList: [] }
         },
         variableValues: [], vsId: RUNTIME, assemblyName: ASSEMBLY, ...props
      },
   });
   fixture.detectChanges();
   return fixture;
}

afterEach(() => vi.restoreAllMocks());

describe("title format dialog", () => {
   it.each(["x", "x2", "y", "y2"])("axis %s: the title combo names chartRegion:title/%s/title", async axis => {
      const fixture = await renderTitle({ axisTitleType: axis });
      const list = combos(fixture);

      expect(list.map(c => [c.dynamicProperty, c.assemblyName, c.vsId]))
         .toEqual([[`chartRegion:title/${axis}/title`, ASSEMBLY, RUNTIME]]);

      const dialog = openFormulaEditor(list[0]);
      expect(dialog.dynamicProperty).toBe(`chartRegion:title/${axis}/title`);
      expect(dialog.contextTable).toBe(ASSEMBLY);
      expect(dialog.runtimeId).toBe(RUNTIME);
   });

   it("viewer: the combo stays value-only (no Expression, so no formula editor)", async () => {
      const fixture = await renderTitle({ axisTitleType: "y", viewer: true });

      expect(combos(fixture).map(c => c.valueOnly)).toEqual([true]);
   });

   it("an unknown axis pairs nothing", async () => {
      const fixture = await renderTitle({ axisTitleType: null });
      const dialog = openFormulaEditor(combos(fixture)[0]);

      expect(dialog.dynamicProperty).toBeUndefined();
      expect(dialog.runtimeId).toBeUndefined();
   });
});

describe("legend format dialog", () => {
   it.each([0, 1, 3])("legend %i: the title combo names chartRegion:legend/%i/title", async index => {
      const fixture = await renderLegend({ legendIndex: index });
      const list = combos(fixture);

      expect(list.map(c => [c.dynamicProperty, c.assemblyName, c.vsId]))
         .toEqual([[`chartRegion:legend/${index}/title`, ASSEMBLY, RUNTIME]]);

      const dialog = openFormulaEditor(list[0]);
      expect(dialog.dynamicProperty).toBe(`chartRegion:legend/${index}/title`);
      expect(dialog.contextTable).toBe(ASSEMBLY);
      expect(dialog.runtimeId).toBe(RUNTIME);
   });

   it("no legend index (-1) pairs nothing", async () => {
      const fixture = await renderLegend({ legendIndex: -1 });
      const dialog = openFormulaEditor(combos(fixture)[0]);

      expect(dialog.dynamicProperty).toBeUndefined();
      expect(dialog.runtimeId).toBeUndefined();
   });
});
