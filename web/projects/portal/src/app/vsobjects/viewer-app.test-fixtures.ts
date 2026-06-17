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

// ---------------------------------------------------------------------------
// Module-level mocks — hoisted by Vitest before any imports are evaluated.
// ---------------------------------------------------------------------------

vi.mock("css-element-queries", () => ({
   ResizeSensor: class {
      constructor(_el: any, _cb: any) {}
      detach() {}
   },
}));

// IntersectionObserver is used in ngAfterViewInit to watch the viewerRoot element.
vi.stubGlobal("IntersectionObserver", class {
   observe = vi.fn();
   unobserve = vi.fn();
   disconnect = vi.fn();
   constructor(_cb: any, _opts?: any) {}
});

// globalPostParams is injected by the server into index.html before Angular loads.
// In tests this global doesn't exist, causing a ReferenceError in openViewsheet0().
vi.stubGlobal("globalPostParams", null);

import { Component, Directive, EventEmitter, Input, NO_ERRORS_SCHEMA, Output } from "@angular/core";
import { provideHttpClient } from "@angular/common/http";
import { render } from "@testing-library/angular";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { NEVER, of, Subject } from "rxjs";

import { MessageDialog } from "../widget/dialog/message-dialog/message-dialog.component";
import { ViewerAppComponent } from "./viewer-app.component";
import { ViewsheetClientService } from "../common/viewsheet-client/viewsheet-client.service";
import { StompClientService } from "../common/viewsheet-client";
import { VSObjectContainer } from "./objects/vs-object-container.component";
import { InteractContainerDirective } from "../widget/interact/interact-container.directive";
import { DataTipService } from "./objects/data-tip/data-tip.service";
import { AdhocFilterService } from "./objects/data-tip/adhoc-filter.service";
import { PopComponentService } from "./objects/data-tip/pop-component.service";
import { VSChartService } from "./objects/chart/services/vs-chart.service";
import { AssemblyActionFactory } from "./action/assembly-action-factory.service";
import { SelectionContainerChildrenService } from "./objects/selection/services/selection-container-children.service";
import { CheckFormDataService } from "./util/check-form-data.service";
import { DebounceService } from "../widget/services/debounce.service";
import { FullScreenService } from "../common/services/full-screen.service";
import { ViewerResizeService } from "./util/viewer-resize.service";
import { FormInputService } from "./util/form-input.service";
import { GlobalSubmitService } from "./util/global-submit.service";
import { ViewerToolbarMessageService } from "./iframe/viewer-toolbar-message.service";
import { DndService } from "../common/dnd/dnd.service";
import { ScaleService } from "../widget/services/scale/scale-service";
import { ContextProvider } from "./context-provider.service";
import { DialogService } from "../widget/slide-out/dialog-service.service";
import { ChartService } from "../graph/services/chart.service";
import { ComposerRecentService } from "../composer/gui/composer-recent.service";
import { SelectionMobileService } from "./objects/selection/services/selection-mobile.service";
import { MiniToolbarService } from "./objects/mini-toolbar/mini-toolbar.service";
import { RichTextService } from "./dialog/rich-text-dialog/rich-text.service";
import { VSTabService } from "./util/vs-tab.service";
import { ViewDataService } from "../viewer/services/view-data.service";
import { FirstDayOfWeekService } from "../common/services/first-day-of-week.service";
import { ModelService } from "../widget/services/model.service";
import { FixedDropdownService } from "../widget/fixed-dropdown/fixed-dropdown.service";
import { DownloadService } from "../../../../shared/download/download.service";
import { Router } from "@angular/router";
import { Title } from "@angular/platform-browser";
import { ShowHyperlinkService } from "./show-hyperlink.service";
import { ShareService } from "../widget/share/share.service";
import { PageTabService } from "../viewer/services/page-tab.service";
import { PagingControlService } from "../common/services/paging-control.service";
import { AssetLoadingService } from "../common/services/asset-loading.service";
import { BaseHrefService } from "../common/services/base-href.service";
import { CurrentUserService } from "../../../../shared/util/current-user.service";
import { HeartbeatWorkerService } from "../common/services/heartbeat-worker.service";
import { KeepAwakeService } from "../common/services/keep-awake.service";
import { OpenComposerService } from "../common/services/open-composer.service";

// ---------------------------------------------------------------------------
// Child component / directive stubs
// VSObjectContainer is always in the DOM (not behind *ngIf), so Angular
// instantiates it immediately.  The real component has a large DI tree; the
// stub cuts that without affecting any host-component logic under test.
// InteractContainerDirective uses interactjs which is unreliable in jsdom.
// ---------------------------------------------------------------------------

@Component({ selector: "vs-object-container", template: "", standalone: true })
export class VSObjectContainerStub {
   @Input() touchDevice: boolean = false;
   @Input() vsInfo: any;
   @Input() containerRef: any;
   @Input() vsObjectActions: any[] = [];
   @Input() selectedAssemblies: any;
   @Input() scaleToScreen: any;
   @Input() appSize: any;
   @Input() allAssemblyBounds: any;
   @Input() submitted: any;
   @Input() keyNavigation: any;
   @Input() focusedObject: any;
   @Input() guideType: any;
   @Input() hideMiniToolbar: boolean = false;
   @Input() globalLoadingIndicator: boolean = false;
   @Input() viewsheetLoading: boolean = false;
   @Input() virtualScrolling: boolean = false;
   @Input() scrollViewport: any;
   @Output() openContextMenu = new EventEmitter<any>();
   @Output() onEditChart = new EventEmitter<any>();
   @Output() onEditTable = new EventEmitter<any>();
   @Output() onOpenChartFormatPane = new EventEmitter<any>();
   @Output() onLoadFormatModel = new EventEmitter<any>();
   @Output() onOpenConditionDialog = new EventEmitter<any>();
   @Output() onOpenHighlightDialog = new EventEmitter<any>();
   @Output() onOpenAnnotationDialog = new EventEmitter<any>();
   @Output() onOpenViewsheet = new EventEmitter<any>();
   @Output() onSelectedAssemblyChanged = new EventEmitter<any>();
   @Output() removeAnnotations = new EventEmitter<any>();
   @Output() maxModeChange = new EventEmitter<any>();
   @Output() onToggleDoubleCalendar = new EventEmitter<any>();
   @Output() onSubmit = new EventEmitter<any>();
   @Output() onLoadingStateChanged = new EventEmitter<any>();
}

@Directive({ selector: "[wInteractContainer]", standalone: true })
export class InteractContainerStub {
   @Input() composited: boolean = true;
   @Input() draggableElementRect: any;
   @Input() draggableRestriction: any;
   @Input() snapToGrid: boolean = false;
   @Input() snapGridSize: number = 10;
   @Input() snapToGuides: boolean = false;
   @Input() snapHorizontalGuides: any;
   @Input() snapVerticalGuides: any;
   @Input() snapGuideRange: any;
   @Input() snapGuideOffset: number = 0;
   @Output() onSnap = new EventEmitter<any>();
}

// ---------------------------------------------------------------------------
// Shared subjects (file-level — not re-created per test so ngOnInit
// subscriptions formed at render time survive into the test body)
// ---------------------------------------------------------------------------

export const commandsSubject = new Subject<any>();
export const onHeartbeatSubject = new Subject<void>();
export const onRenameTransformFinishedSubject = new Subject<any>();
export const onTransformFinishedSubject = new Subject<any>();
export const drillDownSubject = new Subject<void>();
export const fullScreenChangeSubject = new Subject<void>();

// ---------------------------------------------------------------------------
// Shared mocks (file-level — reset in each spec file's beforeEach via
// resetMocks())
// ---------------------------------------------------------------------------

export const VS_CLIENT_MOCK: any = {
   commands: commandsSubject,
   onHeartbeat: onHeartbeatSubject,
   onRenameTransformFinished: onRenameTransformFinishedSubject,
   onTransformFinished: onTransformFinishedSubject,
   connect: vi.fn(),
   sendEvent: vi.fn(),
   runtimeId: null as string | null,
   beforeDestroy: null as (() => void) | null,
   connectionError: vi.fn().mockReturnValue(NEVER),
};

export const STOMP_CLIENT_MOCK: any = {
   reloadOnFailure: false,
};

export const MODAL_MOCK = {
   open: vi.fn().mockImplementation(() => ({
      result: new Promise<any>(() => {}),
      // onCommit is required by ComponentTool.showMessageDialog / showConfirmDialog
      componentInstance: { onCommit: new Subject<string>() },
      close: vi.fn(),
      dismiss: vi.fn(),
   })),
};

export const ASSEMBLY_ACTION_FACTORY_MOCK: any = {
   securityEnabled: false,
   stateProvider: null,
   createActions: vi.fn().mockReturnValue({}),
};

export const SCALE_SERVICE_MOCK = {
   setScale: vi.fn(),
   getScale: vi.fn().mockReturnValue(1),
};

export const SELECTION_MOBILE_SERVICE_MOCK = {
   hasAutoMaxMode: vi.fn().mockReturnValue(false),
   resetSelectionMaxMode: vi.fn(),
};

export const DIALOG_SERVICE_MOCK: any = {
   ngOnDestroy: vi.fn(),
   container: null as any,
   // result is a Promise — addBookmark uses dialogService.open(...).result.then(...)
   open: vi.fn().mockReturnValue({ closed: of(null), close: vi.fn(), result: new Promise<any>(() => {}) }),
};

export const MODEL_SERVICE_MOCK = {
   getModel: vi.fn().mockReturnValue(of([])),
   putModel: vi.fn().mockReturnValue(of({})),
};

export const HYPERLINK_SERVICE_MOCK: any = {
   drillDownSubject,
   portalRepositoryPermission: false,
   singleClick: false,
};

export const PAGE_TAB_SERVICE_MOCK = {
   getDrillTabsTop: vi.fn().mockReturnValue(of(false)),
   updateTabLabel: vi.fn(),
};

export const CURRENT_USER_SERVICE_MOCK = {
   getPortalCurrentUser: vi.fn().mockReturnValue(
      of({ name: { name: "admin", orgID: "host_org" } })
   ),
};

export const SHARE_SERVICE_MOCK = {
   getConfig: vi.fn().mockReturnValue(
      of({ email: false, slack: false, googleChat: false, link: false })
   ),
};

export const FIRST_DAY_OF_WEEK_SERVICE_MOCK = {
   getFirstDay: vi.fn().mockReturnValue(of({ isoFirstDay: 1 })),
};

export const HEARTBEAT_WORKER_SERVICE_MOCK = {
   createHeartbeat: vi.fn().mockReturnValue(NEVER),
   removeHeartbeat: vi.fn(),
};

export const KEEP_AWAKE_SERVICE_MOCK = {
   keepAwake: vi.fn(),
   release: vi.fn(),
};

export const CHECK_FORM_DATA_SERVICE_MOCK = {
   checkFormData: vi.fn().mockReturnValue(of(0)),
   removeObject: vi.fn(),
   addObject: vi.fn(),
   replaceObject: vi.fn(),
};

export const PAGING_CONTROL_SERVICE_MOCK = {
   setScrollTop: vi.fn(),
   setScrollLeft: vi.fn(),
   scrollLeftChange: vi.fn(),
};

export const FULL_SCREEN_SERVICE_MOCK: any = {
   fullScreenChange: fullScreenChangeSubject,
   fullScreenMode: false,
   enterFullScreen: vi.fn(),
   enterFullScreenForElement: vi.fn(),
   exitFullScreen: vi.fn(),
};

export const ASSET_LOADING_SERVICE_MOCK = {
   setLoading: vi.fn(),
};

// ---------------------------------------------------------------------------
// Shared reset — call in each spec file's top-level beforeEach
// ---------------------------------------------------------------------------

export function resetMocks(): void {
   VS_CLIENT_MOCK.sendEvent.mockClear();
   VS_CLIENT_MOCK.connect.mockClear();
   VS_CLIENT_MOCK.connectionError.mockClear().mockReturnValue(NEVER);
   VS_CLIENT_MOCK.runtimeId = null;
   VS_CLIENT_MOCK.beforeDestroy = null;

   STOMP_CLIENT_MOCK.reloadOnFailure = false;

   MODAL_MOCK.open.mockClear().mockImplementation(() => ({
      result: new Promise<any>(() => {}),
      componentInstance: { onCommit: new Subject<string>() },
      close: vi.fn(),
      dismiss: vi.fn(),
   }));

   ASSEMBLY_ACTION_FACTORY_MOCK.createActions.mockClear().mockReturnValue({});

   SCALE_SERVICE_MOCK.setScale.mockClear();
   SCALE_SERVICE_MOCK.getScale.mockClear().mockReturnValue(1);

   SELECTION_MOBILE_SERVICE_MOCK.hasAutoMaxMode.mockClear().mockReturnValue(false);
   SELECTION_MOBILE_SERVICE_MOCK.resetSelectionMaxMode.mockClear();

   DIALOG_SERVICE_MOCK.ngOnDestroy.mockClear();
   DIALOG_SERVICE_MOCK.open.mockClear().mockReturnValue({ closed: of(null), close: vi.fn(), result: new Promise<any>(() => {}) });
   DIALOG_SERVICE_MOCK.container = null;

   MODEL_SERVICE_MOCK.getModel.mockClear().mockReturnValue(of([]));
   MODEL_SERVICE_MOCK.putModel.mockClear().mockReturnValue(of({}));

   HYPERLINK_SERVICE_MOCK.singleClick = false;
   HYPERLINK_SERVICE_MOCK.portalRepositoryPermission = false;

   PAGE_TAB_SERVICE_MOCK.getDrillTabsTop.mockClear().mockReturnValue(of(false));
   PAGE_TAB_SERVICE_MOCK.updateTabLabel.mockClear();

   CURRENT_USER_SERVICE_MOCK.getPortalCurrentUser.mockClear().mockReturnValue(
      of({ name: { name: "admin", orgID: "host_org" } })
   );

   SHARE_SERVICE_MOCK.getConfig.mockClear().mockReturnValue(
      of({ email: false, slack: false, googleChat: false, link: false })
   );

   FIRST_DAY_OF_WEEK_SERVICE_MOCK.getFirstDay.mockClear().mockReturnValue(of({ isoFirstDay: 1 }));

   HEARTBEAT_WORKER_SERVICE_MOCK.createHeartbeat.mockClear().mockReturnValue(NEVER);
   HEARTBEAT_WORKER_SERVICE_MOCK.removeHeartbeat.mockClear();

   CHECK_FORM_DATA_SERVICE_MOCK.checkFormData.mockClear().mockReturnValue(of(0));
   CHECK_FORM_DATA_SERVICE_MOCK.removeObject.mockClear();
   CHECK_FORM_DATA_SERVICE_MOCK.addObject.mockClear();
   CHECK_FORM_DATA_SERVICE_MOCK.replaceObject.mockClear();

   PAGING_CONTROL_SERVICE_MOCK.setScrollTop.mockClear();
   PAGING_CONTROL_SERVICE_MOCK.setScrollLeft.mockClear();
   PAGING_CONTROL_SERVICE_MOCK.scrollLeftChange.mockClear();

   FULL_SCREEN_SERVICE_MOCK.fullScreenMode = false;
   FULL_SCREEN_SERVICE_MOCK.enterFullScreen.mockClear();
   FULL_SCREEN_SERVICE_MOCK.enterFullScreenForElement.mockClear();
   FULL_SCREEN_SERVICE_MOCK.exitFullScreen.mockClear();

   ASSET_LOADING_SERVICE_MOCK.setLoading.mockClear();

   KEEP_AWAKE_SERVICE_MOCK.keepAwake.mockClear();
   KEEP_AWAKE_SERVICE_MOCK.release.mockClear();

   MessageDialog.lastMessage = null;
   MessageDialog.lastMessageTS = 0;
}

// ---------------------------------------------------------------------------
// Default test inputs
// ---------------------------------------------------------------------------

/** assetId whose organization field matches the default currOrgID ("host_org") */
export const DEFAULT_ASSET_ID = "128^4096^__NULL__^TestVS^host_org";

/** assetId whose organization field differs from the default currOrgID */
export const CROSS_ORG_ASSET_ID = "128^4096^__NULL__^TestVS^other_org";

// ---------------------------------------------------------------------------
// renderComponent helper
// ---------------------------------------------------------------------------

export interface RenderOptions {
   assetId?: string | null;
   toolbarPermissions?: string[];
   tabsHeight?: number;
   preview?: boolean;
}

export interface RenderResult {
   comp: ViewerAppComponent;
   fixture: any;
}

export async function renderComponent(opts: RenderOptions = {}): Promise<RenderResult> {
   const {
      assetId = DEFAULT_ASSET_ID,
      toolbarPermissions = [],
      tabsHeight = 0,
      preview = false,
   } = opts;

   const { fixture } = await render(ViewerAppComponent, {
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
         provideHttpClient(),
         { provide: StompClientService, useValue: STOMP_CLIENT_MOCK },
         { provide: ModelService, useValue: MODEL_SERVICE_MOCK },
         { provide: NgbModal, useValue: MODAL_MOCK },
         { provide: FixedDropdownService, useValue: { open: vi.fn().mockReturnValue({ close: vi.fn() }) } },
         { provide: DownloadService, useValue: { download: vi.fn() } },
         { provide: Router, useValue: { navigate: vi.fn(), events: NEVER } },
         { provide: Title, useValue: { setTitle: vi.fn() } },
         { provide: ShowHyperlinkService, useValue: HYPERLINK_SERVICE_MOCK },
         { provide: ShareService, useValue: SHARE_SERVICE_MOCK },
         { provide: PageTabService, useValue: PAGE_TAB_SERVICE_MOCK },
         { provide: PagingControlService, useValue: PAGING_CONTROL_SERVICE_MOCK },
         { provide: AssetLoadingService, useValue: ASSET_LOADING_SERVICE_MOCK },
         { provide: BaseHrefService, useValue: { getBaseHref: vi.fn().mockReturnValue("/") } },
         { provide: CurrentUserService, useValue: CURRENT_USER_SERVICE_MOCK },
         { provide: HeartbeatWorkerService, useValue: HEARTBEAT_WORKER_SERVICE_MOCK },
         { provide: KeepAwakeService, useValue: KEEP_AWAKE_SERVICE_MOCK },
         { provide: OpenComposerService, useValue: { composerOpen: NEVER, openComposerWindow: vi.fn() } },
      ],
      importOverrides: [
         { replace: VSObjectContainer, with: VSObjectContainerStub },
         { replace: InteractContainerDirective, with: InteractContainerStub },
      ],
      componentProviders: [
         { provide: ViewsheetClientService, useValue: VS_CLIENT_MOCK },
         { provide: DataTipService, useValue: { viewerOffsetFunc: null } },
         { provide: AdhocFilterService, useValue: {} },
         { provide: PopComponentService, useValue: { viewerOffsetFunc: null, getComponentModelFunc: null } },
         { provide: VSChartService, useValue: {} },
         { provide: AssemblyActionFactory, useValue: ASSEMBLY_ACTION_FACTORY_MOCK },
         { provide: SelectionContainerChildrenService, useValue: {} },
         { provide: CheckFormDataService, useValue: CHECK_FORM_DATA_SERVICE_MOCK },
         { provide: DebounceService, useValue: { debounce: vi.fn(), cancel: vi.fn() } },
         { provide: FullScreenService, useValue: FULL_SCREEN_SERVICE_MOCK },
         { provide: ViewerResizeService, useValue: {} },
         { provide: FormInputService, useValue: {} },
         { provide: GlobalSubmitService, useValue: {} },
         { provide: ViewerToolbarMessageService, useValue: { refreshButtonDefinitions: vi.fn() } },
         { provide: DndService, useValue: {} },
         { provide: ScaleService, useValue: SCALE_SERVICE_MOCK },
         { provide: ContextProvider, useValue: { embed: false, viewer: true, preview: false } },
         { provide: DialogService, useValue: DIALOG_SERVICE_MOCK },
         { provide: ChartService, useValue: {} },
         { provide: ComposerRecentService, useValue: { addRecentlyViewed: vi.fn(), currentUser: null } },
         { provide: SelectionMobileService, useValue: SELECTION_MOBILE_SERVICE_MOCK },
         { provide: MiniToolbarService, useValue: { freeze: vi.fn(), unfreeze: vi.fn() } },
         { provide: RichTextService, useValue: {} },
         { provide: VSTabService, useValue: {} },
         { provide: ViewDataService, useValue: { data: { scaleToScreen: false, fitToWidth: false } } },
         { provide: FirstDayOfWeekService, useValue: FIRST_DAY_OF_WEEK_SERVICE_MOCK },
      ],
      componentInputs: {
         assetId,
         toolbarPermissions,
         tabsHeight,
         preview,
      },
   });

   return { comp: fixture.componentInstance as ViewerAppComponent, fixture };
}
