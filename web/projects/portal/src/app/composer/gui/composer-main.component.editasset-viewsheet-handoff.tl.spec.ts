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
 * Regression test for bug #76636: create_viewsheet's browser handoff.
 *
 * initComposerClient()'s editAsset.subscribe handler (composer-main.component.ts) is otherwise
 * completely untested -- every other spec in this directory mocks composerClient.editAsset as
 * EMPTY, so the command.viewsheet===true branch never ran under test. This file drives it with a
 * real Subject so the branch actually executes.
 *
 * Covers both symptoms the diagnosis traced to this handler:
 *  - a new viewsheet tab must be opened using command.runtimeId (server-minted runtime), not
 *    left null (non-colliding case below);
 *  - the handler must not locate/force-cast an unrelated already-open sheet and fire a stray
 *    socket event on it, even when command.assetId collides with that sheet's own Sheet.id
 *    (collision case below). Pre-#5219, the server itself sent a colliding assetId (the new
 *    viewsheet's BASE WORKSHEET id); that was fixed server-side in SheetOpenService.createViewsheet.
 *    But the client handler's own sheets.find had no type check, so it could still force-cast a
 *    same-id Worksheet into a Viewsheet and fire on its socketConnection -- confirmed still true
 *    even after #5219 by driving a colliding command directly into this handler. The client-side
 *    fix adds `s.type === "viewsheet"` to the find predicate so this can't happen regardless of
 *    what assetId the server sends.
 */

import "@angular/compiler";
import { Subject } from "rxjs";
import { OpenComposerAssetCommand } from "../command/open-composer-asset-command";
import { Viewsheet } from "../data/vs/viewsheet";
import { Worksheet } from "../data/ws/worksheet";
import { makeMocks, renderComponent } from "./composer-main.spec-helpers";

beforeAll(() => {
   (window as any).BroadcastChannel = (window as any).BroadcastChannel ?? class {
      onmessage: any = null;
      postMessage() {}
      close() {}
      addEventListener() {}
      removeEventListener() {}
   };
});

afterEach(() => {
   vi.restoreAllMocks();
   localStorage.clear();
});

describe("ComposerMainComponent — editAsset viewsheet handoff (bug #76636)", () => {
   it("opens a new viewsheet tab with the server-provided runtimeId and does not touch an unrelated open worksheet's socket", async () => {
      const editAsset = new Subject<OpenComposerAssetCommand>();
      const mocks = makeMocks();
      mocks.composerClient.editAsset = editAsset as any;

      const { comp } = await renderComponent({ deployed: false }, mocks);

      // An already-open worksheet tab -- its Sheet.id must NOT collide with the new
      // viewsheet's own assetId below.
      const ws = new Worksheet();
      ws.id = "1^128^__NULL__^Monthly Order Total 2026";
      ws.runtimeId = "Untitled-6-5";
      ws.socketConnection = { sendEvent: vi.fn() } as any;
      comp.sheets.push(ws);

      editAsset.next({
         assetId: "4^1^__NULL__^Untitled-8-7",
         folderId: null,
         viewsheet: true,
         wsWizard: false,
         runtimeId: "Untitled-8-7",
      } as OpenComposerAssetCommand);

      expect(ws.socketConnection.sendEvent).not.toHaveBeenCalled();

      expect(comp.sheets.length).toBe(2);
      const opened = comp.sheets[1] as Viewsheet;
      expect(opened.type).toBe("viewsheet");
      expect(opened.runtimeId).toBe("Untitled-8-7");
      expect(opened.closeOnServer).toBe(false);
   });

   it("does not force-cast or fire on an open worksheet tab whose id collides with command.assetId (pre-#5219 server shape)", async () => {
      const editAsset = new Subject<OpenComposerAssetCommand>();
      const mocks = makeMocks();
      mocks.composerClient.editAsset = editAsset as any;

      const { comp } = await renderComponent({ deployed: false }, mocks);

      // An already-open worksheet tab whose id DOES collide with command.assetId below --
      // this reproduces the pre-#5219 server behavior of sending the base worksheet's own id
      // as command.assetId for a new viewsheet.
      const ws = new Worksheet();
      ws.id = "1^128^__NULL__^Monthly Order Total 2026";
      ws.runtimeId = "Untitled-6-5";
      ws.socketConnection = { sendEvent: vi.fn() } as any;
      comp.sheets.push(ws);

      editAsset.next({
         assetId: ws.id,
         folderId: null,
         viewsheet: true,
         wsWizard: false,
         runtimeId: "Untitled-8-7",
      } as OpenComposerAssetCommand);

      expect(ws.socketConnection.sendEvent).not.toHaveBeenCalled();

      expect(comp.sheets.length).toBe(2);
      const opened = comp.sheets[1] as Viewsheet;
      expect(opened.type).toBe("viewsheet");
      expect(opened.runtimeId).toBe("Untitled-8-7");
   });
});
