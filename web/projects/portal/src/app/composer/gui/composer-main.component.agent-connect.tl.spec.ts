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
 * ComposerMainComponent — AI agent connect state (bug #81980)
 *
 * agentConnectedCount counts every open sheet, not just the focused one, and is bound into the
 * toolbar; a no-sheet click on the toolbar opens a viewsheet and hands it to the toolbar so the
 * auto-connect is pinned to that sheet.
 */

import "@angular/compiler";
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

function addSheet(comp: any, id: number, agentConnected = false): Viewsheet {
   const vs = new Viewsheet();
   vs.localId = id;
   vs.agentConnected = agentConnected;
   comp.sheets.push(vs);
   return vs;
}

describe("ComposerMainComponent — agentConnectedCount", () => {
   it("is 0 with no sheets", async () => {
      const { comp } = await renderComponent();
      expect(comp.agentConnectedCount).toBe(0);
   });

   it("counts connected sheets across all open sheets, not only the focused one", async () => {
      const { comp } = await renderComponent();
      const background = addSheet(comp, 101, true);
      addSheet(comp, 102, false);
      addSheet(comp, 103, true);
      expect(comp.agentConnectedCount).toBe(2);

      background.agentConnected = false;
      expect(comp.agentConnectedCount).toBe(1);
   });

   it("returns to 0 when all sheets are closed", async () => {
      const { comp } = await renderComponent();
      addSheet(comp, 101, true);
      addSheet(comp, 102, true);
      expect(comp.agentConnectedCount).toBe(2);
      comp.sheets.splice(0, comp.sheets.length);
      expect(comp.agentConnectedCount).toBe(0);
   });

   it("is bound into the toolbar", async () => {
      const { fixture, comp } = await renderComponent();
      addSheet(comp, 101, true);
      fixture.detectChanges();
      const toolbarEl = fixture.nativeElement.querySelector("composer-toolbar");
      expect(toolbarEl.agentConnectedCount).toBe(1);
      comp.sheets[0].agentConnected = false;
      fixture.detectChanges();
      expect(toolbarEl.agentConnectedCount).toBe(0);
   });
});

describe("ComposerMainComponent — no-sheet AI connect", () => {
   it("toolbar output opens a blank viewsheet via openNewViewsheet(null) and pins the toolbar to it", async () => {
      const { comp, fixture } = await renderComponent();
      const beginSpy = vi.fn();
      (comp as any).toolbar = { beginAutoConnect: beginSpy };
      const openSpy = vi.spyOn(comp, "openNewViewsheet").mockImplementation(() => {
         addSheet(comp, 201);
      });

      fixture.nativeElement.querySelector("composer-toolbar")
         .dispatchEvent(new CustomEvent("onConnectWithoutSheet"));

      expect(openSpy).toHaveBeenCalledWith(null);
      expect(beginSpy).toHaveBeenCalledTimes(1);
      const [target, isOpen] = beginSpy.mock.calls[0];
      expect(target).toBe(comp.sheets[comp.sheets.length - 1]);
      expect(isOpen()).toBe(true);

      comp.sheets.splice(comp.sheets.indexOf(target), 1);
      expect(isOpen()).toBe(false);
   });
});
