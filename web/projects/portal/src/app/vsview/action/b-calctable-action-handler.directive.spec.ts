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

import { of as observableOf, Subject } from "rxjs";
import { Rectangle } from "../../common/data/rectangle";
import { CalcTableLayout } from "../../common/data/tablelayout/calc-table-layout";
import { CalcTableActions } from "../../vsobjects/action/calc-table-actions";
import { VSCalcTableModel } from "../../vsobjects/model/vs-calctable-model";
import { BCalcTableActionHandlerDirective } from "./b-calctable-action-handler.directive";

const COPY_CUT_URI = "/events/vs/calctable/tablelayout/copycutcell";
const MODIFY_LAYOUT_URI = "/events/vs/calctable/tablelayout/modifylayout";

describe("BCalcTableActionHandlerDirective (Bug #78560)", () => {
   let directive: BCalcTableActionHandlerDirective;
   let clientService: { sendEvent: ReturnType<typeof vi.fn> };
   let editorService: { invalidateCellBinding: ReturnType<typeof vi.fn> };
   let actionEvents: Subject<any>;

   const keydown = (keyCode: number, ctrlKey: boolean = true) =>
      directive.onKeydown(<KeyboardEvent> <any> {
         keyCode, ctrlKey, stopPropagation: vi.fn(), target: { tagName: "DIV" }
      });

   beforeEach(() => {
      clientService = { sendEvent: vi.fn() };
      editorService = { invalidateCellBinding: vi.fn() };
      const injector: any = { get: vi.fn(() => editorService) };
      directive = new BCalcTableActionHandlerDirective(
         <any> clientService, <any> {}, <any> {}, injector, <any> {}, <any> {}, <any> {});
      directive.vsObject = <VSCalcTableModel> <any> { absoluteName: "TableView1" };
      directive.layoutModel = <CalcTableLayout> <any> { selectedRect: new Rectangle(0, 1, 1, 1) };
      actionEvents = new Subject<any>();
      directive.actions = <CalcTableActions> <any> { onAssemblyActionEvent: actionEvents };
   });

   it("ignores ctrl+v when nothing has been copied, leaving the binding ready", () => {
      keydown(86);

      expect(clientService.sendEvent).not.toHaveBeenCalled();
      expect(editorService.invalidateCellBinding).not.toHaveBeenCalled();
   });

   it("invalidates the binding and sends the paste after a copy", () => {
      keydown(67);
      keydown(86);

      expect(editorService.invalidateCellBinding).toHaveBeenCalledTimes(1);
      expect(clientService.sendEvent).toHaveBeenCalledTimes(1);
      expect(clientService.sendEvent.mock.calls[0][0]).toBe(COPY_CUT_URI);
   });

   it("invalidates the binding and sends the remove request", () => {
      keydown(46, false);

      expect(editorService.invalidateCellBinding).toHaveBeenCalledTimes(1);
      expect(clientService.sendEvent.mock.calls[0][0]).toBe(COPY_CUT_URI);
   });

   it("invalidates the binding and sends merge/split requests", () => {
      actionEvents.next({ id: "calc-table merge-cells" });
      actionEvents.next({ id: "calc-table split-cells" });

      expect(editorService.invalidateCellBinding).toHaveBeenCalledTimes(2);
      expect(clientService.sendEvent.mock.calls.map(c => c[0]))
         .toEqual([MODIFY_LAYOUT_URI, MODIFY_LAYOUT_URI]);
   });
});

/**
 * G2 Task 1, second fix round. This directive lives under `vsview/`, which the first fix round
 * excluded wholesale as "the Viewer runtime". That exclusion was a claim about every file in the
 * directory, and it was false for this one: `[bCalcTableActionHandler]` is used in
 * `vsview/view/vs-object-view.component.html`, rendered by `vsview/edit/vs-binding-pane.component.ts`,
 * which is embedded at `composer/gui/composer-main.component.html:290` under
 * `focusedViewsheet?.bindingEditMode`. So it fires for a Composer user editing a calc table's
 * binding layout, and `calc-table-property-dialog.component.html` renders its Script tab with
 * `[socketConnection]` unconditionally — no `openToScript` guard — so that user reached an
 * undefined socket.
 *
 * The two sibling directives in the same directory open no PropertyDialog subclass, so excluding
 * them was correct. One file of three was wrong, which is why this test pins reachability rather
 * than the directory boundary.
 */
describe("BCalcTableActionHandlerDirective - property dialog socket wiring", () => {
   it("gives the constructed CalcTablePropertyDialog the live client service as its socket", () => {
      const clientService: any = {
         runtimeId: "vs-calc-77",
         sendEvent: vi.fn()
      };
      const componentInstance: any = {};
      const modalService: any = {
         open: vi.fn(() => ({
            componentInstance,
            result: new Promise(() => { /* never resolves - the commit path is not exercised */ })
         }))
      };
      const modelService: any = {
         getModel: vi.fn(() => observableOf({}))
      };
      const injector: any = { get: vi.fn() };
      const renderer: any = { listen: vi.fn() };
      const zone: any = { runOutsideAngular: (fn: () => void) => fn() };
      const context: any = { viewer: false, preview: false };

      const directive = new BCalcTableActionHandlerDirective(
         clientService, modalService, modelService, injector, renderer, zone, context);
      (directive as any).vsObject = { absoluteName: "CalcTable1" };

      (directive as any).showPropertiesDialog();

      expect(modalService.open).toHaveBeenCalledTimes(1);
      // Both assertions matter: runtimeId was also missing here, unlike every other site in
      // this round where only the socket was absent.
      expect(componentInstance.runtimeId).toBe("vs-calc-77");
      expect(componentInstance.socketConnection).toBeTruthy();
      expect(componentInstance.socketConnection).toBe(clientService);
   });
});
