/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
import { AsyncPipe, NgClass, NgFor, NgIf, NgStyle } from "@angular/common";
import { HttpClientTestingModule } from "@angular/common/http/testing";
import {
   Component,
   EventEmitter,
   NgModule,
   NO_ERRORS_SCHEMA,
   Renderer2,
   ViewContainerRef
} from "@angular/core";
import { waitForAsync, ComponentFixture, TestBed } from "@angular/core/testing";
import { BrowserModule, By, Title } from "@angular/platform-browser";
import { Router } from "@angular/router";
import {
   NgbDatepickerConfig,
   NgbDropdownConfig,
   NgbModal,
   NgbModule,
   NgbTooltipConfig
} from "@ng-bootstrap/ng-bootstrap";
import { of as observableOf } from "rxjs";
import { DownloadService } from "../../../../shared/download/download.service";
import { AppInfoService } from "../../../../shared/util/app-info.service";
import { AssetLoadingService } from "../common/services/asset-loading.service";
import { BaseHrefService } from "../common/services/base-href.service";
import { HeartbeatWorkerService } from "../common/services/heartbeat-worker.service";
import { FirstDayOfWeekService } from "../common/services/first-day-of-week.service";
import { FullScreenService } from "../common/services/full-screen.service";
import { PagingControlService } from "../common/services/paging-control.service";
import { DropDownTestModule } from "../common/test/test-module";
import { TestUtils } from "../common/test/test-utils";
import { StompClientService, ViewsheetClientService } from "../common/viewsheet-client";
import { Viewsheet } from "../composer/data/vs/viewsheet";
import { ComposerRecentService } from "../composer/gui/composer-recent.service";
import { PageTabService } from "../viewer/services/page-tab.service";
import { ViewDataService } from "../viewer/services/view-data.service";
import { DomService } from "../widget/dom-service/dom.service";
import { ActionsContextmenuComponent } from "../widget/fixed-dropdown/actions-contextmenu.component";
import { InteractContainerDirective } from "../widget/interact/interact-container.directive";
import { DebounceService } from "../widget/services/debounce.service";
import { ModelService } from "../widget/services/model.service";
import { DefaultScaleService } from "../widget/services/scale/default-scale-service";
import { ScaleService } from "../widget/services/scale/scale-service";
import { ShareConfig } from "../widget/share/share-config";
import { DialogService } from "../widget/slide-out/dialog-service.service";
import { TooltipDirective } from "../widget/tooltip/tooltip.directive";
import { TooltipService } from "../widget/tooltip/tooltip.service";
import { AssemblyActionFactory } from "./action/assembly-action-factory.service";
import { ContextProvider } from "./context-provider.service";
import { RichTextService } from "./dialog/rich-text-dialog/rich-text.service";
import { ViewerToolbarButtonCommand } from "./iframe/viewer-toolbar-button-command";
import { ViewerToolbarEvent } from "./iframe/viewer-toolbar-event";
import { ViewerToolbarMessageService } from "./iframe/viewer-toolbar-message.service";
import { GuideBounds } from "./model/layout/guide-bounds";
import { VSBookmarkInfoModel } from "./model/vs-bookmark-info-model";
import { VSChartService } from "./objects/chart/services/vs-chart.service";
import { DataTipService } from "./objects/data-tip/data-tip.service";
import { PopComponentService } from "./objects/data-tip/pop-component.service";
import { TimerService } from "./objects/data-tip/timer.service";
import { VSPopComponentDirective } from "./objects/data-tip/vs-pop-component.directive";
import { MiniToolbarService } from "./objects/mini-toolbar/mini-toolbar.service";
import { SelectionMobileService } from "./objects/selection/services/selection-mobile.service";
import { ShowHyperlinkService } from "./show-hyperlink.service";
import { ToolbarActionsHandler } from "./toolbar-actions-handler";
import { CheckFormDataService } from "./util/check-form-data.service";
import { GlobalSubmitService } from "./util/global-submit.service";
import { ViewerResizeService } from "./util/viewer-resize.service";
import { ViewerAppComponent } from "./viewer-app.component";

vi.mock("css-element-queries");

@NgModule({
   imports: [
      BrowserModule
   ]
})
class TestModule {
}

@Component({
   selector: "notifications", // eslint-disable-line @angular-eslint/component-selector
   template: "<div></div>",
   standalone: true
})
class MockNotificationsComponent {
   info(message: string): void {
   }
}

describe("ViewerApp Unit Tests", () => {
   let modelService: any;
   let actionFactory: any;
   let stompClient: any;
   let viewsheet: Viewsheet;
   let downloadService: any;
   let viewsheetClientService: any;
   let chartService: any;
   let formDataService: any;
   let debounceService: any;
   let scaleService: any;
   let popService: any;
   let contextProvider: any;
   let dialogService: any;
   let tooltipService: any;
   let viewDataService: any;
   let fullScreenService: any;
   let router: any;
   let renderer: any;
   let sanitizer: any;
   let hyperlinkService: any;
   let titleService: any;
   let viewerResizeService: ViewerResizeService;
   let firstDayOfWeekService: any;
   let shareService: any;
   let mockDocument: any;
   let richTextService: any;
   let zone: any;
   let viewerToolbarMessageService: ViewerToolbarMessageService;
   let mobileToolbarService: any;
   let composerRecentService: any;
   let pageTabService: any;
   let pagingControlService: any;
   let selectionMobileService: any;
   let globalSubmitService: any;
   let miniToolbarService: any;
   let assetLoadingService: any;
   let viewContainerRef: any;
   let baseHrefService: any;
   let heartbeatWorkerService: any;
   let timerService: any;

   beforeEach(waitForAsync(() => {
      formDataService = {
         checkFormData: vi.fn(),
         removeObject: vi.fn(),
         addObject: vi.fn(),
         replaceObject: vi.fn(),
         resetCount: vi.fn()
      };
      modelService = { getModel: vi.fn() };
      modelService.getModel.mockImplementation(() => observableOf({}));
      actionFactory = { createActions: vi.fn() };
      actionFactory.createActions.mockImplementation(() => ({
         onAssemblyActionEvent: new EventEmitter<any>()
      }));
      debounceService = {
         debounce: vi.fn((key, fn, delay, args) => fn(...args)),
         cancel: vi.fn()
      };
      popService = { getPopComponent: vi.fn() };
      popService.getPopComponent.mockImplementation(() => "");
      contextProvider = {};

      stompClient = TestUtils.createMockStompClientService();
      zone = {
         run: fn => fn(),
         runOutsideAngular: fn => fn()
      };
      viewsheetClientService = new ViewsheetClientService(stompClient, zone);

      viewsheet = new Viewsheet();
      viewsheet.localId = 1;
      viewsheet.label = "";
      viewsheet.id = "Viewsheet1";
      viewsheet.socketConnection = <any> { sendEvent: vi.fn() };

      downloadService = { download: vi.fn() };
      chartService = { drill: vi.fn() };
      scaleService = { getScale: vi.fn(), setScale: vi.fn() };
      scaleService.getScale.mockImplementation(() => observableOf(1));
      dialogService = { open: vi.fn(), ngOnDestroy: vi.fn() };

      tooltipService = { createTooltip: vi.fn() };

      viewDataService = new ViewDataService();
      renderer = { listen: vi.fn() };

      fullScreenService = {
         enterFullScreen: vi.fn(),
         exitFullScreen: vi.fn()
      };
      fullScreenService.fullScreenMode = false;
      fullScreenService.fullScreenChange = observableOf({});

      hyperlinkService = { showURL: vi.fn() };
      hyperlinkService.drillDownSubject = observableOf({});
      let domService = {
         requestRead: vi.fn(),
         requestWrite: vi.fn()
      };

      titleService = { setTitle: vi.fn() };

      firstDayOfWeekService = { getFirstDay: vi.fn() };
      firstDayOfWeekService.getFirstDay.mockImplementation(() => observableOf({}));

      shareService = { getConfig: vi.fn() };
      mockDocument = {
         body: {
            classList: {
               add: vi.fn()
            }
         }
      };

      richTextService = {
         showAnnotationDialog: vi.fn()
      };

      viewerToolbarMessageService = new ViewerToolbarMessageService(zone);

      mobileToolbarService = {
         getShowingActions: vi.fn(),
         getMoreActions: vi.fn()
      };

      composerRecentService = {
         currentUser: null,
         recentlyViewedChange: vi.fn(),
         removeRecentlyViewed: vi.fn(),
         addRecentlyViewed: vi.fn(),
         removeNonExistItems: vi.fn()
      };

      pageTabService = {
         updateTabLabel: vi.fn(),
         getDrillTabsTop: vi.fn().mockReturnValue(observableOf(false)),
      };

      pagingControlService = {};
      globalSubmitService = {
         updateState: vi.fn(),
         globalSubmit: vi.fn(),
         updateSelections: vi.fn(),
         emitUpdateSelections: vi.fn
      };
      selectionMobileService = {
         hasAutoMaxMode: vi.fn(),
         resetSelectionMaxMode: vi.fn(),
         toggleSelectionMaxMode: vi.fn(),
         maxSelectionChanged: observableOf({})
      };
      miniToolbarService = {
         hideMiniToolbar: vi.fn(),
         isMiniToolbarHidden: vi.fn(),
         hiddenFreeze: vi.fn(),
         hiddenUnfreeze: vi.fn(),
         isMouseVisited: vi.fn(),
         mouseVisit: vi.fn(),
         resetMiniToolbarHidden: vi.fn()
      };
      assetLoadingService = {
         isLoading: vi.fn(),
         setLoading: vi.fn()
      };
      viewContainerRef = {
         element: vi.fn(),
      };
      baseHrefService = {
         getBaseHref: vi.fn(),
      };
      heartbeatWorkerService = {
         createHeartbeat: vi.fn().mockReturnValue({
            subscribe: vi.fn().mockReturnValue({ unsubscribe: vi.fn() })
         })
      };
      timerService = {
         defer: vi.fn((fn) => {
            fn();
         })
      };


      (window as any).IntersectionObserver = class IntersectionObserver {
         observe() {}
         unobserve() {}
         disconnect() {}
      };

      TestBed.configureTestingModule({
         imports: [
            NgbModule,
            TestModule,
            DropDownTestModule,
            HttpClientTestingModule,
            ViewerAppComponent,
            ActionsContextmenuComponent,
            InteractContainerDirective,
            VSPopComponentDirective,
            TooltipDirective,
            MockNotificationsComponent,
         ],
         providers: [
            {provide: StompClientService, useValue: stompClient},
            {provide: ModelService, useValue: modelService},
            {provide: DownloadService, useValue: downloadService},
            {provide: VSChartService, useValue: chartService},
            {provide: CheckFormDataService, useValue: formDataService},
            NgbModal,
            NgbDatepickerConfig,
            PopComponentService,
            NgbDropdownConfig,
            {provide: DebounceService, useValue: debounceService},
            {provide: ScaleService, useClass: DefaultScaleService},
            {provide: ContextProvider, useValue: contextProvider},
            {provide: DialogService, useValue: dialogService},
            { provide: TooltipService, useValue: tooltipService },
            { provide: ViewDataService, useValue: viewDataService },
            { provide: ShowHyperlinkService, useValue: hyperlinkService },
            { provide: Router, useValue: router },
            { provide: Renderer2, useValue: renderer },
            { provide: DomService, useValue: domService },
            { provide: Title, useValue: titleService },
            { provide: FirstDayOfWeekService, useValue: firstDayOfWeekService },
            ViewerResizeService,
            { provide: RichTextService, useValue: richTextService },
            { provide: ViewerToolbarMessageService, useValue: viewerToolbarMessageService },
            { provide: ToolbarActionsHandler, useValue: mobileToolbarService },
            { provide: ComposerRecentService, useValue: composerRecentService },
            { provide: PageTabService, useValue: pageTabService },
            { provide: PagingControlService, useValue: pagingControlService },
            { provide: GlobalSubmitService, useValue: globalSubmitService},
            { provide: SelectionMobileService, useValue: selectionMobileService},
            { provide: MiniToolbarService, useValue: miniToolbarService },
            { provide: AssetLoadingService, useValue: assetLoadingService },
            { provide: ViewContainerRef, useValue: viewContainerRef },
            { provide: BaseHrefService, useValue: baseHrefService },
            { provide: HeartbeatWorkerService, useValue: heartbeatWorkerService },
            AppInfoService,
            { provide: TimerService, useValue: timerService },
         ],
         
         schemas: [ NO_ERRORS_SCHEMA ]
      });
      TestBed.overrideComponent(ViewerAppComponent, {
         set: {
            imports: [NgIf, NgFor, NgClass, NgStyle, AsyncPipe],
            providers: [
               {provide: AssemblyActionFactory, useValue: actionFactory},
               {provide: ViewsheetClientService, useValue: viewsheetClientService},
               DataTipService,
               { provide: FullScreenService, useValue: fullScreenService }
            ]
         }
      });
      TestBed.compileComponents();
      viewerResizeService = TestBed.inject(ViewerResizeService);
   }));

   //Bug #16456 TODO, logica changed, can not get fixed dropdown pane
   // Bug #19176 hide full screen in preview
   it("should have disabled set as default and hidden full screen button in preview mode", waitForAsync(() => {
      const fixture: ComponentFixture<ViewerAppComponent> = TestBed.createComponent(ViewerAppComponent);
      fixture.componentInstance.touchDevice = false;
      fixture.componentInstance.preview = true;
      fixture.componentInstance.runtimeId = "id-123456";
      let vsBookmarkInfo: VSBookmarkInfoModel = {
         name: "foo",
         label: "bar"
      };
      fixture.componentInstance.vsBookmarkList = [vsBookmarkInfo];
      fixture.componentInstance.layoutName = null;
      fixture.componentInstance.id = null;
      fixture.componentInstance.assetId = "1^128^__NULL__^TEST";
      fixture.componentInstance.queryParameters = new Map<string, string[]>();
      fixture.componentInstance.toolbarPermissions = ["Full Screen"];

      fixture.detectChanges();
      let fullScreenBtn = fixture.nativeElement.querySelector("button.viewer-full-screen-button");
      expect(fullScreenBtn).toBeFalsy();
   }));

   // Removed: processRemoveVSObjectCommand coverage migrated to ATL pass 2.
   //   viewer-app.component.risk.tl.spec.ts — Group 8 "processRemoveVSObjectCommand()"
   //   covers: removes from vsObjects + vsObjectActions in sync, name not found,
   //   selectedActions cleared when removed assembly was selected, emit onLoadingStateChanged.

   // Bug #16961 should refresh scale to screen vs when viewer root pane size changes
   // @by jasonshobe, this test case is failing, but it is bad (testing implementation instead of
   // behavior) so I'm disabling it.
   it.skip("should refresh viewsheet on viewer root resize", waitForAsync(() => {
      const fixture: ComponentFixture<ViewerAppComponent> = TestBed.createComponent(ViewerAppComponent);
      fixture.componentInstance.toolbarVisible = true;
      fixture.componentInstance.preview = true;
      fixture.componentInstance.runtimeId = "Foobar";
      fixture.componentInstance["scaleToScreen"] = true;
      fixture.componentInstance.viewerRoot.nativeElement.style.height = "100px";
      fixture.componentInstance.active = true;
      fixture.detectChanges();

      const onViewerRootResize = vi.spyOn(fixture.componentInstance, "onViewerRootResize");
      const refreshViewsheet = vi.spyOn(fixture.componentInstance, "refreshViewsheet");
      fixture.componentInstance.viewerRoot.nativeElement.style.height = "200px";
      const sendEvent = vi.spyOn(viewsheetClientService, "sendEvent");
      sendEvent.mockImplementation(() => {});

      fixture.detectChanges();

      fixture.whenStable().then(() => {
         expect(sendEvent).toHaveBeenCalled();
         const arg: any = sendEvent.mock.calls[0][1];
         expect(arg.height).toBe(200);
         expect(onViewerRootResize).toHaveBeenCalled();
         expect(refreshViewsheet).toHaveBeenCalled();
      });
   }));

   it("should hide toolbar actions when permissions are set", () => {
      const fixture = TestBed.createComponent(ViewerAppComponent);
      const permissions = [
         "Undo"
      ];

      fixture.componentInstance.toolbarPermissions = permissions;
      fixture.detectChanges();

      const toolbar = fixture.debugElement.query(By.css(".viewer-toolbar"));
      const toolbarButtons = toolbar.queryAll(By.css("button:not([hidden])"));
      const visibleButtonCount = toolbarButtons.length;

      // remove permission
      fixture.componentInstance.toolbarPermissions = [];
      fixture.detectChanges();
      const newVisibleButtonCount = toolbar.queryAll(By.css("button:not([hidden])")).length;
      expect(newVisibleButtonCount).toBe(visibleButtonCount + 1);
   });

   it.skip("should open preview layout with scaled size", () => { // broken test
      const fixture: ComponentFixture<ViewerAppComponent> = TestBed.createComponent(ViewerAppComponent);
      fixture.componentInstance.preview = true;
      fixture.componentInstance.runtimeId = "Foobar";
      fixture.componentInstance.layoutName = "Foolayout";
      fixture.componentInstance.guideType = GuideBounds.GUIDES_16_9_PORTRAIT;
      fixture.componentInstance.viewerRoot.nativeElement.style.height = "100px";
      fixture.componentInstance.viewerRoot.nativeElement.style.width = "100px";
      fixture.componentInstance["scaleToScreen"] = true;
      fixture.componentInstance.active = true;

      const sendEvent = vi.spyOn(viewsheetClientService, "sendEvent");
      sendEvent.mockImplementation(() => {});

      fixture.detectChanges();

      expect(sendEvent).toHaveBeenCalled();
      const arg: any = sendEvent.mock.calls[0][1];
      expect(arg.height).toBe(100);
      expect(parseInt(arg.height, 10)).toBe(56);
   });

   // Bug #20628 Bug #20715 should display correct status for previous page
   it("should display correct status for previous page", waitForAsync(() => {
      const fixture: ComponentFixture<ViewerAppComponent> =
         TestBed.createComponent(ViewerAppComponent);
      fixture.componentInstance.toolbarPermissions = [];
      fixture.componentInstance.toolbarVisible = true;
      fixture.componentInstance.preview = true;
      fixture.componentInstance.runtimeId = "Foobar";
      fixture.componentInstance.active = true;
      fixture.detectChanges();

      let previousBtn = fixture.nativeElement.querySelector("button.viewer-toolbar-btn.viewer-previous-button i");
      expect(previousBtn.classList).toContain("icon-disabled");

      fixture.componentInstance.undoEnabled = true;
      fixture.detectChanges();

      previousBtn = fixture.nativeElement.querySelector("button.viewer-toolbar-btn.viewer-previous-button i");
      expect(previousBtn.classList).not.toContain("icon-disabled");
   }));

   // Removed: setServerUpdateInterval / clearServerUpdateInterval coverage migrated to ATL pass 2.
   //   viewer-app.component.risk.tl.spec.ts — Group 10 "setServerUpdateInterval() / clearServerUpdateInterval()"
   //   covers: createHeartbeat called with correct key + interval, TOUCH_ASSET event on tick,
   //   unsubscribe called on clear, touchInterval (seconds) scaling.

   // Removed (Bug #21287): closeViewsheet confirm-dialog coverage migrated to ATL pass 2.
   //   viewer-app.component.risk.tl.spec.ts — Group 5 "closeViewsheet() non-inTabs path"
   //   covers: confirm dialog when form tables exist, close on ok, no-close on cancel,
   //   direct close when checkFormTables returns false.

   it("should send and receive viewer toolbar window messages", () => new Promise<void>((done) => {
      window["globalPostParams"] = null; // to resolve global var
      modelService.getModel.mockImplementation(() => observableOf([]));

      const fixture: ComponentFixture<ViewerAppComponent> =
         TestBed.createComponent(ViewerAppComponent);
      const viewer = fixture.componentInstance;
      viewer.runtimeId = "id";
      viewer.isIframe = true;
      viewer.shareConfig = <ShareConfig> {};
      let messageCommand: ViewerToolbarButtonCommand = null;

      window.addEventListener("message", messageFromService => {
         // don't trigger on command sent to service.
         if(messageCommand != null) {
            setTimeout(() => {
               expect(viewer.editViewsheet).toHaveBeenCalled();
               done();
            }, 10);

            return;
         }

         const event: ViewerToolbarEvent = messageFromService.data;

         messageCommand = {
            identifier: event.identifier,
            name: "_#(js:Edit)"
         };

         // change identifier so event is rejected by the service w/o error.
         messageFromService.data.identifier = null;

         viewer.editViewsheet = vi.fn();
         window.postMessage(messageCommand, "*");
      });

      fixture.detectChanges();
   }));
});
