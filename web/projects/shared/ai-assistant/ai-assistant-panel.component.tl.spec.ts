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
 * AiAssistantPanelComponent — single pass
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3]  — ngOnInit: serverState transitions (checking → online / offline)
 *                        driven by panelOpen$ + checkHealth + loadWebComponentScript promise chain
 *   Group 2 [Risk 3]  — ngOnDestroy: panelOpenSub + healthSub both cleaned up; emits after
 *                        destroy do not trigger health checks or write serverState
 *   Group 3 [Risk 3]  — healthSub replaced on re-open: previous subscription unsubscribed
 *                        so stale health results do not update serverState
 *   Group 4 [Risk 2]  — ngOnInit: localStorage restore — valid values applied; invalid mode
 *                        string ignored; width below MIN_SIZE ignored
 *   Group 5 [Risk 2]  — toggleMode: cycles side↔bottom and persists to localStorage
 *   Group 6 [baseline] — close: sets aiAssistantService.panelOpen = false
 *   Group 7 [baseline] — toggleCollapsed: delegates to aiAssistantService.panelCollapsed
 *   Group 8 [baseline] — onResize: clamps sideWidth/bottomHeight to viewport bounds
 *   Group 9 [baseline] — startDrag: calls event.preventDefault; collapsed guard prevents setup
 *   Group 10 [baseline] — onDrag (via mousemove): sideWidth updated by drag delta in side mode
 *
 * Suspected bugs (header only):
 *   Suspicion A — Stale promise chain after re-open: when the panel opens a second time before
 *     loadWebComponentScript resolves from the first open, the first chain's `.then(() =>
 *     zone.run(() => serverState = "online"))` has no cancellation guard and can overwrite
 *     the second health check's result. No AbortController or destroyed-flag check present.
 *
 * Out of scope this pass: none (single pass)
 */

import { AsyncPipe } from "@angular/common";
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { render, waitFor } from "@testing-library/angular";
import { Subject } from "rxjs";

import { AiAssistantPanelComponent } from "./ai-assistant-panel.component";
import { AiAssistantService } from "./ai-assistant.service";

// ---------------------------------------------------------------------------
// Shared fixtures
// ---------------------------------------------------------------------------

function makeAiService() {
   const panelOpen$ = new Subject<boolean>();
   return {
      panelOpen$,
      panelCollapsed: false,
      panelOpen: false,
      checkHealth: vi.fn(() => new Subject<boolean>()),
      loadWebComponentScript: vi.fn().mockResolvedValue(undefined),
      refreshBranding: vi.fn().mockResolvedValue(undefined),
   };
}

async function renderComponent(aiService = makeAiService()) {
   vi.spyOn(customElements, "whenDefined").mockResolvedValue(undefined as any);

   const result = await render(AiAssistantPanelComponent, {
      schemas: [NO_ERRORS_SCHEMA],
      // Keep AsyncPipe for the template's |async usage; remove AiAssistantDialogComponent
      // to avoid resolving its transitive DI dependencies.
      componentImports: [AsyncPipe],
      providers: [
         { provide: AiAssistantService, useValue: aiService },
      ],
   });

   return {
      fixture: result.fixture,
      comp: result.fixture.componentInstance as AiAssistantPanelComponent,
      aiService,
   };
}

afterEach(() => {
   vi.restoreAllMocks();
   localStorage.clear();
});

// ---------------------------------------------------------------------------
// Group 1: ngOnInit — serverState transitions
// ---------------------------------------------------------------------------

describe("AiAssistantPanelComponent — ngOnInit: serverState", () => {
   // 🔁 Regression-sensitive: serverState must be reset to "checking" on every open so that
   // a previous "offline" state does not persist and show the error UI when the user re-opens.
   it("should reset serverState to 'checking' when panel re-opens after an offline result", async () => {
      const aiService = makeAiService();
      const health$ = new Subject<boolean>();
      aiService.checkHealth.mockReturnValue(health$);
      const { comp } = await renderComponent(aiService);

      aiService.panelOpen$.next(true);
      health$.next(false);
      expect(comp.serverState).toBe("offline");

      aiService.panelOpen$.next(true); // re-open
      expect(comp.serverState).toBe("checking");
   });

   it("should set serverState to 'offline' when health check returns false", async () => {
      const aiService = makeAiService();
      const health$ = new Subject<boolean>();
      aiService.checkHealth.mockReturnValue(health$);
      const { comp } = await renderComponent(aiService);

      aiService.panelOpen$.next(true);
      health$.next(false);

      expect(comp.serverState).toBe("offline");
   });

   // 🔁 Regression-sensitive: loadWebComponentScript + customElements.whenDefined +
   // refreshBranding must ALL resolve before serverState becomes "online"; missing any
   // step silently leaves the panel in the "checking" state forever.
   it("should set serverState to 'online' when health check is online and all scripts load", async () => {
      const aiService = makeAiService();
      const health$ = new Subject<boolean>();
      aiService.checkHealth.mockReturnValue(health$);
      const { comp } = await renderComponent(aiService);

      aiService.panelOpen$.next(true);
      health$.next(true);

      await waitFor(() => expect(comp.serverState).toBe("online"));
   });

   it("should set serverState to 'offline' when loadWebComponentScript rejects", async () => {
      const aiService = makeAiService();
      aiService.loadWebComponentScript.mockRejectedValue(new Error("load failed"));
      const health$ = new Subject<boolean>();
      aiService.checkHealth.mockReturnValue(health$);
      const { comp } = await renderComponent(aiService);

      aiService.panelOpen$.next(true);
      health$.next(true);

      await waitFor(() => expect(comp.serverState).toBe("offline"));
   });
});

// ---------------------------------------------------------------------------
// Group 2: ngOnDestroy — subscription cleanup (memory leak)
// ---------------------------------------------------------------------------

describe("AiAssistantPanelComponent — ngOnDestroy", () => {
   // 🔁 Regression-sensitive: panelOpenSub must be cleaned up so the health-check cycle does
   // not run after the composer is closed; a leaked sub would call checkHealth on every
   // subsequent open of *any* composer instance in the same page session.
   it("should not call checkHealth after the component is destroyed", async () => {
      const aiService = makeAiService();
      const { fixture } = await renderComponent(aiService);

      fixture.destroy();
      aiService.panelOpen$.next(true);

      expect(aiService.checkHealth).not.toHaveBeenCalled();
   });

   // 🔁 Regression-sensitive: healthSub must be cleaned up so an in-flight health response
   // does not write serverState on the destroyed component.
   it("should not update serverState after the component is destroyed", async () => {
      const aiService = makeAiService();
      const health$ = new Subject<boolean>();
      aiService.checkHealth.mockReturnValue(health$);
      const { fixture, comp } = await renderComponent(aiService);

      aiService.panelOpen$.next(true); // starts healthSub
      expect(comp.serverState).toBe("checking");

      fixture.destroy();
      health$.next(false); // emit after destroy

      expect(comp.serverState).toBe("checking"); // unchanged
   });
});

// ---------------------------------------------------------------------------
// Group 3: healthSub replaced on re-open
// ---------------------------------------------------------------------------

describe("AiAssistantPanelComponent — ngOnInit: healthSub replaced on re-open", () => {
   // 🔁 Regression-sensitive: without explicit unsubscription the first health check's result
   // can arrive after the second open has reset serverState to "checking", corrupting the state.
   it("should ignore results from the previous healthSub when panel re-opens", async () => {
      const aiService = makeAiService();
      const firstHealth$ = new Subject<boolean>();
      const secondHealth$ = new Subject<boolean>();
      aiService.checkHealth
         .mockReturnValueOnce(firstHealth$)
         .mockReturnValueOnce(secondHealth$);

      const { comp } = await renderComponent(aiService);

      aiService.panelOpen$.next(true); // open #1 → healthSub = firstHealth$
      aiService.panelOpen$.next(true); // open #2 → firstHealth$ unsubscribed, healthSub = secondHealth$

      firstHealth$.next(false); // stale result — must be ignored
      expect(comp.serverState).toBe("checking"); // still "checking" from second open

      secondHealth$.next(false); // current result — must apply
      expect(comp.serverState).toBe("offline");
   });
});

// ---------------------------------------------------------------------------
// Group 4: ngOnInit — localStorage restore
// ---------------------------------------------------------------------------

describe("AiAssistantPanelComponent — ngOnInit: localStorage restore", () => {
   it("should restore mode 'bottom' from localStorage", async () => {
      localStorage.setItem("ai-assistant-panel-mode", "bottom");
      const { comp } = await renderComponent();
      expect(comp.mode).toBe("bottom");
   });

   it("should ignore an invalid mode string and keep the default 'side'", async () => {
      localStorage.setItem("ai-assistant-panel-mode", "fullscreen");
      const { comp } = await renderComponent();
      expect(comp.mode).toBe("side");
   });

   it("should not restore sideWidth when the saved value is below MIN_SIZE (300)", async () => {
      localStorage.setItem("ai-assistant-panel-side-width", "50");
      const { comp } = await renderComponent();
      expect(comp.sideWidth).toBe(760); // DEFAULT_SIDE_WIDTH
   });
});

// ---------------------------------------------------------------------------
// Group 5: toggleMode
// ---------------------------------------------------------------------------

describe("AiAssistantPanelComponent — toggleMode", () => {
   it("should switch mode from 'side' to 'bottom'", async () => {
      const { comp } = await renderComponent();
      expect(comp.mode).toBe("side");
      comp.toggleMode();
      expect(comp.mode).toBe("bottom");
   });

   it("should persist the new mode to localStorage", async () => {
      const { comp } = await renderComponent();
      comp.toggleMode();
      expect(localStorage.getItem("ai-assistant-panel-mode")).toBe("bottom");
   });
});

// ---------------------------------------------------------------------------
// Group 6: close — baseline
// ---------------------------------------------------------------------------

describe("AiAssistantPanelComponent — close", () => {
   it("should set aiAssistantService.panelOpen to false", async () => {
      const aiService = makeAiService();
      aiService.panelOpen = true;
      const { comp } = await renderComponent(aiService);

      comp.close();

      expect(aiService.panelOpen).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 7: toggleCollapsed — baseline
// ---------------------------------------------------------------------------

describe("AiAssistantPanelComponent — toggleCollapsed", () => {
   it("should toggle panelCollapsed from false to true via the service", async () => {
      const aiService = makeAiService();
      aiService.panelCollapsed = false;
      const { comp } = await renderComponent(aiService);

      comp.toggleCollapsed();

      expect(aiService.panelCollapsed).toBe(true);
   });

   it("should toggle panelCollapsed from true to false via the service", async () => {
      const aiService = makeAiService();
      aiService.panelCollapsed = true;
      const { comp } = await renderComponent(aiService);

      comp.toggleCollapsed();

      expect(aiService.panelCollapsed).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 8: onResize — baseline (window resize clamps sideWidth / bottomHeight)
// ---------------------------------------------------------------------------

describe("AiAssistantPanelComponent — onResize", () => {
   // 🔁 Regression-sensitive: if the clamp is removed, the panel can grow wider than the
   // viewport and become unreachable/unusable without a page reload.
   it("should clamp sideWidth to 80% of window.innerWidth when mode is 'side'", async () => {
      const { comp } = await renderComponent();
      comp.mode = "side";
      comp.sideWidth = 9999;
      Object.defineProperty(window, "innerWidth", { value: 1000, writable: true, configurable: true });

      comp.onResize();

      expect(comp.sideWidth).toBe(800); // Math.floor(1000 * 0.8)
   });

   it("should not modify sideWidth when it is already within bounds", async () => {
      const { comp } = await renderComponent();
      comp.mode = "side";
      comp.sideWidth = 400;
      Object.defineProperty(window, "innerWidth", { value: 1000, writable: true, configurable: true });

      comp.onResize();

      expect(comp.sideWidth).toBe(400);
   });
});

// ---------------------------------------------------------------------------
// Group 9: startDrag — baseline
// ---------------------------------------------------------------------------

describe("AiAssistantPanelComponent — startDrag", () => {
   it("should call event.preventDefault() when the panel is not collapsed", async () => {
      const aiService = makeAiService();
      aiService.panelCollapsed = false;
      const { comp } = await renderComponent(aiService);
      const event = { clientX: 700, clientY: 0, preventDefault: vi.fn() } as any;

      comp.startDrag(event);

      expect(event.preventDefault).toHaveBeenCalled();
   });

   // 🔁 Regression-sensitive: the collapsed guard must bail out before attaching listeners;
   // if omitted, a collapsed-state drag sets up mousemove handlers that move an invisible panel.
   it("should not call event.preventDefault() and not start drag when collapsed", async () => {
      const aiService = makeAiService();
      aiService.panelCollapsed = true;
      const { comp } = await renderComponent(aiService);
      const originalWidth = comp.sideWidth;
      const event = { clientX: 700, clientY: 0, preventDefault: vi.fn() } as any;

      comp.startDrag(event);
      // Verify no mousemove listener was attached by checking sideWidth is unchanged
      document.dispatchEvent(new MouseEvent("mousemove", { clientX: 600, bubbles: true }));

      expect(event.preventDefault).not.toHaveBeenCalled();
      expect(comp.sideWidth).toBe(originalWidth);
   });
});

// ---------------------------------------------------------------------------
// Group 10: onDrag (via mousemove after startDrag) — baseline
// ---------------------------------------------------------------------------

describe("AiAssistantPanelComponent — onDrag via mousemove", () => {
   beforeEach(() => {
      // Ensure maxWidth (innerWidth * 0.8) does not clamp the test values.
      Object.defineProperty(window, "innerWidth", { value: 2000, writable: true, configurable: true });
   });

   // 🔁 Regression-sensitive: dragging leftward (clientX decreasing) must widen the side panel;
   // swapping the delta direction makes drag feel inverted and the panel shrinks when pulled.
   it("should increase sideWidth when dragging left in side mode", async () => {
      const { comp } = await renderComponent();
      comp.mode = "side";
      comp.sideWidth = 760;

      comp.startDrag({ clientX: 700, clientY: 0, preventDefault: vi.fn() } as any);
      document.dispatchEvent(new MouseEvent("mousemove", { clientX: 650, bubbles: true }));

      // delta = 700 - 650 = 50 → 760 + 50 = 810
      expect(comp.sideWidth).toBe(810);
   });

   it("should not update sideWidth once mouseup ends the drag", async () => {
      const { comp } = await renderComponent();
      comp.mode = "side";
      comp.sideWidth = 760;

      comp.startDrag({ clientX: 700, clientY: 0, preventDefault: vi.fn() } as any);
      document.dispatchEvent(new MouseEvent("mouseup", { bubbles: true }));
      document.dispatchEvent(new MouseEvent("mousemove", { clientX: 600, bubbles: true }));

      // After mouseup the drag is stopped; mousemove should have no effect.
      expect(comp.sideWidth).toBe(760);
   });
});
