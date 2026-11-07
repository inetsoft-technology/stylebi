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
 * ComposerMainComponent.applyCloseRuntimeOnTabClose -- Bug #80383: a tab opened on a runtime the
 * server created only for it (agent create_viewsheet/create_worksheet) must close that runtime
 * when the tab closes; a runtime others depend on (open_base_worksheet) must not.
 *
 * Axis: command flag (true / false / absent) x runtime match x sheet type.
 */

import "@angular/compiler";
import { OpenComposerAssetCommand } from "../command/open-composer-asset-command";
import { Worksheet } from "../data/ws/worksheet";
import { Viewsheet } from "../data/vs/viewsheet";
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

function cmd(extra: Partial<OpenComposerAssetCommand>): OpenComposerAssetCommand {
   return { assetId: "a", folderId: null, viewsheet: false, wsWizard: false, runtimeId: "R-1",
            ...extra } as OpenComposerAssetCommand;
}

async function setup(sheet: Worksheet | Viewsheet) {
   const { comp } = await renderComponent();
   sheet.runtimeId = "R-1";
   sheet.closeOnServer = false;
   comp.sheets.push(sheet);
   return comp as any;
}

describe("ComposerMainComponent.applyCloseRuntimeOnTabClose", () => {
   it("worksheet, flag true: tab now closes its runtime on the server", async () => {
      const ws = new Worksheet();
      const comp = await setup(ws);
      comp.applyCloseRuntimeOnTabClose(cmd({ closeRuntimeOnTabClose: true }));
      expect(ws.closeOnServer).toBe(true);
   });

   it("viewsheet, flag true, agentActive false: still applied (independent of agentActive)", async () => {
      const vs = new Viewsheet();
      const comp = await setup(vs);
      comp.applyCloseRuntimeOnTabClose(
         cmd({ viewsheet: true, closeRuntimeOnTabClose: true, agentActive: false }));
      expect(vs.closeOnServer).toBe(true);
   });

   it("flag false (open_base_worksheet): tab does not own the runtime", async () => {
      const ws = new Worksheet();
      const comp = await setup(ws);
      comp.applyCloseRuntimeOnTabClose(cmd({ closeRuntimeOnTabClose: false, agentActive: true }));
      expect(ws.closeOnServer).toBe(false);
   });

   it("flag absent: unchanged", async () => {
      const ws = new Worksheet();
      const comp = await setup(ws);
      comp.applyCloseRuntimeOnTabClose(cmd({}));
      expect(ws.closeOnServer).toBe(false);
   });

   it("no tab on that runtime: no-op, no throw", async () => {
      const ws = new Worksheet();
      const comp = await setup(ws);
      comp.applyCloseRuntimeOnTabClose(cmd({ runtimeId: "R-other", closeRuntimeOnTabClose: true }));
      expect(ws.closeOnServer).toBe(false);
   });
});
