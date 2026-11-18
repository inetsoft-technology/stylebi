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
 * ComposerMainComponent -- Bug #80379: with Follow Focus ticked, an agent edit of an open sheet
 * switches the Composer to that sheet's tab (FocusSheetCommand on /user/composer-client).
 *
 * Axis: sheet kind (viewsheet / worksheet / preview tab) x the focused sheet's state (plain,
 * preview, wizard, binding edit, layout, already the target) x Composer state (modal open, user
 * just clicked a tab, checkbox off, target not open).
 */

import "@angular/compiler";
import { TestBed } from "@angular/core/testing";
import { Subject } from "rxjs";
import { FocusSheetCommand } from "../command/focus-sheet-command";
import { Viewsheet } from "../data/vs/viewsheet";
import { Worksheet } from "../data/ws/worksheet";
import { ComposerMainComponent } from "./composer-main.component";
import { ComposerTabModel } from "./composer-tab-model";
import { makeMocks, renderComponent } from "./composer-main.spec-helpers";
import { FollowFocusService } from "./wiz/services/follow-focus.service";

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
   vi.useRealTimers();
   vi.restoreAllMocks();
   document.body.classList.remove("modal-open");
   localStorage.clear();
});

const WINDOW = ComposerMainComponent.FOCUS_HINT_COALESCE_MS + 50;

function worksheet(runtimeId: string, localId: number): Worksheet {
   const ws = new Worksheet();
   ws.id = "id-" + runtimeId;
   ws.localId = localId;
   ws.runtimeId = runtimeId;
   ws.closeOnServer = false;
   return ws;
}

function viewsheet(runtimeId: string, localId: number, preview = false): Viewsheet {
   const vs = new Viewsheet();
   vs.id = "id-" + runtimeId;
   vs.localId = localId;
   vs.runtimeId = runtimeId;
   vs.preview = preview;
   vs.socketConnection = { sendEvent: vi.fn() } as any;
   return vs;
}

interface Setup {
   comp: ComposerMainComponent;
   follow: FollowFocusService;
   tabs: Map<string, ComposerTabModel>;
   hint: (runtimeId: string) => void;
}

/** Renders the Composer with the given sheets open, `focused` focused, Follow Focus ticked. */
async function setup(sheets: (Worksheet | Viewsheet)[], focused: Worksheet | Viewsheet,
                     followOn = true): Promise<Setup>
{
   const hints = new Subject<FocusSheetCommand>();
   const mocks = makeMocks();
   mocks.composerClient.focusSheet = hints as any;
   const { comp } = await renderComponent({ deployed: false }, mocks);
   const follow = TestBed.inject(FollowFocusService);
   vi.spyOn(follow, "isFollowTabsEnabled").mockReturnValue(followOn);
   const tabs = new Map<string, ComposerTabModel>();

   for(const sheet of sheets) {
      const tab = new ComposerTabModel(sheet.type as any, sheet);
      comp.sheets.push(sheet);
      comp.openedTabs.push(tab);
      tabs.set(sheet.runtimeId, tab);
   }

   comp.focusedTab = comp.openedTabs.find(t => t.asset === focused);
   vi.useFakeTimers();

   return {
      comp, follow, tabs,
      hint: (runtimeId: string) => hints.next({ type: "focusSheet", runtimeId }),
   };
}

describe("ComposerMainComponent -- Follow Focus tab switching (Bug #80379)", () => {
   it("worksheet: switches to the edited tab", async () => {
      const a = worksheet("W-1", 1);
      const b = worksheet("W-2", 2);
      const { comp, hint } = await setup([a, b], a);

      hint("W-2");
      vi.advanceTimersByTime(WINDOW);

      expect(comp.focusedSheet).toBe(b);
   });

   it("viewsheet: switches like a user click, including the binding-tree refresh", async () => {
      const a = worksheet("W-1", 1);
      const v = viewsheet("V-2", 2);
      const { comp, hint } = await setup([a, v], a);

      hint("V-2");
      vi.advanceTimersByTime(WINDOW);

      expect(comp.focusedSheet).toBe(v);
      expect((v.socketConnection as any).sendEvent).toHaveBeenCalledWith(
         "/events/vs/bindingtree/gettreemodel", expect.anything());
   });

   it("does nothing when Follow Focus is off", async () => {
      const a = worksheet("W-1", 1);
      const b = worksheet("W-2", 2);
      const { comp, hint } = await setup([a, b], a, false);

      hint("W-2");
      vi.advanceTimersByTime(WINDOW);

      expect(comp.focusedSheet).toBe(a);
   });

   it("is a no-op while a modal dialog is open, and does not retry afterwards", async () => {
      const a = worksheet("W-1", 1);
      const b = worksheet("W-2", 2);
      const { comp, hint } = await setup([a, b], a);
      document.body.classList.add("modal-open");

      hint("W-2");
      vi.advanceTimersByTime(WINDOW);
      document.body.classList.remove("modal-open");
      vi.advanceTimersByTime(5000);

      expect(comp.focusedSheet).toBe(a);
   });

   it("is a no-op while the focused sheet is in preview mode", async () => {
      const a = worksheet("W-1", 1);
      const b = worksheet("W-2", 2);
      const { comp, hint } = await setup([a, b], a);
      comp.previewMode = true;

      hint("W-2");
      vi.advanceTimersByTime(WINDOW);

      expect(comp.focusedSheet).toBe(a);
   });

   it("is a no-op while the focused sheet is in the wizard", async () => {
      const a = worksheet("W-1", 1);
      const b = worksheet("W-2", 2);
      const { comp, hint } = await setup([a, b], a);
      comp.wizardEditMode = true;

      hint("W-2");
      vi.advanceTimersByTime(WINDOW);

      expect(comp.focusedSheet).toBe(a);
   });

   it("is a no-op while the focused viewsheet is in binding edit", async () => {
      const v = viewsheet("V-1", 1);
      const b = worksheet("W-2", 2);
      const { comp, hint } = await setup([v, b], v);
      v.bindingEditMode = true;

      hint("W-2");
      vi.advanceTimersByTime(WINDOW);

      expect(comp.focusedSheet).toBe(v);
   });

   it("is a no-op while the focused viewsheet is in layout mode", async () => {
      const a = worksheet("W-1", 1);
      const b = worksheet("W-2", 2);
      const { comp, hint } = await setup([a, b], a);
      comp.layoutMode = true;

      hint("W-2");
      vi.advanceTimersByTime(WINDOW);

      expect(comp.focusedSheet).toBe(a);
   });

   it("is a no-op for the tab that is already focused (no second sheetShow)", async () => {
      const a = worksheet("W-1", 1);
      const b = worksheet("W-2", 2);
      const { comp, hint } = await setup([a, b], a);
      const changed = vi.spyOn(comp, "onFocusedSheetChanged");

      hint("W-1");
      vi.advanceTimersByTime(WINDOW);

      expect(changed).not.toHaveBeenCalled();
      expect(comp.focusedSheet).toBe(a);
   });

   it("never opens a tab for a sheet that is not open in this Composer", async () => {
      const a = worksheet("W-1", 1);
      const { comp, hint } = await setup([a], a);
      const openWs = vi.spyOn(comp, "openWorksheet");
      const openVs = vi.spyOn(comp, "openViewsheet");

      hint("W-closed");
      vi.advanceTimersByTime(WINDOW);

      expect(comp.focusedSheet).toBe(a);
      expect(comp.openedTabs.length).toBe(1);
      expect(openWs).not.toHaveBeenCalled();
      expect(openVs).not.toHaveBeenCalled();
   });

   it("selects the sheet's own tab, never the preview tab that shares its runtime id", async () => {
      const a = worksheet("W-1", 1);
      const parent = viewsheet("V-2", 2);
      const preview = viewsheet("V-2", 3, true);
      const { comp, hint } = await setup([a, preview, parent], a);

      hint("V-2");
      vi.advanceTimersByTime(WINDOW);

      expect(comp.focusedSheet).toBe(parent);
   });

   it("a burst of hints becomes one switch, to the last sheet", async () => {
      const a = worksheet("W-1", 1);
      const b = worksheet("W-2", 2);
      const c = worksheet("W-3", 3);
      const { comp, hint } = await setup([a, b, c], a);
      const changed = vi.spyOn(comp, "onFocusedSheetChanged");

      hint("W-2");
      hint("W-3");
      hint("W-2");
      vi.advanceTimersByTime(WINDOW);

      expect(changed).toHaveBeenCalledTimes(1);
      expect(comp.focusedSheet).toBe(b);
   });

   it("does not pull the user away right after they clicked a tab, then follows again", async () => {
      const a = worksheet("W-1", 1);
      const b = worksheet("W-2", 2);
      const { comp, tabs, hint } = await setup([a, b], a);

      comp.onTabSelected(tabs.get("W-1"));
      vi.advanceTimersByTime(500);
      hint("W-2");
      vi.advanceTimersByTime(WINDOW);
      expect(comp.focusedSheet).toBe(a);

      vi.advanceTimersByTime(ComposerMainComponent.FOCUS_HINT_SUPPRESS_AFTER_USER_TAB_MS);
      hint("W-2");
      vi.advanceTimersByTime(WINDOW);
      expect(comp.focusedSheet).toBe(b);
   });

   it("reads the checkbox when it acts, so unticking inside the window cancels the switch", async () => {
      const a = worksheet("W-1", 1);
      const b = worksheet("W-2", 2);
      const { comp, follow, hint } = await setup([a, b], a);

      hint("W-2");
      (follow.isFollowTabsEnabled as any).mockReturnValue(false);
      vi.advanceTimersByTime(WINDOW);

      expect(comp.focusedSheet).toBe(a);
   });

   it("a pending hint dies with the component", async () => {
      const a = worksheet("W-1", 1);
      const b = worksheet("W-2", 2);
      const { comp, hint } = await setup([a, b], a);

      hint("W-2");
      comp.ngOnDestroy();
      vi.advanceTimersByTime(WINDOW);

      expect(vi.getTimerCount()).toBe(0);
      expect(comp.focusedSheet).toBe(a);
   });
});
