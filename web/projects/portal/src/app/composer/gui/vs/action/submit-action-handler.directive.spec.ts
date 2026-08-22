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
import { SubmitActionHandlerDirective } from "./submit-action-handler.directive";

/**
 * l9-tester reported this exact case (Follow Focus L9 9.9/9.12): opening
 * Submit1's Properties -> Script tab pushed an editorContext with assembly:
 * null, because SubmitActionHandlerDirective never set `dialog.assemblyName`
 * on the constructed SubmitPropertyDialog - the same missing-Input-assignment
 * bug class as the G2 Task 1 socketConnection fix. See
 * docs/superpowers/plans/2026-08-22-follow-focus-missing-assembly-name-fix-plan.md.
 */
describe("SubmitActionHandlerDirective - property dialog assemblyName wiring", () => {
   it("gives the constructed SubmitPropertyDialog an assemblyName matching the model's absoluteName", () => {
      const socketConnectionStub = { runtimeId: "vs-submit-123", sendEvent: vi.fn() };
      const vsInfo: any = {
         runtimeId: "vs-submit-123",
         socketConnection: socketConnectionStub,
         vsObjects: []
      };
      const model: any = { absoluteName: "Submit1" };

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

      const directive = new SubmitActionHandlerDirective(modelService, modalService, context);
      directive.model = model;
      directive.vsInfo = vsInfo;

      // "properties" is private; invoke it the way the repo's other action-handler specs do.
      (directive as any).showPropertyDialog();

      expect(modalService.open).toHaveBeenCalledTimes(1);
      expect(componentInstance.runtimeId).toBe("vs-submit-123");
      expect(componentInstance.assemblyName).toBe("Submit1");
   });
});
