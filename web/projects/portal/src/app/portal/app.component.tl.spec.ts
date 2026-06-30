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
 * PortalAppComponent — single pass
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3]  — ngOnDestroy: window.removeEventListener called for 'message' with the same
 *                        stored reference as addEventListener; not called before destroy;
 *                        handleMessageEvent not invoked after destroy
 *   Group 2 [Risk 3]  — handleMessageEvent: null / unknown payloads do not throw
 *   Group 3 [baseline] — ngOnInit: window.addEventListener('message', ...) registration
 *
 * Method coverage (this pass):
 *   ngOnInit()           | test          | message listener registration (Group 3)
 *   ngOnDestroy()        | test          | message listener cleanup (Group 1)
 *   handleMessageEvent() | test          | payload guard + post-destroy dispatch guard (Groups 1–2)
 *   showPreferences()    | skip          | dialog flow; out of scope — requires full modal/template wiring
 *   checkDefaultTab()    | skip          | routing side effect; out of scope — covered in portal routing specs
 *   openComposer()       | skip          | BroadcastChannel + window.open; out of scope — browser API heavy
 *   remaining public API | skip          | tab tooltips, logout, profiling — UI/display; out of scope this pass
 *
 * HTTP: MSW inline server.use() via setupPortalMsw()
 *
 * MSW note: vitest-setup-tl.ts starts MSW with onUnhandledRequest:"error", so every ngOnInit HTTP
 * subscription must be intercepted. Real HttpClient (provideHttpClient) + MSW handlers cover
 * portal model, tabs, license, and first-day-of-week. Non-HTTP boundaries (router, modal, AI
 * assistant) are stubbed via render() providers.
 *
 * ALT note: each it() may call render() at most once. Multi-cycle create/destroy coverage uses
 * it.each() so each case gets a fresh TestBed configuration without resetTestingModule().
 *
 * KEY contracts:
 *   ngOnInit registers exactly one window 'message' listener via the stored _onMessage reference.
 *   ngOnDestroy must call removeEventListener with that same reference — anonymous handlers cannot
 *   be removed and listeners accumulate across navigations.
 *   handleMessageEvent must not run after destroy when postMessage is dispatched on window.
 *
 * Out of scope this pass: full portal model loading side effects, default-tab routing, tab UI
 * rendering, composer launch, logout/profiling, showGettingStarted HTTP flow.
 */

import { DOCUMENT } from "@angular/common";
import { provideHttpClient } from "@angular/common/http";
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { Title } from "@angular/platform-browser";
import { ActivatedRoute, Router } from "@angular/router";
import { NgbDatepickerConfig, NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { render } from "@testing-library/angular";
import { http, HttpResponse } from "msw";
import { of } from "rxjs";
import { server } from "@test-mocks/server";
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

// ---------------------------------------------------------------------------
// Shared fixtures
// ---------------------------------------------------------------------------

/** Minimal portal model — satisfies property access in the ngOnInit HTTP subscription. */
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

/** MSW handlers for every HTTP call fired during ngOnInit. */
function setupPortalMsw() {
   server.use(
      http.get("*/api/portal/get-portal-model", () => HttpResponse.json(PORTAL_MODEL_STUB)),
      http.get("*/api/portal/get-portal-tabs", () => HttpResponse.json([])),
      http.get("*/api/license-info", () => HttpResponse.json({})),
      http.get("*/api/first-day-of-week", () =>
         HttpResponse.json({ javaFirstDay: 1, isoFirstDay: 1 })),
   );
}

/** Non-HTTP dependencies stubbed at the render() provider level. */
function makeProviders() {
   return [
      provideHttpClient(),
      PortalTabsService,
      LicenseInfoService,
      FirstDayOfWeekService,
      PortalModelService,
      HideNavService,
      HistoryBarService,
      { provide: AiAssistantService, useValue: {
            loadCurrentUser:    vi.fn(),
            aiAssistantVisible: false,
         }
      },
      { provide: AiAssistantDialogService, useValue: {} },
      { provide: NgbModal, useValue: {
            open: vi.fn(() => ({ result: Promise.resolve(), componentInstance: {} })),
         }
      },
      { provide: NgbDatepickerConfig, useValue: { minDate: null, maxDate: null, firstDayOfWeek: 1 } },
      { provide: ActivatedRoute, useValue: { queryParams: of({}) } },
      { provide: Router,         useValue: { navigate: vi.fn(), events: of(), url: "/portal" } },
      { provide: CurrentRouteService, useValue: { currentUrl: of("/portal") } },
      { provide: OpenComposerService, useValue: { composerOpen: of(false) } },
      { provide: LogoutService, useValue: {
            inGracePeriod: of(false),
            setLogoutUrl:  vi.fn(),
            logout:        vi.fn(),
         }
      },
      { provide: GettingStartedService, useValue: { start: vi.fn() } },
      { provide: Title,    useValue: { setTitle: vi.fn() } },
      { provide: DOCUMENT, useValue: document },
   ];
}

async function renderComponent() {
   setupPortalMsw();
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
// Group 1 [Risk 3]: ngOnDestroy — 'message' listener cleanup
// ---------------------------------------------------------------------------

describe("PortalAppComponent — ngOnDestroy: 'message' listener cleanup", () => {
   // 🔁 Regression-sensitive: without removeEventListener, listeners accumulate across navigations
   // and handleMessageEvent may fire N times per postMessage.
   it("should call window.removeEventListener for 'message' on destroy", async () => {
      const addSpy    = vi.spyOn(window, "addEventListener");
      const removeSpy = vi.spyOn(window, "removeEventListener");

      const { fixture } = await renderComponent();

      const addCount     = addSpy.mock.calls.filter(c => c[0] === "message").length;
      const removeBefore = removeSpy.mock.calls.filter(c => c[0] === "message").length;
      expect(addCount).toBe(1);
      expect(removeBefore).toBe(0);

      fixture.destroy();

      const removeCount = removeSpy.mock.calls.filter(c => c[0] === "message").length;
      expect(removeCount).toBe(addCount);
   });

   // 🔁 Regression-sensitive: removeEventListener is a no-op unless the handler reference matches
   // addEventListener exactly. it.each gives one render() per case (ALT constraint).
   it.each([0, 1, 2])(
      "should pass the same function reference to removeEventListener as to addEventListener (cycle %i)",
      async () => {
         const addSpy    = vi.spyOn(window, "addEventListener");
         const removeSpy = vi.spyOn(window, "removeEventListener");

         const { fixture } = await renderComponent();
         fixture.destroy();

         const addedRef   = addSpy.mock.calls.find(c => c[0] === "message")?.[1];
         const removedRef = removeSpy.mock.calls.find(c => c[0] === "message")?.[1];
         expect(removedRef).toBe(addedRef);
      },
   );

   it("should not remove the 'message' listener before the component is destroyed", async () => {
      const removeSpy = vi.spyOn(window, "removeEventListener");
      await renderComponent();
      const removeCount = removeSpy.mock.calls.filter(c => c[0] === "message").length;
      expect(removeCount).toBe(0);
   });

   // 🔁 Regression-sensitive: a leaked listener would still invoke handleMessageEvent after destroy.
   it("should not call handleMessageEvent after the component is destroyed", async () => {
      const { fixture, comp } = await renderComponent();
      const handlerSpy = vi.spyOn(comp as any, "handleMessageEvent");

      fixture.destroy();
      window.dispatchEvent(new MessageEvent("message", {
         data: { event: "openDialog", dialogName: "preferences" },
      }));

      expect(handlerSpy).not.toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 2 [Risk 3]: handleMessageEvent — payload guard
// ---------------------------------------------------------------------------

describe("PortalAppComponent — handleMessageEvent: payload guard", () => {
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

// ---------------------------------------------------------------------------
// Group 3 [baseline]: ngOnInit — message listener registration
// ---------------------------------------------------------------------------

describe("PortalAppComponent — Baseline: message listener registration", () => {
   it("ngOnInit calls window.addEventListener('message', ...)", async () => {
      const addSpy = vi.spyOn(window, "addEventListener");

      await renderComponent();

      const messageCalls = addSpy.mock.calls.filter(([type]) => type === "message");
      expect(messageCalls.length).toBe(1);
   });
});
