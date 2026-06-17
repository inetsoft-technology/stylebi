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
 * VSWizardPreviewPane — single pass
 *
 * Risk-first coverage:
 *   Group 1  [Risk 2] — ngOnInit(): vsInfo created with correct linkuri and runtimeId
 *   Group 2  [Risk 2] — setPreviewPaneSize(): sends SET_PREVIEW_PANE_SIZE with
 *                        GuiTool.getChartMaxModeSize().height - 98
 *   Group 3  [Risk 2] — changeDescription(): sets this.description and sends
 *                        /events/vswizard/preview/changeDescription with the new value
 *   Group 4  [baseline] — processRefreshDescriptionCommand: sets this.description from command
 *   Group 5  [baseline] — assemblyName getter: returns vsObject.absoluteName; null when no
 *                          vsObject
 *   Group 6  [baseline] — hasLegend getter: true when legends.length > 0; true when
 *                          legendHidden=true; false when legends empty and legendHidden=false
 *   Group 7  [Risk 3]  — memory-leak (known bug): CommandProcessor commands subscription is
 *                          NOT cleaned up on destroy because component has no ngOnDestroy
 *
 * Confirmed bugs (it.fails):
 *   Group 7 — VSWizardPreviewPane extends CommandProcessor but does not implement ngOnDestroy /
 *              call this.cleanup(), so viewsheetClient.commands remains subscribed after the
 *              component is destroyed. Any subsequent command published on the global channel
 *              dispatches to the dead instance.
 *
 * Out of scope:
 *   ngAfterViewInit calling setPreviewPaneSize() — tested in Group 2 via direct call; the
 *     AfterViewInit hook itself cannot be observed distinctly from render side-effects in ATL
 *     without a spy set up before rendering which would hide the real call path
 *   getAssemblyName() — always returns null; trivial, zero branch
 *   @HostListener window:resize wiring — depends on Angular's host binding and document-level
 *     event dispatch; indirect coverage via setPreviewPaneSize() direct call in Group 2
 */

import { Component, NO_ERRORS_SCHEMA } from "@angular/core";
import { render } from "@testing-library/angular";
import { Subject } from "rxjs";

import { VSWizardPreviewPane } from "./wizard-preview-pane.component";
import { WizardPreviewContainer } from "./wizard-preview-container.component";
import { ViewsheetClientService } from "../../../common/viewsheet-client";
import { ContextProvider } from "../../../vsobjects/context-provider.service";
import { GuiTool } from "../../../common/util/gui-tool";
import { VsWizardEditModes } from "../../model/vs-wizard-edit-modes";

// Stub WizardPreviewContainer: it imports VSChart, VSTable, etc. which have deep DI trees.
const StubPreviewContainer = Component({
   selector: "wizard-preview-container",
   template: "",
})(class {});

// ---------------------------------------------------------------------------
// Module-level Subject — persists across tests for the memory-leak group.
// ---------------------------------------------------------------------------
const commandsSubject = new Subject<any>();

const SEND_EVENT_MOCK = vi.fn();

const VS_CLIENT_MOCK = {
   commands: commandsSubject,
   sendEvent: SEND_EVENT_MOCK,
};

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

interface ChartObjectOpts {
   absoluteName?: string;
   legends?: any[];
   legendHidden?: boolean;
}

interface RenderOpts {
   runtimeId?: string;
   linkuri?: string;
   vsObject?: any;
   showLegend?: boolean;
}

function makeChartObject(opts: ChartObjectOpts = {}): any {
   return {
      absoluteName: opts.absoluteName ?? "Chart1",
      legends: opts.legends ?? [],
      legendHidden: opts.legendHidden ?? false,
   };
}

async function renderComponent(opts: RenderOpts = {}) {
   const result = await render(VSWizardPreviewPane, {
      inputs: {
         runtimeId: opts.runtimeId ?? "vs1",
         linkuri: opts.linkuri ?? "/app/viewer",
         vsObject: opts.vsObject !== undefined ? opts.vsObject : makeChartObject(),
         editMode: VsWizardEditModes.WIZARD_DASHBOARD,
         showLegend: opts.showLegend ?? false,
         consoleMessages: [],
      },
      providers: [
         { provide: ViewsheetClientService, useValue: VS_CLIENT_MOCK },
         {
            provide: ContextProvider,
            useValue: { viewer: false, preview: false, composer: true, binding: false },
         },
      ],
      importOverrides: [
         { replace: WizardPreviewContainer, with: StubPreviewContainer },
      ],
      schemas: [NO_ERRORS_SCHEMA],
   });
   return { comp: result.fixture.componentInstance as VSWizardPreviewPane, fixture: result.fixture };
}

beforeEach(() => {
   SEND_EVENT_MOCK.mockClear();
});

// ---------------------------------------------------------------------------
// Group 1 — ngOnInit(): vsInfo initialisation [Risk 2]
// ---------------------------------------------------------------------------

describe("VSWizardPreviewPane — ngOnInit(): vsInfo initialisation", () => {
   // 🔁 Regression-sensitive: the correct linkuri and runtimeId are required for the
   // embedded chart component to request the right viewsheet session. Wrong values cause
   // "viewsheet not found" errors in the preview pane.
   it("should create vsInfo with the provided runtimeId", async () => {
      const { comp } = await renderComponent({ runtimeId: "rt-test-123" });
      expect(comp.vsInfo.runtimeId).toBe("rt-test-123");
   });

   it("should create vsInfo with the provided linkuri", async () => {
      const { comp } = await renderComponent({ linkuri: "/app/custom" });
      // linkuri is stored inside vsInfo; access via the public property
      expect(comp.vsInfo.linkUri).toBe("/app/custom");
   });
});

// ---------------------------------------------------------------------------
// Group 2 — setPreviewPaneSize() [Risk 2]
// ---------------------------------------------------------------------------

describe("VSWizardPreviewPane — setPreviewPaneSize()", () => {
   // 🔁 Regression-sensitive: the height must be reduced by exactly 98px (the status bar
   // height). Off-by-one here causes chart overflow or clipping in the wizard preview.
   it("should send SET_PREVIEW_PANE_SIZE with GuiTool height minus 98", async () => {
      const { comp } = await renderComponent();
      SEND_EVENT_MOCK.mockClear();
      const guiSpy = vi.spyOn(GuiTool, "getChartMaxModeSize").mockReturnValue(
         { width: 1200, height: 800 } as any,
      );

      try {
         comp.setPreviewPaneSize();

         expect(SEND_EVENT_MOCK).toHaveBeenCalledWith(
            "/events/vswizard/preview/setPaneSize",
            expect.objectContaining({ size: expect.objectContaining({ height: 702 }) }),
         );
      }
      finally {
         guiSpy.mockRestore();
      }
   });

   it("should send SET_PREVIEW_PANE_SIZE including the width from GuiTool", async () => {
      const { comp } = await renderComponent();
      SEND_EVENT_MOCK.mockClear();
      const guiSpy = vi.spyOn(GuiTool, "getChartMaxModeSize").mockReturnValue(
         { width: 1024, height: 600 } as any,
      );

      try {
         comp.setPreviewPaneSize();

         expect(SEND_EVENT_MOCK).toHaveBeenCalledWith(
            "/events/vswizard/preview/setPaneSize",
            expect.objectContaining({ size: expect.objectContaining({ width: 1024 }) }),
         );
      }
      finally {
         guiSpy.mockRestore();
      }
   });
});

// ---------------------------------------------------------------------------
// Group 3 — changeDescription() [Risk 2]
// ---------------------------------------------------------------------------

describe("VSWizardPreviewPane — changeDescription()", () => {
   // 🔁 Regression-sensitive: the description must be stored locally (for template rendering)
   // AND sent to the server (so it persists). Missing either side causes a desync between
   // what the user sees and what the server saves.
   it("should set this.description to the new value", async () => {
      const { comp } = await renderComponent();

      comp.changeDescription("My Chart Description");

      expect(comp.description).toBe("My Chart Description");
   });

   it("should send /events/vswizard/preview/changeDescription with the description", async () => {
      const { comp } = await renderComponent();
      SEND_EVENT_MOCK.mockClear();

      comp.changeDescription("Updated Title");

      expect(SEND_EVENT_MOCK).toHaveBeenCalledWith(
         "/events/vswizard/preview/changeDescription",
         expect.objectContaining({ text: "Updated Title" }),
      );
   });
});

// ---------------------------------------------------------------------------
// Group 4 — processRefreshDescriptionCommand [baseline]
// ---------------------------------------------------------------------------

describe("VSWizardPreviewPane — processRefreshDescriptionCommand (via CommandProcessor)", () => {
   it("should set this.description from the command", async () => {
      const { comp } = await renderComponent();

      (comp as any)["processRefreshDescriptionCommand"]({ description: "Server-side description" });

      expect(comp.description).toBe("Server-side description");
   });
});

// ---------------------------------------------------------------------------
// Group 5 — assemblyName getter [baseline]
// ---------------------------------------------------------------------------

describe("VSWizardPreviewPane — assemblyName getter", () => {
   it("should return vsObject.absoluteName when vsObject is set", async () => {
      const { comp } = await renderComponent({ vsObject: makeChartObject({ absoluteName: "MyChart" }) });
      expect(comp.assemblyName).toBe("MyChart");
   });

   it("should return null when vsObject is null", async () => {
      const { comp } = await renderComponent({ vsObject: null });
      expect(comp.assemblyName).toBeNull();
   });
});

// ---------------------------------------------------------------------------
// Group 6 — hasLegend getter [baseline]
// ---------------------------------------------------------------------------

describe("VSWizardPreviewPane — hasLegend getter", () => {
   it("should return true when legends array has at least one entry", async () => {
      const { comp } = await renderComponent({
         vsObject: makeChartObject({ legends: [{ type: "color" }], legendHidden: false }),
      });
      expect(comp.hasLegend).toBe(true);
   });

   it("should return true when legendHidden=true even with empty legends", async () => {
      const { comp } = await renderComponent({
         vsObject: makeChartObject({ legends: [], legendHidden: true }),
      });
      expect(comp.hasLegend).toBe(true);
   });

   it("should return false when legends is empty and legendHidden=false", async () => {
      const { comp } = await renderComponent({
         vsObject: makeChartObject({ legends: [], legendHidden: false }),
      });
      expect(comp.hasLegend).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 7 — memory-leak: CommandProcessor subscription not cleaned up [Risk 3]
// ---------------------------------------------------------------------------

describe("VSWizardPreviewPane — CommandProcessor subscription lifecycle", () => {
   // 🔁 Known bug: VSWizardPreviewPane extends CommandProcessor (which subscribes to
   // viewsheetClient.commands in its constructor) but does not implement ngOnDestroy or
   // call this.cleanup(). After the component is destroyed the subscription remains active,
   // dispatching incoming commands to the dead instance. This can cause console errors and
   // stale state mutations when the wizard preview is closed and a new one is opened.
   it("should have an active commands subscription while alive", async () => {
      const { fixture } = await renderComponent();
      expect(commandsSubject.observed).toBe(true);
      // Destroy to let ATL clean up — we are only asserting the alive state here.
      fixture.destroy();
   });

   // Expected failure: `expect(commandsSubject.observed).toBe(false)` fails because
   // VSWizardPreviewPane has no ngOnDestroy / cleanup(), so CommandProcessor's commands
   // subscription is never unsubscribed after destroy. If this test fails for an unrelated
   // reason (e.g. renderComponent throws during setup), it.fails will still pass silently —
   // verify by checking the failure message says "expected true to be false".
   it.fails(
      "should unsubscribe from CommandProcessor commands after ngOnDestroy (BUG: no ngOnDestroy)",
      async () => {
         const { fixture } = await renderComponent();
         expect(commandsSubject.observed).toBe(true);

         fixture.destroy();

         expect(commandsSubject.observed).toBe(false);
      },
   );
});
