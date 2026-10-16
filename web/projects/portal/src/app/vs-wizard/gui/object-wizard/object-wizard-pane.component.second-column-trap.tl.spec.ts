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
 * ObjectWizardPane — trap warning on an aggregate's second ("With") column (Bug #78716)
 *
 * Renders the real ObjectWizardPane, VSWizardAggregatePane, VSWizardAggregateItem,
 * DynamicComboBox, NgbModal/MessageDialog and VSWizardBindingTreeService (HttpClient). The
 * checktrap request is answered by MSW with true, so every With pick shows the Trap dialog.
 *
 * Binding: Sales = Correlation(Sales) With Qty, Units = Sum(Units); available fields Qty, Price.
 *
 * Note: polls with detectChanges + setTimeout instead of testing-library waitFor, which starves
 * the worker when real DynamicComboBox children are rendered.
 */

import { provideHttpClient } from "@angular/common/http";
import { Component, NO_ERRORS_SCHEMA } from "@angular/core";
import { ComponentFixture } from "@angular/core/testing";
import { By } from "@angular/platform-browser";
import { render } from "@testing-library/angular";
import { http, HttpResponse } from "msw";
import { Subject } from "rxjs";
import { server } from "@test-mocks/server";

import { ObjectWizardPane } from "./object-wizard-pane.component";
import { SecondColumnEdit, VSWizardAggregateItem } from "./wizard-aggregate-item.component";
import { DynamicComboBox } from "../../../widget/dynamic-combo-box/dynamic-combo-box.component";
import { FormattingPane } from "../../../format/objects/formatting-pane.component";
import { MessageDialog } from "../../../widget/dialog/message-dialog/message-dialog.component";
import { ObjectWizardToolBarComponent } from "../object-wizard-tool-bar.component";
import { SplitPane } from "../../../widget/split-pane/split-pane.component";
import { WizardBindingTree } from "./wizard-binding-tree.component";
import { WizardVisualizationPane } from "./wizard-visualization-pane.component";
import { VSWizardPreviewPane } from "./wizard-preview-pane.component";
import { VSLoadingDisplay } from "../../../vsobjects/objects/vs-loading-display/vs-loading-display.component";
import { NotificationsComponent } from "../../../widget/notifications/notifications.component";
import { ViewsheetClientService } from "../../../common/viewsheet-client/viewsheet-client.service";
import { ModelService } from "../../../widget/services/model.service";
import { VSChartService } from "../../../vsobjects/objects/chart/services/vs-chart.service";
import { UIContextService } from "../../../common/services/ui-context.service";
import { AiAssistantService } from "../../../../../../shared/ai-assistant/ai-assistant.service";
import { VsWizardEditModes } from "../../model/vs-wizard-edit-modes";
import { VSWizardBindingTreeService } from "../../services/vs-wizard-binding-tree.service";
import {
   AI_ASSISTANT_MOCK,
   NotificationsStub,
   ObjectWizardToolBarStub,
   SplitPaneStub,
   VSLoadingDisplayStub,
   VSWizardPreviewPaneStub,
   WizardBindingTreeStub,
   WizardVisualizationPaneStub,
} from "./object-wizard-pane.test-fixtures";

@Component({ selector: "formatting-pane", template: "", standalone: true })
class FormattingPaneStub {}

const REFRESH = "/events/vswizard/binding/refresh";
const client = { commands: new Subject<any>(), sendEvent: vi.fn() };
let trapChecks: any[] = [];
const unhandled: any[] = [];
const onUnhandled = (e: PromiseRejectionEvent) => unhandled.push(e.reason);
let fix: ComponentFixture<ObjectWizardPane>;

async function until(cond: () => boolean, label: string): Promise<void> {
   for(let i = 0; i < 200; i++) {
      fix?.detectChanges();

      if(cond()) {
         return;
      }

      await new Promise(r => setTimeout(r, 10));
   }

   throw new Error("timed out waiting for " + label);
}

async function settle(): Promise<void> {
   await new Promise(r => setTimeout(r, 50));
   fix.detectChanges();
}

function column(name: string, dataType: string): any {
   return { name, attribute: name, view: name, dataType, classType: "ColumnRef" };
}

function aggregate(name: string, formula: string, second: any): any {
   return {
      name, attribute: name, columnValue: name, fullName: formula + "(" + name + ")",
      formula, dataType: "double", classType: "ChartAggregateRef",
      secondaryColumn: second, secondaryColumnValue: second ? second.name : null, numValue: null
   };
}

async function setup() {
   const qty = column("Qty", "integer");
   const price = column("Price", "double");
   const sales = aggregate("Sales", "Correlation", qty);
   const units = aggregate("Units", "Sum", null);
   const { fixture } = await render(ObjectWizardPane, {
      schemas: [NO_ERRORS_SCHEMA],
      configureTestBed: (tb) => {
         tb.overrideComponent(VSWizardAggregateItem, {
            remove: { imports: [FormattingPane] }, add: { imports: [FormattingPaneStub] }
         });
      },
      componentInputs: {
         runtimeId: "rt-1", editMode: VsWizardEditModes.WIZARD_DASHBOARD,
         originalMode: VsWizardEditModes.WIZARD_DASHBOARD
      },
      importOverrides: [
         { replace: ObjectWizardToolBarComponent, with: ObjectWizardToolBarStub },
         { replace: SplitPane, with: SplitPaneStub },
         { replace: WizardBindingTree, with: WizardBindingTreeStub },
         { replace: WizardVisualizationPane, with: WizardVisualizationPaneStub },
         { replace: VSWizardPreviewPane, with: VSWizardPreviewPaneStub },
         { replace: VSLoadingDisplay, with: VSLoadingDisplayStub },
         { replace: NotificationsComponent, with: NotificationsStub },
      ],
      providers: [provideHttpClient(), VSWizardBindingTreeService],
      componentProviders: [
         { provide: ViewsheetClientService, useValue: client },
         { provide: ModelService, useValue: { getModel: vi.fn(), putModel: vi.fn() } },
         { provide: VSChartService, useValue: { showAllLegends: vi.fn(), hideLegend: vi.fn() } },
         { provide: UIContextService, useValue: { sheetClose: vi.fn() } },
         { provide: AiAssistantService, useValue: AI_ASSISTANT_MOCK },
      ],
   });
   fix = fixture;
   const comp = fixture.componentInstance;
   (comp as any)._bindingModel = {
      sourceName: "orders", assemblyType: 4, autoOrder: true, showLegend: true,
      chartBindingModel: { xfields: [], yfields: [sales, units], availableFields: [qty, price] },
      tableBindingModel: null, filterBindingModel: null, fixedFormulaMap: []
   };
   fixture.detectChanges();
   await settle();
   const items = fixture.debugElement.queryAll(By.directive(VSWizardAggregateItem));
   expect(items.length).toBe(2);
   client.sendEvent.mockClear();
   return { sales, units, qty, price, items };
}

function combos(item: any): DynamicComboBox[] {
   return item.queryAll(By.directive(DynamicComboBox)).map((d: any) => d.componentInstance);
}

// the real "With" combo of the Sales item (its value is the second column's name)
function withCombo(item: any): DynamicComboBox {
   const combo = combos(item).find((c: any) => c.value === "Qty" || c.value === "Price");
   expect(combo).toBeTruthy();
   return combo;
}

const modalWindow = () => document.querySelector<HTMLElement>("ngb-modal-window");
const refreshes = () => client.sendEvent.mock.calls.filter(c => c[0] === REFRESH).map(c => c[1]);

async function pickWith(item: any, name: string): Promise<void> {
   withCombo(item).valueChange.emit(name);
   await until(() => modalWindow() != null, "trap dialog open");
   expect(modalWindow().textContent).toContain("designer.binding.trapFound");
}

async function clickButton(label: string): Promise<void> {
   const button = Array.from(modalWindow().querySelectorAll("button"))
      .find(b => b.textContent.includes(label));
   expect(button).toBeTruthy();
   button.click();
   await until(() => modalWindow() == null, "trap dialog closed");
   await settle();
}

async function pressEscape(): Promise<void> {
   modalWindow().dispatchEvent(new KeyboardEvent("keydown", { key: "Escape", bubbles: true }));
   await until(() => modalWindow() == null, "trap dialog closed");
   await settle();
}

beforeEach(() => {
   trapChecks = [];
   client.sendEvent.mockClear();
   MessageDialog.lastMessage = null;
   MessageDialog.lastMessageTS = 0;
   unhandled.length = 0;
   window.addEventListener("unhandledrejection", onUnhandled);
   server.use(http.post("*/api/vswizard/binding/aggregate/checktrap", async ({ request }) => {
      trapChecks.push(await request.json());
      return HttpResponse.json(true);
   }));
});

afterEach(() => {
   window.removeEventListener("unhandledrejection", onUnhandled);
   document.querySelectorAll("ngb-modal-window, ngb-modal-backdrop").forEach(e => e.remove());
});

describe("ObjectWizardPane — With column trap warning (Bug #78716)", () => {
   it("item emits the edited index with the column it replaced", async () => {
      const { qty, items } = await setup();
      const item = items[0].componentInstance as VSWizardAggregateItem;
      const edits: SecondColumnEdit[] = [];
      const sub = item.onEditSecondColumn.subscribe(e => edits.push(e));

      try {
         await pickWith(items[0], "Price");
      }
      finally {
         sub.unsubscribe();
      }

      expect(edits).toEqual([{ index: 0, oldColumn: qty, oldValue: "Qty" }]);
      expect(trapChecks[0].bindingModel.yfields[0].secondaryColumnValue).toBe("Price");
      await clickButton("Undo");
   });

   it("Escape restores the previous column, sends nothing, and leaves no unhandled rejection", async () => {
      const { sales, qty, items } = await setup();
      const errorSpy = vi.spyOn(console, "error");

      await pickWith(items[0], "Price");
      await pressEscape();

      expect(sales.secondaryColumn).toBe(qty);
      expect(sales.secondaryColumnValue).toBe("Qty");
      expect((<any> withCombo(items[0])).value).toBe("Qty");
      expect(refreshes().length).toBe(0);
      expect(unhandled).toEqual([]);
      expect(errorSpy).not.toHaveBeenCalled();
   });

   it("the next unrelated edit after Escape does not send the trapped column", async () => {
      const { items } = await setup();

      await pickWith(items[0], "Price");
      await pressEscape();

      // change Units' formula (Sum -> Max) through its real formula combo: a direct refresh
      const unitsFormula = combos(items[1]).find((c: any) => c.value === "Sum");
      expect(unitsFormula).toBeTruthy();
      unitsFormula.valueChange.emit("Max");
      await settle();

      const sent = refreshes();
      expect(trapChecks.length).toBe(1);
      expect(sent.length).toBe(1);
      expect(sent[0].bindingModel.yfields[0].secondaryColumnValue).toBe("Qty");
      expect(sent[0].bindingModel.yfields[0].secondaryColumn.name).toBe("Qty");
   });

   it("Undo restores the previous column and sends nothing", async () => {
      const { sales, qty, items } = await setup();

      await pickWith(items[0], "Price");
      await clickButton("Undo");

      expect(sales.secondaryColumn).toBe(qty);
      expect(sales.secondaryColumnValue).toBe("Qty");
      expect((<any> withCombo(items[0])).value).toBe("Qty");
      expect(refreshes().length).toBe(0);
   });

   it("Keep and Continue applies the new column", async () => {
      const { sales, price, items } = await setup();

      await pickWith(items[0], "Price");
      await clickButton("Keep and Continue");

      expect(sales.secondaryColumn).toBe(price);
      expect(sales.secondaryColumnValue).toBe("Price");
      const sent = refreshes();
      expect(sent.length).toBe(1);
      expect(sent[0].bindingModel.yfields[0].secondaryColumnValue).toBe("Price");
   });

   it("Undo of a later trap restores the column committed by an earlier Keep", async () => {
      const { sales, price, items } = await setup();

      await pickWith(items[0], "Price");
      await clickButton("Keep and Continue");
      client.sendEvent.mockClear();
      MessageDialog.lastMessageTS = 0;

      await pickWith(items[0], "Qty");
      await clickButton("Undo");

      expect(sales.secondaryColumn).toBe(price);
      expect(sales.secondaryColumnValue).toBe("Price");
      expect(refreshes().length).toBe(0);
   });

   it("a trap question suppressed as a duplicate restores the previous column", async () => {
      const { sales, qty, items } = await setup();

      await pickWith(items[0], "Price");
      await clickButton("Undo");

      // the same trap message within 500 ms is rejected by showMessageDialog without a dialog
      MessageDialog.lastMessageTS = Date.now();
      withCombo(items[0]).valueChange.emit("Price");
      await until(() => trapChecks.length == 2, "second trap check");
      await settle();

      expect(modalWindow()).toBeNull();
      expect(sales.secondaryColumn).toBe(qty);
      expect(sales.secondaryColumnValue).toBe("Qty");
      expect(refreshes().length).toBe(0);
      expect(unhandled).toEqual([]);
   });
});
