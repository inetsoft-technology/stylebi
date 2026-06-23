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
 * PortalAppComponent — Memory leak: window 'message' listener not removed on destroy
 * Issue #75490
 * @confirmed-bug Bug-C  app.component.ts:185
 *   ngOnInit() calls window.addEventListener("message", handler, false), but
 *   ngOnDestroy() (app.component.ts:200) only unsubscribes RxJS subscriptions and never
 *   calls window.removeEventListener("message", ...).
 *
 *   Impact: each time PortalAppComponent is created (navigation / page reload), one extra
 *   'message' listener accumulates on window. In a long session handleMessageEvent may be
 *   called N times per postMessage, potentially opening N dialogs simultaneously.
 *
 *   Fix:
 *     // Save reference in ngOnInit
 *     private readonly _onMessage = (evt: MessageEvent) => this.handleMessageEvent(evt);
 *     window.addEventListener("message", this._onMessage, false);
 *
 *     // Remove in ngOnDestroy
 *     window.removeEventListener("message", this._onMessage, false);
 *
 *   it.fails convention:
 *     - While the bug exists: the inner expect fails → it.fails is marked ✅ (expected failure)
 *     - After the fix: the inner expect passes → it.fails is marked ❌ (remove .fails)
 *
 * MSW note: vitest-setup-tl.ts starts MSW with onUnhandledRequest:"error", so all HTTP
 * requests must be intercepted. This file replaces HttpClient via providers (module-level
 * injector) with a plain vi.fn() mock returning of(...), preventing any real HTTP traffic from
 * reaching MSW. Using providers (vs componentProviders) is safe here because every injected
 * service is replaced with a mock — no real Angular DI scoping distinctions apply.
 */

/**
 * PortalAppComponent — single pass
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3]  — ngOnDestroy: window.removeEventListener called for 'message' with same
 *                        reference as addEventListener; not called before destroy
 *
 * Suspected bugs: none remaining after Bug #75490 fix.
 *
 * Out of scope this pass: full portal model loading, routing, UI rendering.
 */

import { DOCUMENT } from "@angular/common";
import { HttpClient } from "@angular/common/http";
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { Title } from "@angular/platform-browser";
import { ActivatedRoute, Router } from "@angular/router";
import { NgbDatepickerConfig, NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { render } from "@testing-library/angular";
import { of } from "rxjs";
import { AiAssistantService } from "../../../../shared/ai-assistant/ai-assistant.service";
import { LogoutService } from "../../../../shared/util/logout.service";
import { AiAssistantDialogService } from "../common/services/ai-assistant-dialog.service";
import { FirstDayOfWeekService } from "../common/services/first-day-of-week.service";
import { LicenseInfoService } from "../common/services/license-info.service";
import { OpenComposerService } from "../common/services/open-composer.service";
import { GettingStartedService } from "../widget/dialog/getting-started-dialog/service/getting-started.service";

import { PortalAppComponent } from "./app.component";
import { CurrentRouteService } from "./services/current-route.service";
import { HideNavService } from "./services/hide-nav.service";
import { HistoryBarService } from "./services/history-bar.service";
import { PortalModelService } from "./services/portal-model.service";
import { PortalTabsService } from "./services/portal-tabs.service";

// ── Minimal portal model stub — satisfies property access in the ngOnInit HTTP subscription ──

const PORTAL_MODEL_STUB = {
   composerEnabled:          false,
   aiAssistantVisible:       false,
   accessible:               false,
   title:                    "Test Portal",
   logoutUrl:                "",
   hasDashboards:            false,
   homeVisible:              true,
   helpURL:                  null,
   profiling:                false,
   elasticLicenseExhausted:  false,
};

// ── Component-level provider mocks (replaces all constructor-injected services) ──────────────
// ---------------------------------------------------------------------------
// Shared fixtures
// ---------------------------------------------------------------------------

function makeProviders() {
   return [
      // HttpClient: mock directly to prevent ngOnInit's http.get() from making real HTTP requests
      { provide: HttpClient, useValue: {
            get:  vi.fn(() => of(PORTAL_MODEL_STUB)),
            post: vi.fn(() => of(null)),
         }
      },
      // AI Assistant
      { provide: AiAssistantService, useValue: {
            loadCurrentUser:    vi.fn(),
            aiAssistantVisible: false,
         }
      },
      { provide: AiAssistantDialogService, useValue: {} },
      // NG-Bootstrap
      { provide: NgbModal,            useValue: { open: vi.fn(() => ({ result: Promise.resolve(), componentInstance: {} })) } },
      { provide: NgbDatepickerConfig,  useValue: { minDate: null, maxDate: null, firstDayOfWeek: 1 } },
      // Router / Route
      { provide: ActivatedRoute, useValue: { queryParams: of({}) } },
      { provide: Router,         useValue: { navigate: vi.fn(), events: of() } },
      // Portal services
      { provide: PortalTabsService,   useValue: { getPortalTabs: vi.fn(() => of([])), getCustomTabs: vi.fn(() => of([])) } },
      { provide: HideNavService,      useValue: { hideNav: false } },
      { provide: PortalModelService,  useValue: { model: undefined } },
      { provide: HistoryBarService,   useValue: { refreshStatus: vi.fn() } },
      { provide: CurrentRouteService, useValue: { currentUrl: of("/portal") } },
      { provide: OpenComposerService, useValue: { composerOpen: of(false) } },
      // Common services
      { provide: LicenseInfoService,    useValue: { getLicenseInfo: vi.fn(() => of({})) } },
      { provide: FirstDayOfWeekService, useValue: { getFirstDay: vi.fn(() => of({ isoFirstDay: 1 })) } },
      { provide: LogoutService, useValue: {
            inGracePeriod: of(false),
            setLogoutUrl:  vi.fn(),
            logout:        vi.fn(),
         }
      },
      { provide: GettingStartedService, useValue: { start: vi.fn() } },
      // Angular built-ins
      { provide: Title,    useValue: { setTitle: vi.fn() } },
      { provide: DOCUMENT, useValue: document },
   ];
}

async function renderComponent() {
   const result = await render(PortalAppComponent, {
      schemas: [NO_ERRORS_SCHEMA],
      componentImports: [],
      providers: makeProviders(),
   });
   return {
      fixture: result.fixture,
      comp: result.fixture.componentInstance as PortalAppComponent,
   };
}

afterEach(() => {
   vi.restoreAllMocks();
   document.body.className = document.body.className.replace(/\bapp-loaded\b/g, "").trim();
});

// ---------------------------------------------------------------------------
// Group 1: ngOnDestroy — 'message' listener removed via stored reference
// ---------------------------------------------------------------------------

describe("PortalAppComponent — ngOnDestroy: 'message' listener cleanup", () => {
   // 🔁 Regression-sensitive: Bug #75490 — without a stored reference, removeEventListener is
   // a no-op and listeners accumulate across navigations, firing handleMessageEvent N times per
   // postMessage and leaking component instances.
   it("should call window.removeEventListener for 'message' on destroy", async () => {
      const addSpy    = vi.spyOn(window, "addEventListener");
      const removeSpy = vi.spyOn(window, "removeEventListener");

      const { fixture } = await renderComponent();

      const addCount    = addSpy.mock.calls.filter(c => c[0] === "message").length;
      const removeBefore = removeSpy.mock.calls.filter(c => c[0] === "message").length;
      expect(addCount).toBe(1);
      expect(removeBefore).toBe(0);

      fixture.destroy();

      const removeCount = removeSpy.mock.calls.filter(c => c[0] === "message").length;
      expect(removeCount).toBe(addCount);
   });

   it("should pass the same function reference to removeEventListener as to addEventListener", async () => {
      const addSpy    = vi.spyOn(window, "addEventListener");
      const removeSpy = vi.spyOn(window, "removeEventListener");

      for(let i = 0; i < 3; i++) {
         const { fixture } = await renderComponent();
         fixture.destroy();
      }

      const addedRefs   = addSpy.mock.calls.filter(c => c[0] === "message").map(c => c[1]);
      const removedRefs = removeSpy.mock.calls.filter(c => c[0] === "message").map(c => c[1]);
      expect(addedRefs.length).toBe(3);
      addedRefs.forEach((ref, i) => expect(removedRefs[i]).toBe(ref));
   });

   it("should not remove the 'message' listener before the component is destroyed", async () => {
      const removeSpy = vi.spyOn(window, "removeEventListener");
      await renderComponent();
      const removeCount = removeSpy.mock.calls.filter(c => c[0] === "message").length;
      expect(removeCount).toBe(0);
   });

   it("should not call handleMessageEvent after the component is destroyed", async () => {
      const { fixture, comp } = await renderComponent();
      const handlerSpy = vi.spyOn(comp as any, "handleMessageEvent");

      fixture.destroy();
      window.dispatchEvent(new MessageEvent("message", { data: { event: "openDialog", dialogName: "preferences" } }));

      expect(handlerSpy).not.toHaveBeenCalled();
   });

   it("handleMessageEvent does not throw on null or unknown data payloads", async () => {
      const { fixture } = await renderComponent();
      const comp = fixture.componentInstance;

      expect(() => {
         comp.handleMessageEvent({ data: null } as any);
         comp.handleMessageEvent({ data: {} } as any);
         comp.handleMessageEvent({ data: { event: "unknown" } } as any);
      }).not.toThrow();
   });
});

// ── Baseline: verify ngOnInit registers the message listener ──────────────────

describe("PortalAppComponent — Baseline: message listener registration", () => {

   it("ngOnInit calls window.addEventListener('message', ...)", async () => {
      const addSpy = vi.spyOn(window, "addEventListener");

      await renderComponent();

      const messageCalls = addSpy.mock.calls.filter(([type]) => type === "message");
      expect(messageCalls.length).toBe(1);
   });
});

