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
 * WSPaneComponent — Pass 3: Display
 *
 * Risk-first coverage:
 *   Group 1  [Risk 2] — toggleShowColumnName: sets the internal showColumnName field
 *                        that controls header display in child dialogs
 *   Group 2  [Risk 2] — processShowLoadingMaskCommand: preparingData public field is
 *                        set correctly (controls "preparing data" label in template)
 *   Group 3  [Risk 1] — worksheet @Input setter: calls aiAssistantDialogService.setWorksheetContext
 *                        so the AI assistant panel shows the correct worksheet scope
 *   Group 4  [Risk 1] — active @Input setter: reattaches/detaches ChangeDetectorRef
 *
 * Confirmed bugs: none
 *
 * Suspected bugs (header only):
 *   Suspicion A — toggleShowColumnName only writes to a private field; there is no
 *     ChangeDetectionRef.markForCheck() call, so OnPush sub-components that read
 *     `showColumnName` (passed as input to openSortColumnDialog) won't re-render until
 *     the next event cycle. Currently harmless because the dialog is opened on demand,
 *     but worth noting if the flag is ever bound to the template directly.
 *
 * Out of scope this pass:
 *   processWSRemoveAssemblyCommand, processMessageCommand, etc. (covered in ws-pane.component.risk.tl.spec.ts)
 *   cut/copy/addVariable, etc. (covered in ws-pane.component.interaction.tl.spec.ts)
 */

import {
   renderComponent,
   makeMocks,
} from "./ws-pane.component.test-helpers";

afterEach(() => vi.restoreAllMocks());

// ---------------------------------------------------------------------------
// Group 1: toggleShowColumnName [Risk 2]
// ---------------------------------------------------------------------------

describe("WSPaneComponent — toggleShowColumnName", () => {

   // toggleShowColumnName stores the flag used when opening SortColumnDialog;
   // the private field must flip synchronously.
   it("should set the internal showColumnName flag to true", async () => {
      const { comp } = await renderComponent();
      (comp as any).showColumnName = false;

      comp.toggleShowColumnName(true);

      expect((comp as any).showColumnName).toBe(true);
   });

   it("should set the internal showColumnName flag to false", async () => {
      const { comp } = await renderComponent();
      (comp as any).showColumnName = true;

      comp.toggleShowColumnName(false);

      expect((comp as any).showColumnName).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 2: preparingData public field [Risk 2]
// ---------------------------------------------------------------------------

describe("WSPaneComponent — preparingData display state", () => {

   // preparingData is bound in the template to the VSLoadingDisplay component;
   // it must reflect the server-pushed ShowLoadingMaskCommand.preparingData value.
   it("should expose preparingData=true when ShowLoadingMaskCommand has preparingData=true", async () => {
      const { comp, mocks } = await renderComponent();

      mocks.dispatchCommand("ShowLoadingMaskCommand", { preparingData: true, count: 1 });

      expect((comp as any).preparingData).toBe(true);
   });

   it("should expose preparingData=false when ShowLoadingMaskCommand has preparingData=false", async () => {
      const { comp, mocks } = await renderComponent();
      (comp as any).preparingData = true;

      mocks.dispatchCommand("ShowLoadingMaskCommand", { preparingData: false, count: 1 });

      expect((comp as any).preparingData).toBe(false);
   });

   it("should remain false by default (no ShowLoadingMaskCommand received)", async () => {
      const { comp } = await renderComponent();

      expect((comp as any).preparingData).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 3: worksheet @Input setter context wiring [Risk 1]
// ---------------------------------------------------------------------------

describe("WSPaneComponent — worksheet @Input setter", () => {

   // The AI assistant panel renders context-specific suggestions based on the active
   // worksheet. If setWorksheetContext is not called on assignment the assistant lags
   // one sheet behind on tab switch.
   it("should call aiAssistantDialogService.setWorksheetContext when worksheet is set", async () => {
      const mocks = makeMocks();
      const { comp } = await renderComponent(mocks);

      const newWs = { ...mocks.worksheet, id: "new-ws" };
      comp.worksheet = newWs as any;

      expect(mocks.aiAssistantDialogService.setWorksheetContext).toHaveBeenCalledWith(newWs);
   });
});

// ---------------------------------------------------------------------------
// Group 4: active @Input setter — ChangeDetectorRef [Risk 1]
// ---------------------------------------------------------------------------

describe("WSPaneComponent — active @Input setter", () => {

   // active=true reattaches change detection so the pane renders live data;
   // active=false detaches it so inactive tabs don't re-render unnecessarily.
   it("should reattach change detector when active=true", async () => {
      const { comp, fixture } = await renderComponent();
      const cdSpy = vi.spyOn((comp as any).changeDetector, "reattach");

      fixture.componentRef.setInput("active", true);

      expect(cdSpy).toHaveBeenCalled();
   });

   it("should detach change detector when active=false", async () => {
      const { comp, fixture } = await renderComponent();
      const cdSpy = vi.spyOn((comp as any).changeDetector, "detach");

      fixture.componentRef.setInput("active", false);

      expect(cdSpy).toHaveBeenCalled();
   });
});
