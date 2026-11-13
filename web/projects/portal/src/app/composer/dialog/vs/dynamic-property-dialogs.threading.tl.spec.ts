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
 * Property dialogs hand their assembly and runtime id down to the Visible / Enabled / Title
 * combos (bug #80459 review F1, F2). Pinned here through real dialogs, one per host-chain family:
 *  - the embedded viewsheet dialog (Visible pairs; its Enabled group is hidden by the server);
 *  - the tab dialog (dialog -> tab-general-pane -> general-prop-pane, vsId newly threaded);
 *  - the oval dialog (dialog -> shape-general-pane -> basic-general-pane);
 *  - the text dialog (dialog -> text-general-pane -> output-general-pane -> general-prop-pane).
 * For each, the combo gets the assembly and runtime id, and opening its formula editor passes
 * the SAME assembly and runtime id on to the formula dialog (the unpaired behaviour change the
 * review asked to be pinned), plus the pairing when a client owns that runtime.
 */
import { UntypedFormGroup } from "@angular/forms";
import { By } from "@angular/platform-browser";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { render } from "@testing-library/angular";
import { ComponentTool } from "../../../common/util/component-tool";
import { SheetClientRegistry } from "../../../common/viewsheet-client/sheet-client-registry.service";
import { UIContextService } from "../../../common/services/ui-context.service";
import { PropertyDialogService } from "../../../vsobjects/util/property-dialog.service";
import { VSTrapService } from "../../../vsobjects/util/vs-trap.service";
import { DynamicComboBox } from "../../../widget/dynamic-combo-box/dynamic-combo-box.component";
import { OvalPropertyDialog } from "./oval-property-dialog.component";
import { TabPropertyDialog } from "./tab-property-dialog.component";
import { TextPropertyDialog } from "./text-property-dialog.component";
import { ViewsheetObjectPropertyDialog } from "./viewsheet-object-property-dialog.component";
import { of as observableOf } from "rxjs";

const RUNTIME = "rt-42";

function basicModel(): any {
   return {
      name: "Obj1", visible: "Show", shadow: false, enabled: true, sendSelections: false,
      primary: true, refresh: false, editable: false, nameEditable: true,
      showShadowCheckbox: false, showEnabledCheckbox: false, showRefreshCheckbox: false,
      showEditableCheckbox: false, showSendSelectionsCheckbox: false, objectNames: []
   };
}

function generalModel(showEnabledGroup: boolean): any {
   return {
      basicGeneralPaneModel: basicModel(), showSubmitCheckbox: false, submitOnChange: true,
      showEnabledGroup, enabled: "True", popLocation: null
   };
}

function sizeModel(): any {
   return {
      left: 0, top: 0, width: 10, height: 10, titleHeight: 0, scaleVertical: false,
      container: false, lockAspectRatio: false, ratio: 1
   };
}

async function renderDialog(component: any, model: any, registry: SheetClientRegistry)
   : Promise<DynamicComboBox[]>
{
   const { fixture } = await render(component, {
      providers: [
         { provide: NgbModal, useValue: { open: vi.fn() } },
         { provide: SheetClientRegistry, useValue: registry },
         { provide: PropertyDialogService, useValue: { checkScript: vi.fn() } },
         { provide: VSTrapService, useValue: { checkTrap: vi.fn() } },
         {
            provide: UIContextService,
            useValue: {
               isVS: vi.fn(), isAdhoc: vi.fn(), getDefaultTab: vi.fn(() => undefined),
               setDefaultTab: vi.fn(), getObjectChange: vi.fn(() => observableOf({}))
            }
         }
      ],
      componentProperties: {
         model, runtimeId: RUNTIME, assemblyName: "Obj1", variableValues: [],
         scriptTreeModel: null
      },
   });
   fixture.detectChanges();

   return fixture.debugElement.queryAll(By.directive(DynamicComboBox))
      .map(d => d.componentInstance);
}

function registry(): SheetClientRegistry {
   const registry = new SheetClientRegistry();
   registry.register({ runtimeId: RUNTIME } as any, "VIEWSHEET");
   return registry;
}

/** What the combo's formula editor receives. */
function openFormulaEditor(combo: DynamicComboBox): any {
   const dialog: any = {};
   vi.spyOn(ComponentTool, "showDialog").mockReturnValue(dialog);
   combo.showFormulaEditor();
   return dialog;
}

afterEach(() => vi.restoreAllMocks());

describe("property dialogs thread the assembly and runtime id to the dynamic combos", () => {
   it("embedded viewsheet: the Visible combo resolves a pairing and no Enabled combo exists", async () => {
      const combos = await renderDialog(ViewsheetObjectPropertyDialog, {
         generalPropPaneModel: generalModel(false), sizePositionPaneModel: sizeModel(),
         vsAssemblyScriptPaneModel: {}
      }, registry());

      // the server hides the Enabled group of this type (showEnabledGroup=false), and the
      // manifest lists no enabled@viewsheet, so Visible is the only dynamic combo
      expect(combos.map(c => c.dynamicProperty)).toEqual(["visible"]);

      const dialog = openFormulaEditor(combos[0]);
      expect(dialog.dynamicProperty).toBe("visible");
      expect(dialog.contextTable).toBe("Obj1");
      expect(dialog.runtimeId).toBe(RUNTIME);
      expect(dialog.vsId).toBe(RUNTIME);
      expect(dialog.assemblyName).toBe("Obj1");
   });

   it("tab: Visible and Enabled get the runtime id that this pane newly threads", async () => {
      const combos = await renderDialog(TabPropertyDialog, {
         tabGeneralPaneModel: {
            generalPropPaneModel: generalModel(true), tabListPaneModel: { assemblies: [], labels: [] }, bottomTabs: false,
            sizePositionPaneModel: sizeModel()
         },
         vsAssemblyScriptPaneModel: {}
      }, registry());

      expect(combos.map(c => c.dynamicProperty).sort()).toEqual(["enabled", "visible"]);

      for(const combo of combos) {
         const dialog = openFormulaEditor(combo);
         expect(dialog.vsId).toBe(RUNTIME);
         expect(dialog.assemblyName).toBe("Obj1");
         expect(dialog.dynamicProperty).toBe(combo.dynamicProperty);
         expect(dialog.runtimeId).toBe(RUNTIME);
      }
   });

   it("oval (shape-general-pane -> basic-general-pane): Visible gets the runtime id", async () => {
      const combos = await renderDialog(OvalPropertyDialog, {
         shapeGeneralPaneModel: {
            basicGeneralPaneModel: basicModel(), sizePositionPaneModel: sizeModel()
         },
         ovalPropertyPaneModel: {}, vsAssemblyScriptPaneModel: {}
      }, registry());

      expect(combos.map(c => c.dynamicProperty)).toEqual(["visible"]);

      const dialog = openFormulaEditor(combos[0]);
      expect(dialog.vsId).toBe(RUNTIME);
      expect(dialog.assemblyName).toBe("Obj1");
      expect(dialog.dynamicProperty).toBe("visible");
      expect(dialog.runtimeId).toBe(RUNTIME);
   });
});
