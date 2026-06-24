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
 * Shared test fixtures and renderComponent factory for PortalAppComponent multi-pass TL specs.
 * Consumed by:
 *   app.component.interaction.tl.spec.ts  (Pass 1 — interaction contracts)
 *   app.component.risk.tl.spec.ts         (Pass 2 — async / risk paths)
 *
 * app.component.tl.spec.ts (Bug-C memory leak) keeps its own simpler renderComponent.
 *
 * BroadcastChannel note:
 *   vi.stubGlobal sets BroadcastChannel to a vi.fn() at module load.
 *   vi.restoreAllMocks() in afterEach calls mockRestore() → mockReset() on every tracked vi.fn(),
 *   clearing its mockImplementation.  Call beforeEachCleanup() in beforeEach to re-apply it.
 */

import { vi } from "vitest";
import { DOCUMENT } from "@angular/common";
import { HttpClient } from "@angular/common/http";
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { ComponentFixture } from "@angular/core/testing";
import { Title } from "@angular/platform-browser";
import { ActivatedRoute, Router } from "@angular/router";
import { render } from "@testing-library/angular";
import { NgbDatepickerConfig, NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { Observable, of, Subject } from "rxjs";

import { AiAssistantService } from "../../../../shared/ai-assistant/ai-assistant.service";
import { AiAssistantDialogService } from "../common/services/ai-assistant-dialog.service";
import { LicenseInfoService } from "../common/services/license-info.service";
import { FirstDayOfWeekService } from "../common/services/first-day-of-week.service";
import { OpenComposerService } from "../common/services/open-composer.service";
import { LogoutService } from "../../../../shared/util/logout.service";
import { HideNavService } from "./services/hide-nav.service";
import { PortalModelService } from "./services/portal-model.service";
import { HistoryBarService } from "./services/history-bar.service";
import { PortalTabsService } from "./services/portal-tabs.service";
import { CurrentRouteService } from "./services/current-route.service";
import { GettingStartedService } from "../widget/dialog/getting-started-dialog/service/getting-started.service";
import { MessageDialog } from "../widget/dialog/message-dialog/message-dialog.component";
import { PortalAppComponent } from "./app.component";
import { PortalModel } from "./portal-model";
import { PortalTab, PortalTabs } from "./portal-tab";

// ---------------------------------------------------------------------------
// Model factory
// ---------------------------------------------------------------------------

export function makePortalModel(overrides: Partial<PortalModel> = {}): PortalModel {
   return {
      composerEnabled:         false,
      aiAssistantVisible:      false,
      accessible:              false,
      title:                   "Test Portal",
      logoutUrl:               "http://example.com/logout",
      hasDashboards:           false,
      homeVisible:             true,
      helpURL:                 null,
      profiling:               false,
      profile:                 false,
      elasticLicenseExhausted: false,
      currentUser:             null,
      helpVisible:             true,
      preferencesVisible:      true,
      logoutVisible:           true,
      homeLink:                "/portal",
      reportEnabled:           true,
      dashboardEnabled:        true,
      newDatasourceEnabled:    true,
      newWorksheetEnabled:     true,
      newViewsheetEnabled:     true,
      customLogo:              false,
      ...overrides,
   } as PortalModel;
}

// ---------------------------------------------------------------------------
// Tab fixture constants
// ---------------------------------------------------------------------------

export const DASHBOARD_TAB: PortalTab = {
   name: PortalTabs.DASHBOARD, label: "Dashboard", uri: "tab/dashboard", custom: false, visible: true,
};
export const REPORT_TAB: PortalTab = {
   name: PortalTabs.REPORT, label: "Report", uri: "tab/report", custom: false, visible: true,
};
export const DATA_TAB: PortalTab = {
   name: PortalTabs.DATA, label: "Data", uri: "tab/data", custom: false, visible: true,
};
export const SCHEDULE_TAB: PortalTab = {
   name: PortalTabs.SCHEDULE, label: "Schedule", uri: "tab/schedule", custom: false, visible: true,
};

// ---------------------------------------------------------------------------
// renderComponent
// ---------------------------------------------------------------------------

export interface RenderOpts {
   currentUrl?: string;
   // HTTP responses consumed FIFO before ngOnInit's portal-model GET
   // (needed when constructor subscription fires showGettingStarted() first)
   preInitHttpResponses?: unknown[];
   composerOpen?: boolean;
   portalTabs?: PortalTab[];
   queryParams?: Record<string, string>;
}

export interface AppComponentMocks {
   comp:             PortalAppComponent;
   fixture:          ComponentFixture<PortalAppComponent>;
   httpMock:         { get: ReturnType<typeof vi.fn>; post: ReturnType<typeof vi.fn> };
   gettingStartedSvc: { start: ReturnType<typeof vi.fn> };
   logoutSvc:        { inGracePeriod: Observable<boolean>; setLogoutUrl: ReturnType<typeof vi.fn>; logout: ReturnType<typeof vi.fn> };
   routerMock:       { navigate: ReturnType<typeof vi.fn>; events: Observable<never> };
   titleMock:        { setTitle: ReturnType<typeof vi.fn> };
   aiAssistantSvc:   { loadCurrentUser: ReturnType<typeof vi.fn>; aiAssistantVisible: boolean };
   modalSvc:         { open: ReturnType<typeof vi.fn> };
   historyBarSvc:    { refreshStatus: ReturnType<typeof vi.fn> };
}

export async function renderComponent(opts: RenderOpts = {}): Promise<AppComponentMocks> {
   const httpMock = {
      get:  vi.fn(() => of(makePortalModel())),
      post: vi.fn(() => of(null)),
   };

   if(opts.preInitHttpResponses) {
      for(const val of opts.preInitHttpResponses) {
         httpMock.get.mockReturnValueOnce(of(val as PortalModel));
      }
   }

   const gettingStartedSvc = { start: vi.fn() };
   const logoutSvc         = { inGracePeriod: of(false) as Observable<boolean>, setLogoutUrl: vi.fn(), logout: vi.fn() };
   const routerMock        = { navigate: vi.fn(), events: of() as Observable<never> };
   const titleMock         = { setTitle: vi.fn() };
   const aiAssistantSvc    = { loadCurrentUser: vi.fn(), aiAssistantVisible: false };
   const historyBarSvc     = { refreshStatus: vi.fn() };
   const modalSvc = {
      open: vi.fn().mockImplementation(() => ({
         result:            new Promise<unknown>(() => {}),
         componentInstance: { onCommit: new Subject<string>(), onCancel: new Subject<void>() },
         close:             vi.fn(),
         dismiss:           vi.fn(),
      })),
   };

   const { fixture } = await render(PortalAppComponent, {
      schemas:            [NO_ERRORS_SCHEMA],
      componentImports:   [],
      componentProviders: [
         { provide: HttpClient,               useValue: httpMock },
         { provide: AiAssistantService,       useValue: aiAssistantSvc },
         { provide: AiAssistantDialogService, useValue: {} },
         { provide: NgbModal,                 useValue: modalSvc },
         { provide: NgbDatepickerConfig,      useValue: { minDate: null, maxDate: null, firstDayOfWeek: 1 } },
         { provide: ActivatedRoute,           useValue: { queryParams: of(opts.queryParams ?? {}) } },
         { provide: Router,                   useValue: routerMock },
         { provide: PortalTabsService,        useValue: {
               getPortalTabs: vi.fn(() => of(opts.portalTabs ?? [])),
               getCustomTabs: vi.fn(() => of([])),
            }
         },
         { provide: HideNavService,           useValue: { hideNav: false } },
         { provide: PortalModelService,       useValue: { model: undefined } },
         { provide: HistoryBarService,        useValue: historyBarSvc },
         { provide: CurrentRouteService,      useValue: { currentUrl: of(opts.currentUrl ?? "/portal") } },
         { provide: OpenComposerService,      useValue: { composerOpen: of(opts.composerOpen ?? false) } },
         { provide: LicenseInfoService,       useValue: { getLicenseInfo: vi.fn(() => of({})) } },
         { provide: FirstDayOfWeekService,    useValue: { getFirstDay: vi.fn(() => of({ isoFirstDay: 1 })) } },
         { provide: LogoutService,            useValue: logoutSvc },
         { provide: GettingStartedService,    useValue: gettingStartedSvc },
         { provide: Title,                    useValue: titleMock },
         { provide: DOCUMENT,                 useValue: document },
      ],
   });

   return {
      comp: fixture.componentInstance,
      fixture,
      httpMock,
      gettingStartedSvc,
      logoutSvc,
      routerMock,
      titleMock,
      aiAssistantSvc,
      modalSvc,
      historyBarSvc,
   };
}

// ---------------------------------------------------------------------------
// beforeEach cleanup (call from every spec's beforeEach)
// ---------------------------------------------------------------------------

/**
 * Reset cross-test state.  Call inside `beforeEach()` in every spec that imports
 * this helper module.
 *
 * Responsibilities:
 *  - Clears document.body.className so ngOnInit's "app-loaded" guard starts fresh
 *  - Resets MessageDialog dedup guard (rule C4)
 */
export function beforeEachCleanup(): void {
   document.body.className = "";
   MessageDialog.lastMessage = null;
   (MessageDialog as unknown as Record<string, unknown>).lastMessageTS = 0;
}
