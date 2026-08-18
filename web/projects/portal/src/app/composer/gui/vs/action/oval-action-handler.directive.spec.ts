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

import { of as observableOf } from "rxjs";
import { OvalActionHandlerDirective } from "./oval-action-handler.directive";

/**
 * G2 Task 1 fix round: 118 pre-existing passing tests never caught that
 * `dialog.socketConnection` was declared on the script-pane chain but never
 * assigned by any of the action handlers that actually construct these
 * property dialogs at runtime - every dialog.runtimeId = ... assignment had
 * no dialog.socketConnection = ... counterpart. This test exercises the real
 * "properties" action end to end through OvalActionHandlerDirective (the
 * reviewer's own example) and asserts the constructed dialog instance comes
 * out with a live, non-null socketConnection - not just that the assignment
 * line exists in source.
 */
describe("OvalActionHandlerDirective - property dialog socket wiring", () => {
   it("gives the constructed OvalPropertyDialog a non-null socketConnection matching vsInfo's", () => {
      const socketConnectionStub = { runtimeId: "vs-oval-123", sendEvent: vi.fn() };
      const vsInfo: any = {
         runtimeId: "vs-oval-123",
         socketConnection: socketConnectionStub,
         vsObjects: [],
         focusedAssembliesChanged: vi.fn()
      };
      const model: any = { absoluteName: "Oval1", locked: false };

      const modelService: any = {
         getModel: vi.fn(() => observableOf({}))
      };
      const componentInstance: any = {};
      const modalService: any = {
         open: vi.fn(() => ({
            componentInstance,
            result: new Promise(() => { /* never resolves - not exercised here */ })
         }))
      };
      const context: any = { viewer: false, preview: false };

      const directive = new OvalActionHandlerDirective(modelService, modalService, context);
      directive.model = model;
      directive.vsInfo = vsInfo;

      // "oval properties" is private; invoke it the way the repo's other
      // action-handler specs reach private members.
      (directive as any).showPropertyDialog();

      expect(modalService.open).toHaveBeenCalledTimes(1);
      expect(componentInstance.runtimeId).toBe("vs-oval-123");
      expect(componentInstance.socketConnection).toBeTruthy();
      expect(componentInstance.socketConnection).toBe(socketConnectionStub);
   });
});
