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
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3]  — ngOnDestroy: window.removeEventListener called for 'message' with same
 *                        reference as addEventListener; not called before destroy
 *
 * HTTP: provideHttpClient() + MSW (vitest-setup-tl.ts enforces onUnhandledRequest:"error").
 * Non-HTTP dependencies are stubbed via render() providers.
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

function setupPortalMsw() {
   server.use(
      http.get("*/api/portal/get-portal-model", () => HttpResponse.json(PORTAL_MODEL_STUB)),
      http.get("*/api/portal/get-portal-tabs", () => HttpResponse.json([])),
      http.get("*/api/license-info", () => HttpResponse.json({})),
      http.get("*/api/first-day-of-week", () =>
         HttpResponse.json({ javaFirstDay: 1, isoFirstDay: 1 })),
   );
}

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

describe("PortalAppComponent — ngOnDestroy: 'message' listener cleanup", () => {
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

   // ALT: one render() per it — cannot call render() twice in the same test.
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

   it("should not call handleMessageEvent after the component is destroyed", async () => {
      const { fixture, comp } = await renderComponent();
      const handlerSpy = vi.spyOn(comp as any, "handleMessageEvent");

      fixture.destroy();
      window.dispatchEvent(new MessageEvent("message", {
         data: { event: "openDialog", dialogName: "preferences" },
      }));

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

describe("PortalAppComponent — Baseline: message listener registration", () => {
   it("ngOnInit calls window.addEventListener('message', ...)", async () => {
      const addSpy = vi.spyOn(window, "addEventListener");

      await renderComponent();

      const messageCalls = addSpy.mock.calls.filter(([type]) => type === "message");
      expect(messageCalls.length).toBe(1);
   });
});
