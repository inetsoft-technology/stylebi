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
import { Subject } from "rxjs";
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
