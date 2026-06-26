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
 * Shared test helpers for DashboardTabComponent multi-pass suite.
 * Imported by interaction / risk / display spec files.
 */

import { Component, Directive, NO_ERRORS_SCHEMA } from "@angular/core";
import { provideHttpClient } from "@angular/common/http";
import { ActivatedRoute, Router, RouterOutlet } from "@angular/router";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { BehaviorSubject, Subject, of } from "rxjs";
import { render } from "@testing-library/angular";

import { ModelService } from "../../widget/services/model.service";
import { DashboardTabComponent } from "./dashboard-tab.component";
import { DashboardTabModel } from "./dashboard-tab-model";
import { DashboardModel } from "../../common/data/dashboard-model";
import { DashboardService } from "./dashboard.service";
import { CurrentRouteService } from "../services/current-route.service";
import { HideNavService } from "../services/hide-nav.service";
import { AssetLoadingService } from "../../common/services/asset-loading.service";
import { AiAssistantService } from "../../../../../shared/ai-assistant/ai-assistant.service";
import { ResponsiveTabsComponent } from "../../widget/responsive-tabs/responsive-tabs.component";
import { FixedDropdownDirective } from "../../widget/fixed-dropdown/fixed-dropdown.directive";
import { EnterClickDirective } from "../../widget/directive/enter-click.directive";
import { DefaultFocusDirective } from "../../widget/directive/default-focus.directive";

// ---------------------------------------------------------------------------
// Stubs — replace DI-heavy imports so the component renders in isolation
// ---------------------------------------------------------------------------

@Component({ selector: "responsive-tabs", template: "", standalone: true })
export class ResponsiveTabsStub {}

@Component({ selector: "router-outlet", template: "", standalone: true })
export class RouterOutletStub {}

@Directive({ selector: "[fixedDropdown]", standalone: true })
export class FixedDropdownStub {}

@Directive({ selector: "[enterClick]", standalone: true })
export class EnterClickStub {}

@Directive({ selector: "[defaultFocus]", standalone: true })
export class DefaultFocusStub {}

// ---------------------------------------------------------------------------
// Test data
// ---------------------------------------------------------------------------

export const DASHBOARD_1 = {
   name: "DB1", label: "Dashboard 1", identifier: "db1-id", type: "u",
} as unknown as DashboardModel;

export const DASHBOARD_2 = {
   name: "DB2", label: "Dashboard 2", identifier: "db2-id", type: "u",
} as unknown as DashboardModel;

export const TAB_MODEL: DashboardTabModel = {
   dashboards: [DASHBOARD_1, DASHBOARD_2],
   dashboardTabsTop: false,
   editable: true,
   composerEnabled: true,
};

// ---------------------------------------------------------------------------
// Module-level mock singletons — shared across all three spec files.
// Each spec file must call clearAllMocks() in beforeEach.
// ---------------------------------------------------------------------------

export const newDashboard$ = new Subject<void>();

export const ROUTER_MOCK = {
   navigate: vi.fn().mockResolvedValue(true),
};

export const MODAL_MOCK = {
   open: vi.fn().mockImplementation(() => ({
      result: new Promise<any>(() => {}),
      componentInstance: {},
      close: vi.fn(),
      dismiss: vi.fn(),
   })),
};

export const HIDE_NAV_SERVICE_MOCK = {
   appendParameter: vi.fn().mockImplementation((p: any) => p),
};

export const MODEL_SERVICE_MOCK = {
   getModel: vi.fn().mockReturnValue(of(TAB_MODEL)),
};

export const ASSET_LOADING_SERVICE_MOCK = {
   isLoading: vi.fn().mockReturnValue(false),
};

export const AI_ASSISTANT_SERVICE_MOCK = {
   setContextTypeFieldValue: vi.fn(),
};

// ---------------------------------------------------------------------------
// clearAllMocks — call in beforeEach of every spec file
// ---------------------------------------------------------------------------

export function clearAllMocks(): void {
   ROUTER_MOCK.navigate.mockClear().mockResolvedValue(true);
   MODAL_MOCK.open.mockClear();
   HIDE_NAV_SERVICE_MOCK.appendParameter.mockClear().mockImplementation((p: any) => p);
   MODEL_SERVICE_MOCK.getModel.mockClear().mockReturnValue(of(TAB_MODEL));
   ASSET_LOADING_SERVICE_MOCK.isLoading.mockClear().mockReturnValue(false);
   AI_ASSISTANT_SERVICE_MOCK.setContextTypeFieldValue.mockClear();
}

// ---------------------------------------------------------------------------
// renderComp
// ---------------------------------------------------------------------------

export interface RenderOpts {
   /** Initial value emitted by currentRouteService.dashboard (null = no selection). */
   currentDashboard?: string | null;
   /** Override the DashboardTabModel delivered by route.data. */
   model?: DashboardTabModel;
}

export async function renderComp(opts: RenderOpts = {}) {
   const routeData$ = new BehaviorSubject<any>({
      dashboardTabModel: opts.model ?? TAB_MODEL,
   });
   const currentDashboard$ = new BehaviorSubject<string | null>(
      opts.currentDashboard ?? null
   );

   const { fixture } = await render(DashboardTabComponent, {
      providers: [
         provideHttpClient(),
         { provide: Router, useValue: ROUTER_MOCK },
         { provide: ActivatedRoute, useValue: { data: routeData$ } },
         { provide: NgbModal, useValue: MODAL_MOCK },
         { provide: ModelService, useValue: MODEL_SERVICE_MOCK },
         { provide: HideNavService, useValue: HIDE_NAV_SERVICE_MOCK },
         { provide: CurrentRouteService, useValue: { dashboard: currentDashboard$ } },
         { provide: AssetLoadingService, useValue: ASSET_LOADING_SERVICE_MOCK },
         { provide: AiAssistantService, useValue: AI_ASSISTANT_SERVICE_MOCK },
      ],
      componentProviders: [
         { provide: DashboardService, useValue: { newDashboard: newDashboard$ } },
      ],
      importOverrides: [
         { replace: ResponsiveTabsComponent, with: ResponsiveTabsStub },
         { replace: RouterOutlet, with: RouterOutletStub },
         { replace: FixedDropdownDirective, with: FixedDropdownStub },
         { replace: EnterClickDirective, with: EnterClickStub },
         { replace: DefaultFocusDirective, with: DefaultFocusStub },
      ],
      schemas: [NO_ERRORS_SCHEMA],
   });

   return { comp: fixture.componentInstance as DashboardTabComponent, fixture };
}
