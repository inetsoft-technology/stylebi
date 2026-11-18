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

import { NgZone } from "@angular/core";
import { of } from "rxjs";
import { ComposerClientService } from "./composer-client.service";

/**
 * Bug #80379: /user/composer-client carries two kinds of command. A Follow Focus hint must reach
 * only the focus handler, and an open-asset command only the open handler.
 */
describe("ComposerClientService", () => {
   let service: ComposerClientService;
   let deliver: (body: any) => void;
   let connection: { subscribe: ReturnType<typeof vi.fn>; send: ReturnType<typeof vi.fn>;
                     disconnect: ReturnType<typeof vi.fn> };

   beforeEach(() => {
      deliver = null;
      connection = {
         subscribe: vi.fn((dest: string, cb: (m: any) => void) => {
            if(dest === "/user/composer-client") {
               deliver = (body: any) => cb({ frame: { body: JSON.stringify(body) } });
            }
         }),
         send: vi.fn(),
         disconnect: vi.fn(),
      };
      const client: any = { connect: vi.fn(() => of(connection)) };
      const zone = { run: (fn: () => any) => fn() } as unknown as NgZone;
      service = new ComposerClientService(client, zone);
      service.connect();
   });

   it("routes a focusSheet command to focusSheet only", () => {
      const focus = vi.fn();
      const open = vi.fn();
      service.focusSheet.subscribe(focus);
      service.editAsset.subscribe(open);

      deliver({ type: "focusSheet", runtimeId: "W-2" });

      expect(focus).toHaveBeenCalledWith({ type: "focusSheet", runtimeId: "W-2" });
      expect(open).not.toHaveBeenCalled();
   });

   it("routes an open-asset command (no type) to editAsset only", () => {
      const focus = vi.fn();
      const open = vi.fn();
      service.focusSheet.subscribe(focus);
      service.editAsset.subscribe(open);

      deliver({ assetId: "a", viewsheet: false, runtimeId: "W-3" });

      expect(open).toHaveBeenCalledWith({ assetId: "a", viewsheet: false, runtimeId: "W-3" });
      expect(focus).not.toHaveBeenCalled();
   });

   it("completes focusSheet on disconnect", () => {
      const done = vi.fn();
      service.focusSheet.subscribe({ complete: done });

      service.disconnect();

      expect(done).toHaveBeenCalled();
   });
});
