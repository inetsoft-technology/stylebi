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
 * ComposerMainComponent.openSheet -- Bug #78587 (a): an agent's open_base_worksheet command must
 * not be dropped because a tab for the same worksheet is already open on another runtime.
 *
 * Axis: existing tab state (none / unmodified / modified / importing / focused vs background /
 * same runtime) x outcome (tab runtime, dialog, notification).
 */

import "@angular/compiler";
import { ComponentTool } from "../../common/util/component-tool";
import { ComposerTabModel } from "./composer-tab-model";
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

async function setup(runtimeId: string, opts: { modified?: boolean, prohibited?: boolean,
                                                  focus?: boolean } = {})
{
   const { comp } = await renderComponent();
   const ws = new Worksheet();
   ws.id = "wsId";
   ws.localId = 1;
   ws.runtimeId = runtimeId;
   ws.closeOnServer = false;
   ws.closeProhibited = !!opts.prohibited;
   vi.spyOn(ws, "isModified").mockReturnValue(!!opts.modified);
   comp.sheets.push(ws);
   comp.openedTabs.push(new ComposerTabModel("worksheet", ws));

   if(opts.focus) {
      (comp as any)._focusedSheet = ws;
   }

   return { comp, ws };
}

function wsTabs(comp: any): Worksheet[] {
   return comp.sheets.filter((s: any) => s.id === "wsId");
}

describe("ComposerMainComponent.openSheet -- agent runtime vs existing worksheet tab", () => {
   it("no tab: creates a tab on the agent runtime", async () => {
      const { comp } = await renderComponent();
      comp.openWorksheet("wsId", false, "W-20");
      expect(wsTabs(comp).map(s => s.runtimeId)).toEqual(["W-20"]);
   });

   it("unmodified background tab on another runtime: replaced by a tab on the agent runtime", async () => {
      const { comp, ws } = await setup("W-17");
      const dialog = vi.spyOn(ComponentTool, "showMessageDialog");

      comp.openWorksheet("wsId", false, "W-20");

      expect(wsTabs(comp).map(s => s.runtimeId)).toEqual(["W-20"]);
      expect(comp.sheets).not.toContain(ws);
      expect(dialog).not.toHaveBeenCalled();
   });

   it("unmodified FOCUSED tab on another runtime: still replaced (focus must not short-circuit)", async () => {
      const { comp } = await setup("W-17", { focus: true });
      comp.openWorksheet("wsId", false, "W-20");
      expect(wsTabs(comp).map(s => s.runtimeId)).toEqual(["W-20"]);
   });

   it("same runtime: no-op, tab untouched, no dialog", async () => {
      const { comp, ws } = await setup("W-20", { focus: true });
      const dialog = vi.spyOn(ComponentTool, "showMessageDialog");

      comp.openWorksheet("wsId", false, "W-20");

      expect(wsTabs(comp)).toEqual([ws]);
      expect(dialog).not.toHaveBeenCalled();
   });

   it("no runtimeId (status-bar link): focused tab is left alone", async () => {
      const { comp, ws } = await setup("W-17", { focus: true });
      comp.openWorksheet("wsId");
      expect(wsTabs(comp)).toEqual([ws]);
      expect(ws.runtimeId).toBe("W-17");
   });

   it("modified tab, human keeps: tab and runtime unchanged", async () => {
      const { comp, ws } = await setup("W-17", { modified: true });
      vi.spyOn(ComponentTool, "showMessageDialog").mockResolvedValue("no");

      comp.openWorksheet("wsId", false, "W-20");
      await Promise.resolve();
      await Promise.resolve();

      expect(wsTabs(comp)).toEqual([ws]);
      expect(ws.runtimeId).toBe("W-17");
   });

   it("modified tab, dialog dismissed: treated as keep", async () => {
      const { comp, ws } = await setup("W-17", { modified: true });
      vi.spyOn(ComponentTool, "showMessageDialog").mockRejectedValue("cancel");

      comp.openWorksheet("wsId", false, "W-20");
      await Promise.resolve();
      await Promise.resolve();

      expect(wsTabs(comp)).toEqual([ws]);
   });

   it("modified tab, human discards: replaced by a tab on the agent runtime", async () => {
      const { comp } = await setup("W-17", { modified: true });
      const dialog = vi.spyOn(ComponentTool, "showMessageDialog").mockResolvedValue("yes");

      comp.openWorksheet("wsId", false, "W-20");
      // nothing is closed before the human answers
      expect(wsTabs(comp).map(s => s.runtimeId)).toEqual(["W-17"]);
      await vi.waitFor(() => expect(wsTabs(comp).map(s => s.runtimeId)).toEqual(["W-20"]));

      // two choices only: there is deliberately no "save first" option
      expect(Object.keys(dialog.mock.calls[0][3] as object)).toEqual(["yes", "no"]);
   });

   it("modified tab, discard answered after the tab was closed meanwhile: no tab is created", async () => {
      const { comp, ws } = await setup("W-17", { modified: true });
      let answer: (v: string) => void;
      vi.spyOn(ComponentTool, "showMessageDialog")
         .mockReturnValue(new Promise<string>(r => answer = r) as any);

      comp.openWorksheet("wsId", false, "W-20");
      comp.sheets.splice(comp.sheets.indexOf(ws), 1);
      answer("yes");
      await Promise.resolve();
      await Promise.resolve();

      expect(wsTabs(comp)).toEqual([]);
   });

   it("modified tab, import starts while the dialog is open: not replaced, human is told", async () => {
      const { comp, ws } = await setup("W-17", { modified: true });
      let answer: (v: string) => void;
      vi.spyOn(ComponentTool, "showMessageDialog")
         .mockReturnValue(new Promise<string>(r => answer = r) as any);

      comp.openWorksheet("wsId", false, "W-20");
      ws.closeProhibited = true;
      answer("yes");
      await Promise.resolve();
      await Promise.resolve();

      expect(wsTabs(comp)).toEqual([ws]);
      expect((comp as any).notifications.warning).toHaveBeenCalled();
   });

   it("importing tab (closeProhibited): not replaced, human is told, no dialog", async () => {
      const { comp, ws } = await setup("W-17", { prohibited: true });
      const dialog = vi.spyOn(ComponentTool, "showMessageDialog");

      comp.openWorksheet("wsId", false, "W-20");

      expect(wsTabs(comp)).toEqual([ws]);
      expect(ws.runtimeId).toBe("W-17");
      expect(dialog).not.toHaveBeenCalled();
      expect((comp as any).notifications.warning).toHaveBeenCalled();
   });
});
