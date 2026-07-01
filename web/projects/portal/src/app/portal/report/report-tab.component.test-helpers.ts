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

import { HttpClient, provideHttpClient } from "@angular/common/http";
import { ChangeDetectorRef, NgZone } from "@angular/core";
import { TestBed } from "@angular/core/testing";
import { DomSanitizer } from "@angular/platform-browser";
import { ActivatedRoute, Router } from "@angular/router";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { BehaviorSubject, Subject } from "rxjs";

import { AiAssistantService, ContextType } from "../../../../../shared/ai-assistant/ai-assistant.service";
import { RepositoryEntry } from "../../../../../shared/data/repository-entry";
import { RepositoryEntryType } from "../../../../../shared/data/repository-entry-type.enum";
import { AssetLoadingService } from "../../common/services/asset-loading.service";
import { OpenComposerService } from "../../common/services/open-composer.service";
import { ViewsheetClientService } from "../../common/viewsheet-client";
import { RepositoryTreeService } from "../../widget/repository-tree/repository-tree.service";
import { CurrentUser } from "../current-user";
import { CurrentRouteService } from "../services/current-route.service";
import { HideNavService } from "../services/hide-nav.service";
import { HistoryBarService } from "../services/history-bar.service";
import { CollapseRepositoryTreeService } from "./desktop/collapse-repository-tree.service.component";
import { ReportTabModel } from "./report-tab-model";
import { ReportTabComponent } from "./report-tab.component";

type ReportTabPrivateApi = {
   init(): void;
   isEntryOpened(entry: RepositoryEntry): boolean;
   processMessageCommand(command: { message: string; type: string }): void;
   reloadUrl(url: string, navigationExtras: { queryParams?: Record<string, string> }): void;
};

type ReportTabCurrentRouteStub = {
   currentUrl$: BehaviorSubject<string>;
   repositoryEntry$: BehaviorSubject<RepositoryEntry | null>;
   repositoryUrl$: BehaviorSubject<string | null>;
   currentUrl: BehaviorSubject<string>;
   repositoryEntry: BehaviorSubject<RepositoryEntry | null>;
   repositoryUrl: BehaviorSubject<string | null>;
};

type ReportTabRouterStub = {
   events: Subject<unknown>;
   navigate: ReturnType<typeof vi.fn>;
};

type ReportTabNotificationsStub = {
   success: ReturnType<typeof vi.fn>;
   info: ReturnType<typeof vi.fn>;
   warning: ReturnType<typeof vi.fn>;
   danger: ReturnType<typeof vi.fn>;
};

type ReportTabMobileViewStub = {
   activePane: string;
};

type ReportTabViewsheetClientStub = {
   commands: Subject<unknown>;
   connect: ReturnType<typeof vi.fn>;
   sendEvent: ReturnType<typeof vi.fn>;
};

export function createReportTabModel(
   overrides: Partial<ReportTabModel> = {},
): ReportTabModel {
   return {
      expandAllNodes: false,
      showRepositoryAsList: false,
      searchEnabled: true,
      welcomePageUri: "",
      licensedComponentMsg: "",
      dragAndDrop: false,
      collapseTree: false,
      ...overrides,
   };
}

export function makeCurrentUser(overrides: Partial<CurrentUser> = {}): CurrentUser {
   return {
      anonymous: false,
      name: {
         name: "alice",
         orgID: null,
      },
      isSysAdmin: false,
      alias: "Alice",
      localeLanguage: "en",
      localeCountry: "US",
      ...overrides,
   };
}

export function makeReportEntry(overrides: Partial<RepositoryEntry> = {}): RepositoryEntry {
   return {
      name: "Sales",
      label: "Sales",
      alias: null,
      path: "Examples/Sales",
      identifier: "id-sales",
      type: RepositoryEntryType.VIEWSHEET,
      entry: {
         identifier: "vs-sales",
      },
      ...overrides,
   } as RepositoryEntry;
}

export function createReportTabRootNode(type: string = "Folder") {
   return {
      type,
      expanded: false,
      children: [
         {
            data: {
               path: "/",
            },
            expanded: false,
         },
      ],
   };
}

export function createReportTabCurrentRouteStub(): ReportTabCurrentRouteStub {
   const currentUrl$ = new BehaviorSubject<string>(null);
   const repositoryEntry$ = new BehaviorSubject<RepositoryEntry | null>(null);
   const repositoryUrl$ = new BehaviorSubject<string | null>(null);

   return {
      currentUrl$,
      repositoryEntry$,
      repositoryUrl$,
      currentUrl: currentUrl$,
      repositoryEntry: repositoryEntry$,
      repositoryUrl: repositoryUrl$,
   };
}

export function createReportTabViewsheetClientStub(): ReportTabViewsheetClientStub {
   return {
      commands: new Subject<unknown>(),
      connect: vi.fn(),
      sendEvent: vi.fn(),
   };
}

export function createReportTabComponent(options: {
   reportTabModel?: ReportTabModel;
   rootNode?: any;
   currentUser?: CurrentUser;
} = {}) {
   const currentRouteService = createReportTabCurrentRouteStub();
   const router: ReportTabRouterStub = {
      events: new Subject<unknown>(),
      navigate: vi.fn().mockResolvedValue(true),
   };
   const route = {
      data: new BehaviorSubject({
         reportTabModel: options.reportTabModel ?? createReportTabModel(),
      }),
   };
   const notifications$ = new Subject<{ type: string; content: string }>();
   const repositoryTreeService = {
      onNotification: notifications$,
      getRootFolder: vi.fn().mockReturnValue(new BehaviorSubject(options.rootNode ?? createReportTabRootNode())),
   };
   const modal = { open: vi.fn() };
   const hideNavService = {
      appendParameter: vi.fn().mockImplementation((params?: Record<string, string>) => ({
         ...params,
         hiddenNav: "true",
      })),
   };
   const changeRef = { detectChanges: vi.fn() };
   const historyBarService = { isHistoryBarEnabled: true };
   const viewsheetClient = createReportTabViewsheetClientStub();
   const composerService = {
      composerOpen: new BehaviorSubject<boolean>(false),
   };
   const collapseTreeService = {
      collapseTree: vi.fn(),
   };
   const assetLoadingService = {
      isLoading: vi.fn().mockReturnValue(false),
   };
   const aiAssistantService = {
      setContextTypeFieldValue: vi.fn(),
   };
   const zone = new NgZone({ enableLongStackTrace: false });

   TestBed.resetTestingModule();
   TestBed.configureTestingModule({
      providers: [
         provideHttpClient(),
         { provide: RepositoryTreeService, useValue: repositoryTreeService },
         { provide: DomSanitizer, useValue: {} },
         { provide: ActivatedRoute, useValue: route },
         { provide: Router, useValue: router },
         { provide: NgbModal, useValue: modal },
         { provide: HideNavService, useValue: hideNavService },
         { provide: ChangeDetectorRef, useValue: changeRef },
         { provide: HistoryBarService, useValue: historyBarService },
         { provide: CurrentRouteService, useValue: currentRouteService },
         { provide: ViewsheetClientService, useValue: viewsheetClient },
         { provide: OpenComposerService, useValue: composerService },
         { provide: CollapseRepositoryTreeService, useValue: collapseTreeService },
         { provide: AssetLoadingService, useValue: assetLoadingService },
         { provide: AiAssistantService, useValue: aiAssistantService },
      ],
   });

   const comp = new ReportTabComponent(
      TestBed.inject(RepositoryTreeService),
      TestBed.inject(DomSanitizer),
      TestBed.inject(ActivatedRoute),
      TestBed.inject(Router),
      TestBed.inject(HttpClient),
      TestBed.inject(NgbModal),
      TestBed.inject(HideNavService),
      TestBed.inject(ChangeDetectorRef),
      TestBed.inject(HistoryBarService),
      TestBed.inject(CurrentRouteService),
      TestBed.inject(ViewsheetClientService),
      zone,
      TestBed.inject(OpenComposerService),
      TestBed.inject(CollapseRepositoryTreeService),
      TestBed.inject(AssetLoadingService),
      TestBed.inject(AiAssistantService),
   );

   return {
      comp,
      currentRouteService,
      router,
      route,
      repositoryTreeService,
      notifications$,
      modal,
      hideNavService,
      changeRef,
      historyBarService,
      viewsheetClient,
      composerService,
      collapseTreeService,
      assetLoadingService,
      aiAssistantService,
      currentUser: options.currentUser ?? makeCurrentUser(),
   };
}

export function asReportTabPrivateApi(comp: ReportTabComponent): ReportTabPrivateApi {
   // TL coverage needs access to private navigation and command helpers without changing production visibility.
   return comp as unknown as ReportTabPrivateApi;
}

export function attachReportTabNotifications(comp: ReportTabComponent): ReportTabNotificationsStub {
   const notifications = {
      success: vi.fn(),
      info: vi.fn(),
      warning: vi.fn(),
      danger: vi.fn(),
   };

   // Tests inject the minimal @ViewChild surface directly because constructor subscriptions write into notifications.
   (comp as unknown as { notifications: ReportTabNotificationsStub }).notifications = notifications;
   return notifications;
}

export function attachReportTabMobileView(comp: ReportTabComponent): ReportTabMobileViewStub {
   const mobileView = { activePane: "Repository" };

   // Tests inject the minimal @ViewChild surface directly because route subscriptions flip the active pane.
   (comp as unknown as { mobileView: ReportTabMobileViewStub }).mobileView = mobileView;
   return mobileView;
}
