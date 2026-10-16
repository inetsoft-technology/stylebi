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
 * WizardBindingTree - trap / source-change dialog answers (Bug #78715).
 *
 * Uses the real WizardBindingTree, TreeComponent, VSWizardBindingTreeService, DebounceService,
 * MessageDialog and NgbModal. Only ViewsheetClientService is stubbed. Columns are ticked with
 * real clicks on the tree items, and Escape is a real keydown on ngb-modal-window. Every tick
 * waits past the 200 ms recommender debounce.
 */

import { HttpClientTestingModule } from "@angular/common/http/testing";
import { ApplicationRef } from "@angular/core";
import { ComponentFixture, fakeAsync, flush, TestBed, tick } from "@angular/core/testing";
import { NoopAnimationsModule } from "@angular/platform-browser/animations";
import { NgbConfig, NgbModal, NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { Subject } from "rxjs";
import { AssetEntry } from "../../../../../../shared/data/asset-entry";
import { AssetType } from "../../../../../../shared/data/asset-type";
import { BindingTreeService } from "../../../binding/widget/binding-tree/binding-tree.service";
import { ViewsheetClientService } from "../../../common/viewsheet-client";
import { ViewsheetCommandMessage } from "../../../common/viewsheet-client/viewsheet-command-message";
import { MessageDialog } from "../../../widget/dialog/message-dialog/message-dialog.component";
import { TreeNodeModel } from "../../../widget/tree/tree-node-model";
import { VsWizardEditModes } from "../../model/vs-wizard-edit-modes";
import { VSWizardBindingTreeService } from "../../services/vs-wizard-binding-tree.service";
import { WizardBindingTree } from "./wizard-binding-tree.component";

const NODE_CHANGED = "/events/vswizard/binding/tree/node-changed";
const REFRESH_FIELDS = "/events/vswizard/binding/tree/refresh-fields";

function column(label: string, tableName: string, cube: string): TreeNodeModel {
   const entry: AssetEntry = <any> {
      scope: 1, type: AssetType.COLUMN, user: null, path: tableName + "/" + label, alias: null,
      identifier: "1^12^__NULL__^" + tableName + "/" + label,
      properties: {
         table: tableName, refType: "0", attribute: label, cubeColumnType: cube,
         assembly: tableName
      }
   };
   return { label, data: entry, leaf: true, children: [] };
}

function table(name: string, dims: string[], measures: string[]): TreeNodeModel {
   const entry: any = {
      scope: 1, type: AssetType.TABLE, path: name, identifier: "t^" + name,
      properties: { table: name, assembly: name }
   };
   return {
      label: name, data: entry, leaf: false, expanded: true,
      children: [
         { label: "Dimensions", leaf: false, expanded: false,
           children: dims.map(d => column(d, name, "0")) },
         { label: "Measures", leaf: false, expanded: false,
           children: measures.map(m => column(m, name, "1")) }
      ]
   };
}

describe("WizardBindingTree - trap and source-change dialog answers", () => {
   let fixture: ComponentFixture<WizardBindingTree>;
   let client: any;
   let service: VSWizardBindingTreeService;
   let rejections: string[];

   const host = (): HTMLElement => fixture.nativeElement;
   const treeItem = (label: string) =>
      host().querySelector<HTMLElement>(`[role="treeitem"][aria-label="${label}"]`);
   const isTicked = (label: string) => treeItem(label).getAttribute("aria-selected") === "true";
   const sent = (uri: string) =>
      client.sendEvent.mock.calls.filter((c: any) => c[0] === uri).map((c: any) => c[1]);
   const nodeChangedPaths = (): string[][] =>
      sent(NODE_CHANGED).map((e: any) => e.selectedEntries.map((x: any) => x.path));
   const selectedLabels = () => service.selectedNodes.map(n => n.label);
   const command = (type: string, body: any) =>
      client.commands.next(new ViewsheetCommandMessage(null, type, body));
   const modals = () => document.body.querySelectorAll<HTMLElement>("ngb-modal-window");
   const topModal = (): HTMLElement => {
      const all = modals();
      return all.length ? all[all.length - 1] : null;
   };
   const clickButton = (label: string) =>
      (Array.from(topModal().querySelectorAll("button"))
         .find(b => b.textContent.trim() === label) as HTMLButtonElement).click();

   function flushView(): void {
      const appRef = TestBed.inject(ApplicationRef);
      fixture.detectChanges();
      appRef.tick();
      tick();
      fixture.detectChanges();
      appRef.tick();
   }

   // fakeAsync rethrows an unhandled promise rejection from tick(); record it instead
   function settle(): void {
      for(let i = 0; i < 3; i++) {
         try {
            tick(16);
         }
         catch(e) {
            rejections.push(String(e));
         }
      }

      flushView();
   }

   function render(): void {
      fixture = TestBed.createComponent(WizardBindingTree);
      fixture.componentRef.setInput("runtimeId", "rt.1");
      fixture.componentRef.setInput("temporarySheet", true);
      fixture.componentRef.setInput("originalMode", VsWizardEditModes.VIEWSHEET_PANE);
      flushView();
   }

   function loadTree(): void {
      command("RefreshWizardTreeCommand", {
         treeModel: { label: "root", expanded: true, children: [
            table("orders", ["region", "city"], ["sales", "cost"]),
            table("returns", ["reason"], ["amount"])
         ] },
         reload: false, forceRefresh: false,
         treeInfo: { grayedOutFields: [], tempAssemblyName: "_temp_chart_", tempBinding: null }
      });
      flushView();
   }

   function clickColumn(label: string): void {
      treeItem(label).dispatchEvent(new MouseEvent("click", { bubbles: true }));
      flushView();
   }

   // tick each column and let the recommender debounce fire after each
   function tickColumns(...labels: string[]): void {
      for(const label of labels) {
         clickColumn(label);
         tick(250);
         flushView();
      }
   }

   function pressEscape(): void {
      const event = new KeyboardEvent("keydown", { key: "Escape", keyCode: 27, bubbles: true });
      Object.defineProperty(event, "which", { value: 27 });
      topModal().dispatchEvent(event);
      settle();
   }

   // the server puts the refused node-changed event into the trap command
   function sendTrap(event: any): void {
      command("VSTrapCommand", { events: { [NODE_CHANGED]: event }, type: "CONFIRM" });
      flushView();
   }

   function lastNodeChanged(): any {
      const events = sent(NODE_CHANGED);
      return events[events.length - 1];
   }

   beforeEach(async () => {
      rejections = [];
      client = {
         runtimeId: "rt.1", clientId: "c", commands: new Subject<any>(), sendEvent: vi.fn()
      };

      await TestBed.configureTestingModule({
         imports: [HttpClientTestingModule, NgbModule, NoopAnimationsModule, WizardBindingTree],
         providers: [
            VSWizardBindingTreeService,
            { provide: BindingTreeService, useExisting: VSWizardBindingTreeService },
            { provide: ViewsheetClientService, useValue: client }
         ]
      }).compileComponents();

      TestBed.inject(NgbConfig).animation = false;
      MessageDialog.lastMessage = null;
      MessageDialog.lastMessageTS = 0;
      service = TestBed.inject(VSWizardBindingTreeService);
   });

   afterEach(() => {
      TestBed.inject(NgbModal).dismissAll();
   });

   describe("trap dialog", () => {
      it("Escape unticks the new column, drops it from selectedPaths and keeps it out of " +
         "the next tick", fakeAsync(() => {
         render();
         loadTree();
         tickColumns("city", "sales");
         const refused = lastNodeChanged();
         client.sendEvent.mockClear();
         sendTrap(refused);
         expect(modals().length).toBe(1);

         pressEscape();

         expect(modals().length).toBe(0);
         expect(rejections).toEqual([]);
         expect(client.sendEvent).not.toHaveBeenCalled();
         expect(isTicked("sales")).toBe(false);
         expect(isTicked("city")).toBe(true);
         expect(selectedLabels()).toEqual(["city"]);
         expect(service.selectedPaths).toEqual(["orders/city"]);

         tickColumns("region");
         expect(nodeChangedPaths()).toEqual([["orders/city", "orders/region"]]);
         flush();
      }));

      it("No unticks the new column through the setter so a tree reload doesn't tick it again",
         fakeAsync(() => {
            render();
            loadTree();
            tickColumns("city", "sales");
            sendTrap(lastNodeChanged());
            client.sendEvent.mockClear();

            clickButton("_#(js:No)");
            flushView();

            expect(modals().length).toBe(0);
            expect(client.sendEvent).not.toHaveBeenCalled();
            expect(isTicked("sales")).toBe(false);
            expect(service.selectedPaths).toEqual(["orders/city"]);

            loadTree();
            tick(250);
            flushView();

            expect(isTicked("sales")).toBe(false);
            expect(isTicked("city")).toBe(true);
            expect(nodeChangedPaths().every(paths => !paths.includes("orders/sales"))).toBe(true);
            flush();
         }));

      it("Yes resends the refused event confirmed and keeps the column ticked", fakeAsync(() => {
         render();
         loadTree();
         tickColumns("city", "sales");
         sendTrap(lastNodeChanged());
         client.sendEvent.mockClear();

         clickButton("_#(js:Yes)");
         flushView();

         expect(modals().length).toBe(0);
         expect(client.sendEvent).toHaveBeenCalledTimes(1);
         const [uri, event] = client.sendEvent.mock.calls[0];
         expect(uri).toBe(NODE_CHANGED);
         expect(event.confirmed).toBe(true);
         expect(event.selectedEntries.map((e: any) => e.path))
            .toEqual(["orders/city", "orders/sales"]);
         expect(isTicked("sales")).toBe(true);
         expect(service.selectedPaths).toEqual(["orders/city", "orders/sales"]);
         flush();
      }));

      // A tick still pending when the trap opens is sent behind the modal, and the server traps
      // that event too. The second trap arrives within 500 ms and is rejected as a duplicate
      // message; that rejection must drop the pending tick like a dismissal, or the column
      // would stay ticked but unbound.
      it("a duplicate trap rejected behind the open dialog drops the pending tick",
         fakeAsync(() => {
            render();
            loadTree();
            tickColumns("city", "sales");
            const first = lastNodeChanged();
            client.sendEvent.mockClear();

            clickColumn("region");
            tick(20);
            sendTrap(first);
            tick(200);
            flushView();
            expect(nodeChangedPaths())
               .toEqual([["orders/city", "orders/sales", "orders/region"]]);
            tick(50);
            sendTrap(lastNodeChanged());
            settle();

            expect(modals().length).toBe(1);
            expect(selectedLabels()).toEqual(["city", "sales"]);

            clickButton("_#(js:No)");
            flushView();

            expect(modals().length).toBe(0);
            expect(selectedLabels()).toEqual(["city"]);
            expect(service.selectedPaths).toEqual(["orders/city"]);
            expect(isTicked("sales")).toBe(false);
            expect(isTicked("region")).toBe(false);
            flush();
         }));
   });

   describe("source-change dialog", () => {
      it("Escape unticks the column from the other table, drops it from selectedPaths and " +
         "keeps it out of the next tick", fakeAsync(() => {
         render();
         loadTree();
         tickColumns("city", "amount");
         client.sendEvent.mockClear();
         command("VSWizardSourceChangeCommand", {});
         flushView();
         expect(modals().length).toBe(1);

         pressEscape();

         expect(modals().length).toBe(0);
         expect(rejections).toEqual([]);
         expect(sent(REFRESH_FIELDS)).toEqual([]);
         expect(isTicked("amount")).toBe(false);
         expect(isTicked("city")).toBe(true);
         expect(service.selectedPaths).toEqual(["orders/city"]);

         tickColumns("region");
         expect(nodeChangedPaths()).toEqual([["orders/city", "orders/region"]]);
         expect(lastNodeChanged().tableName).toBe("orders");
         flush();
      }));

      it("Cancel unticks the column through the setter so a tree reload doesn't tick it again",
         fakeAsync(() => {
            render();
            loadTree();
            tickColumns("city", "amount");
            command("VSWizardSourceChangeCommand", {});
            flushView();
            client.sendEvent.mockClear();

            clickButton("_#(js:Cancel)");
            flushView();

            expect(modals().length).toBe(0);
            expect(sent(REFRESH_FIELDS)).toEqual([]);
            expect(isTicked("amount")).toBe(false);
            expect(service.selectedPaths).toEqual(["orders/city"]);

            loadTree();
            tick(250);
            flushView();

            expect(isTicked("amount")).toBe(false);
            expect(isTicked("city")).toBe(true);
            expect(nodeChangedPaths().every(paths => !paths.includes("returns/amount")))
               .toBe(true);
            flush();
         }));

      it("OK keeps only the new column and sends refresh-fields for it", fakeAsync(() => {
         render();
         loadTree();
         tickColumns("city", "amount");
         command("VSWizardSourceChangeCommand", {});
         flushView();
         client.sendEvent.mockClear();

         clickButton("_#(js:OK)");
         flushView();

         expect(modals().length).toBe(0);
         const refresh = sent(REFRESH_FIELDS);
         expect(refresh.length).toBe(1);
         expect(refresh[0].selectedEntries.map((e: any) => e.path)).toEqual(["returns/amount"]);
         expect(refresh[0].tableName).toBe("returns");
         expect(selectedLabels()).toEqual(["amount"]);
         expect(service.selectedPaths).toEqual(["returns/amount"]);
         expect(isTicked("city")).toBe(false);
         expect(isTicked("amount")).toBe(true);
         flush();
      }));
   });
});
