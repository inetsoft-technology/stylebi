/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import { HttpClientTestingModule } from "@angular/common/http/testing";
import { Component, EventEmitter, NgModule, NO_ERRORS_SCHEMA, Renderer2 } from "@angular/core";
import { async, ComponentFixture, TestBed } from "@angular/core/testing";
import { BrowserModule, By, Title } from "@angular/platform-browser";
import { Router } from "@angular/router";
import {
   NgbConfig,
   NgbDatepickerConfig,
   NgbDropdownConfig,
   NgbModal,
   NgbModule,
   NgbTooltipConfig
} from "@ng-bootstrap/ng-bootstrap";
import { of as observableOf } from "rxjs";
import { DownloadService } from "../../../../shared/download/download.service";
import { AssetLoadingService } from "../common/services/asset-loading.service";
import { FirstDayOfWeekService } from "../common/services/first-day-of-week.service";
import { FullScreenService } from "../common/services/full-screen.service";
import { DropDownTestModule } from "../common/test/test-module";
import { TestUtils } from "../common/test/test-utils";
import { ComponentTool } from "../common/util/component-tool";
import { StompClientService, ViewsheetClientService } from "../common/viewsheet-client";
import { Viewsheet } from "../composer/data/vs/viewsheet";
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
import { ChartActions } from "./action/chart-actions";
import { CrosstabActions } from "./action/crosstab-actions";
import { TableActions } from "./action/table-actions";
import { RemoveVSObjectCommand } from "./command/remove-vs-object-command";
import { ContextProvider, ViewerContextProviderFactory } from "./context-provider.service";
import { RichTextService } from "./dialog/rich-text-dialog/rich-text.service";
import { ViewerToolbarButtonCommand } from "./iframe/viewer-toolbar-button-command";
import { ViewerToolbarEvent } from "./iframe/viewer-toolbar-event";
import { ViewerToolbarMessageService } from "./iframe/viewer-toolbar-message.service";
import { GuideBounds } from "./model/layout/guide-bounds";
import { VSBookmarkInfoModel } from "./model/vs-bookmark-info-model";
import { VSChartService } from "./objects/chart/services/vs-chart.service";
import { DataTipService } from "./objects/data-tip/data-tip.service";
import { PopComponentService } from "./objects/data-tip/pop-component.service";
import { VSPopComponentDirective } from "./objects/data-tip/vs-pop-component.directive";
import { ShowHyperlinkService } from "./show-hyperlink.service";
import { CheckFormDataService } from "./util/check-form-data.service";
import { ViewerResizeService } from "./util/viewer-resize.service";
import { ViewerAppComponent } from "./viewer-app.component";
import { ResizeSensor } from "css-element-queries";
import { ToolbarActionsHandler } from "./toolbar-actions-handler";
import { ComposerRecentService } from "../composer/gui/composer-recent.service";
import { PagingControlService } from "../common/services/paging-control.service";
import { SelectionMobileService } from "./objects/selection/services/selection-mobile.service";
import { GlobalSubmitService } from "./util/global-submit.service";
import { MiniToolbarService } from "./objects/mini-toolbar/mini-toolbar.service";
import { FeatureFlagsService } from "../../../../shared/feature-flags/feature-flags.service";

jest.mock("css-element-queries");

@NgModule({
   imports: [
      BrowserModule
   ]
})
class TestModule {
}

@Component({
   selector: "notifications", // eslint-disable-line @angular-eslint/component-selector
   template: "<div></div>"
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
   let featureFlagsService: any;
   let assetLoadingService: any;

   beforeEach(async(() => {
      formDataService = {
         checkFormData: jest.fn(),
         removeObject: jest.fn(),
         addObject: jest.fn(),
         replaceObject: jest.fn(),
         resetCount: jest.fn()
      };
      modelService = { getModel: jest.fn() };
      modelService.getModel.mockImplementation(() => observableOf({}));
      actionFactory = { createActions: jest.fn() };
      actionFactory.createActions.mockImplementation(() => ({
         onAssemblyActionEvent: new EventEmitter<any>()
      }));
      debounceService = {
         debounce: jest.fn((key, fn, delay, args) => fn(...args)),
         cancel: jest.fn()
      };
      popService = { getPopComponent: jest.fn() };
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
      viewsheet.socketConnection = <any> { sendEvent: jest.fn() };

      downloadService = { download: jest.fn() };
      chartService = { drill: jest.fn() };
      scaleService = { getScale: jest.fn(), setScale: jest.fn() };
      scaleService.getScale.mockImplementation(() => observableOf(1));
      dialogService = { open: jest.fn(), ngOnDestroy: jest.fn() };

      tooltipService = { createTooltip: jest.fn() };

      viewDataService = new ViewDataService();
      renderer = { listen: jest.fn() };

      fullScreenService = {
         enterFullScreen: jest.fn(),
         exitFullScreen: jest.fn()
      };
      fullScreenService.fullScreenMode = false;
      fullScreenService.fullScreenChange = observableOf({});

      hyperlinkService = { showURL: jest.fn() };
      hyperlinkService.drillDownSubject = observableOf({});
      let domService = {
         requestRead: jest.fn(),
         requestWrite: jest.fn()
      };

      titleService = { setTitle: jest.fn() };

      firstDayOfWeekService = { getFirstDay: jest.fn() };
      firstDayOfWeekService.getFirstDay.mockImplementation(() => observableOf({}));

      shareService = { getConfig: jest.fn() };
      mockDocument = {
         body: {
            classList: {
               add: jest.fn()
            }
         }
      };

      richTextService = {
         showAnnotationDialog: jest.fn()
      };

      viewerToolbarMessageService = new ViewerToolbarMessageService(zone);

      mobileToolbarService = {
         getShowingActions: jest.fn(),
         getMoreActions: jest.fn()
      };

      composerRecentService = {
         currentUser: null,
         recentlyViewedChange: jest.fn(),
         removeRecentlyViewed: jest.fn(),
         addRecentlyViewed: jest.fn(),
         removeNonExistItems: jest.fn()
      };

      pageTabService = {
         updateTabLabel: jest.fn(),
      };

      pagingControlService = {};
      globalSubmitService = {
         updateState: jest.fn(),
         globalSubmit: jest.fn(),
         updateSelections: jest.fn(),
         emitUpdateSelections: jest.fn
      };
      selectionMobileService = {
         hasAutoMaxMode: jest.fn(),
         resetSelectionMaxMode: jest.fn(),
         toggleSelectionMaxMode: jest.fn(),
         maxSelectionChanged: observableOf({})
      };
      miniToolbarService = {
         hideMiniToolbar: jest.fn(),
         isMiniToolbarHidden: jest.fn(),
         hiddenFreeze: jest.fn(),
         hiddenUnfreeze: jest.fn(),
         isMouseVisited: jest.fn(),
         mouseVisit: jest.fn(),
         resetMiniToolbarHidden: jest.fn()
      };
      featureFlagsService = {
         isFeatureEnabled: jest.fn()
      };
      assetLoadingService = {
         isLoading: jest.fn(),
         setLoading: jest.fn()
      };

      TestBed.configureTestingModule({
         imports: [
            NgbModule,
            TestModule, DropDownTestModule, HttpClientTestingModule
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
            { provide: FeatureFlagsService, useValue: featureFlagsService },
            { provide: AssetLoadingService, useValue: assetLoadingService },
         ],
         declarations: [
            ViewerAppComponent, ActionsContextmenuComponent, InteractContainerDirective,
            VSPopComponentDirective, TooltipDirective, MockNotificationsComponent
         ],
         schemas: [ NO_ERRORS_SCHEMA ]
      });
      TestBed.overrideComponent(ViewerAppComponent, {
         set: {
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
   it("should have disabled set as default and hidden full screen button in preview mode", async(() => {
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

   it("should remove the vsobject's actions when removing the vsobject", () => {
      const viewerApp = new ViewerAppComponent(
         viewsheetClientService, null, null, null, null, null, null, null,
         new NgbDatepickerConfig(), null, actionFactory, null, null, formDataService,
         debounceService, scaleService, contextProvider, viewDataService, fullScreenService, router,
         renderer, null, sanitizer, titleService, hyperlinkService, viewerResizeService,
         firstDayOfWeekService, new NgbTooltipConfig(new NgbConfig()), shareService, null,
         richTextService, viewerToolbarMessageService, mobileToolbarService, mockDocument, composerRecentService,
         pageTabService, pagingControlService, selectionMobileService, featureFlagsService,
         assetLoadingService);
      const mockChart = TestUtils.createMockVSChartModel("Mock Chart");
      const mockTable = TestUtils.createMockVSTableModel("Mock Table");
      const mockCrosstab = TestUtils.createMockVSCrosstabModel("Mock Crosstab");
      const mockChartActions = new ChartActions(mockChart, popService, ViewerContextProviderFactory(false));
      const mockTableActions = new TableActions(mockTable, ViewerContextProviderFactory(false), false);
      const mockCrosstabActions = new CrosstabActions(mockCrosstab, ViewerContextProviderFactory(false), false);

      viewerApp.vsObjects = [
         mockChart,
         mockTable,
         mockCrosstab
      ];

      viewerApp.vsObjectActions = [
         mockChartActions,
         mockTableActions,
         mockCrosstabActions
      ];

      const removeCommand: RemoveVSObjectCommand = {
         name: mockTable.absoluteName
      };

      viewerApp.processRemoveVSObjectCommand(removeCommand);

      const vsObjects = viewerApp.vsObjects;
      const actions = viewerApp.vsObjectActions;

      expect(vsObjects.length).toBe(2);
      expect(vsObjects[0]).toBe(mockChart);
      expect(vsObjects[1]).toBe(mockCrosstab);
      expect(vsObjects.indexOf(mockTable)).toBe(-1);

      expect(actions.length).toBe(2);
      expect(actions[0]).toBe(mockChartActions);
      expect(actions[1]).toBe(mockCrosstabActions);
      expect(actions.indexOf(mockTableActions)).toBe(-1);
   });

   // Bug #16961 should refresh scale to screen vs when viewer root pane size changes
   // @by jasonshobe, this test case is failing, but it is bad (testing implementation instead of
   // behavior) so I'm disabling it.
   xit("should refresh viewsheet on viewer root resize", async(() => {
      const fixture: ComponentFixture<ViewerAppComponent> = TestBed.createComponent(ViewerAppComponent);
      fixture.componentInstance.toolbarVisible = true;
      fixture.componentInstance.preview = true;
      fixture.componentInstance.runtimeId = "Foobar";
      fixture.componentInstance["scaleToScreen"] = true;
      fixture.componentInstance.viewerRoot.nativeElement.style.height = "100px";
      fixture.componentInstance.active = true;
      fixture.detectChanges();

      const onViewerRootResize = jest.spyOn(fixture.componentInstance, "onViewerRootResize");
      const refreshViewsheet = jest.spyOn(fixture.componentInstance, "refreshViewsheet");
      fixture.componentInstance.viewerRoot.nativeElement.style.height = "200px";
      const sendEvent = jest.spyOn(viewsheetClientService, "sendEvent");
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
         "Previous"
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

   xit("should open preview layout with scaled size", () => { // broken test
      const fixture: ComponentFixture<ViewerAppComponent> = TestBed.createComponent(ViewerAppComponent);
      fixture.componentInstance.preview = true;
      fixture.componentInstance.runtimeId = "Foobar";
      fixture.componentInstance.layoutName = "Foolayout";
      fixture.componentInstance.guideType = GuideBounds.GUIDES_16_9_PORTRAIT;
      fixture.componentInstance.viewerRoot.nativeElement.style.height = "100px";
      fixture.componentInstance.viewerRoot.nativeElement.style.width = "100px";
      fixture.componentInstance["scaleToScreen"] = true;
      fixture.componentInstance.active = true;

      const sendEvent = jest.spyOn(viewsheetClientService, "sendEvent");
      sendEvent.mockImplementation(() => {});

      fixture.detectChanges();

      expect(sendEvent).toHaveBeenCalled();
      const arg: any = sendEvent.mock.calls[0][1];
      expect(arg.height).toBe(100);
      expect(parseInt(arg.height, 10)).toBe(56);
   });

   // Bug #20628 Bug #20715 should display correct status for previous page
   it("should display correct status for previous page", async(() => {
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

   //Bug #21287
   it("should show confirm dialog when edit form table", () => {
      const fixture: ComponentFixture<ViewerAppComponent> =
         TestBed.createComponent(ViewerAppComponent);

      modelService.getModel.mockImplementation(() => observableOf(true));
      let showConfirmDialog = jest.spyOn(ComponentTool, "showConfirmDialog");
      showConfirmDialog.mockImplementation(() => Promise.resolve("ok"));

      fixture.componentInstance.closeViewsheet();
      expect(showConfirmDialog).toHaveBeenCalled();
      expect(showConfirmDialog.mock.calls[0][1]).toBe("_#(js:Confirm)");
      expect(showConfirmDialog.mock.calls[0][2]).toBe("_#(js:common.warnUnsavedChanges)");
   });

   it("should send and receive viewer toolbar window messages", done => {
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

         viewer.editViewsheet = jest.fn();
         window.postMessage(messageCommand, "*");
      });

      fixture.detectChanges();
   });
});
