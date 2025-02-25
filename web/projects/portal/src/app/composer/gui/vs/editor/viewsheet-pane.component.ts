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
import {
   AfterViewInit,
   ChangeDetectorRef,
   Component,
   ElementRef,
   EventEmitter,
   HostListener,
   Injector,
   Input,
   NgZone,
   OnDestroy,
   OnInit,
   Output,
   Renderer2,
   ViewChild
} from "@angular/core";
import { NgbModal, NgbModalOptions } from "@ng-bootstrap/ng-bootstrap";
import { Subject, Subscription } from "rxjs";
import { AssetEntry, createAssetEntry } from "../../../../../../../shared/data/asset-entry";
import { AssetType } from "../../../../../../../shared/data/asset-type";
import { DownloadService } from "../../../../../../../shared/download/download.service";
import { AppInfoService } from "../../../../../../../shared/util/app-info.service";
import { DateTypeFormatter } from "../../../../../../../shared/util/date-type-formatter";
import { Tool } from "../../../../../../../shared/util/tool";
import { RefreshBindingTreeCommand } from "../../../../binding/command/refresh-binding-tree-command";
import { SetGrayedOutFieldsCommand } from "../../../../binding/command/set-grayed-out-fields-command";
import { SourceInfoType } from "../../../../binding/data/source-info-type";
import { RefreshBindingTreeEvent } from "../../../../binding/event/refresh-binding-tree-event";
import { VSBindingTreeService } from "../../../../binding/widget/binding-tree/vs-binding-tree.service";
import { AssemblyActionEvent } from "../../../../common/action/assembly-action-event";
import { AssemblyActionGroup } from "../../../../common/action/assembly-action-group";
import { ChatService } from "../../../../common/chat/chat.service";
import { AssetEntryHelper } from "../../../../common/data/asset-entry-helper";
import { CommonKVModel } from "../../../../common/data/common-kv-model";
import { DataRef } from "../../../../common/data/data-ref";
import { DragEvent } from "../../../../common/data/drag-event";
import { Line } from "../../../../common/data/line";
import { Point } from "../../../../common/data/point";
import { Rectangle } from "../../../../common/data/rectangle";
import { TableDataPathTypes } from "../../../../common/data/table-data-path-types";
import { VariableInfo } from "../../../../common/data/variable-info";
import { DndService } from "../../../../common/dnd/dnd.service";
import { VSDndService } from "../../../../common/dnd/vs-dnd.service";
import { CollectParametersOverEvent } from "../../../../common/event/collect-parameters-over-event";
import { UIContextService } from "../../../../common/services/ui-context.service";
import { ComponentTool } from "../../../../common/util/component-tool";
import { DataPathConstants } from "../../../../common/util/data-path-constants";
import { GuiTool } from "../../../../common/util/gui-tool";
import { CommandProcessor, ViewsheetClientService } from "../../../../common/viewsheet-client";
import { MessageCommand } from "../../../../common/viewsheet-client/message-command";
import { ChartTool } from "../../../../graph/model/chart-tool";
import { ChartService } from "../../../../graph/services/chart.service";
import { AssemblyActionFactory } from "../../../../vsobjects/action/assembly-action-factory.service";
import { AddVSObjectCommand } from "../../../../vsobjects/command/add-vs-object-command";
import { ClearLoadingCommand } from "../../../../vsobjects/command/clear-loading-command";
import { CollectParametersCommand } from "../../../../vsobjects/command/collect-parameters-command";
import { ExportVSCommand } from "../../../../vsobjects/command/export-vs-command";
import { InitGridCommand } from "../../../../vsobjects/command/init-grid-command";
import { RefreshVSObjectCommand } from "../../../../vsobjects/command/refresh-vs-object-command";
import { RemoveVSObjectCommand } from "../../../../vsobjects/command/remove-vs-object-command";
import { RenameVSObjectCommand } from "../../../../vsobjects/command/rename-vs-object-command";
import { SetCurrentFormatCommand } from "../../../../vsobjects/command/set-current-format-command";
import { SetRuntimeIdCommand } from "../../../../vsobjects/command/set-runtime-id-command";
import { SetViewsheetInfoCommand } from "../../../../vsobjects/command/set-viewsheet-info-command";
import { ShowLoadingMaskCommand } from "../../../../vsobjects/command/show-loading-mask-command";
import { UpdateLayoutCommand } from "../../../../vsobjects/command/update-layout-command";
import { UpdateLayoutUndoStateCommand } from "../../../../vsobjects/command/update-layout-undo-state-command";
import { UpdateUndoStateCommand } from "../../../../vsobjects/command/update-unto-state-command";
import {
   ComposerContextProviderFactory,
   ContextProvider
} from "../../../../vsobjects/context-provider.service";
import { CancelViewsheetLoadingEvent } from "../../../../vsobjects/event/cancel-viewsheet-loading-event";
import { FormatVSObjectEvent } from "../../../../vsobjects/event/format-vs-object-event";
import { GetVSObjectFormatEvent } from "../../../../vsobjects/event/get-vs-object-format-event";
import { OpenViewsheetEvent } from "../../../../vsobjects/event/open-viewsheet-event";
import { VSCalendarModel } from "../../../../vsobjects/model/calendar/vs-calendar-model";
import { VSChartModel } from "../../../../vsobjects/model/vs-chart-model";
import { VSLineModel } from "../../../../vsobjects/model/vs-line-model";
import { VSObjectModel } from "../../../../vsobjects/model/vs-object-model";
import { VSViewsheetModel } from "../../../../vsobjects/model/vs-viewsheet-model";
import { VSChartService } from "../../../../vsobjects/objects/chart/services/vs-chart.service";
import { AdhocFilterService } from "../../../../vsobjects/objects/data-tip/adhoc-filter.service";
import { DataTipService } from "../../../../vsobjects/objects/data-tip/data-tip.service";
import { PopComponentService } from "../../../../vsobjects/objects/data-tip/pop-component.service";
import { SelectionContainerChildrenService } from "../../../../vsobjects/objects/selection/services/selection-container-children.service";
import { VSUtil } from "../../../../vsobjects/util/vs-util";
import { ConsoleDialogComponent } from "../../../../widget/console-dialog/console-dialog.component";
import { ConsoleMessage } from "../../../../widget/console-dialog/console-message";
import { VariableInputDialogModel } from "../../../../widget/dialog/variable-input-dialog/variable-input-dialog-model";
import { VariableInputDialog } from "../../../../widget/dialog/variable-input-dialog/variable-input-dialog.component";
import { SelectionBoxEvent } from "../../../../widget/directive/selection-box.directive";
import { DomService } from "../../../../widget/dom-service/dom.service";
import { InteractContainerDirective } from "../../../../widget/interact/interact-container.directive";
import { NotificationsComponent } from "../../../../widget/notifications/notifications.component";
import { PlaceholderDragElementModel } from "../../../../widget/placeholder-drag-element/placeholder-drag-element-model";
import { DebounceService } from "../../../../widget/services/debounce.service";
import { DragService } from "../../../../widget/services/drag.service";
import { ModelService } from "../../../../widget/services/model.service";
import { ScaleService } from "../../../../widget/services/scale/scale-service";
import {
   ComposerDialogServiceFactory,
   DialogService
} from "../../../../widget/slide-out/dialog-service.service";
import { SlideOutService } from "../../../../widget/slide-out/slide-out.service";
import { TreeNodeModel } from "../../../../widget/tree/tree-node-model";
import { OpenSheetEvent } from "../../../data/open-sheet-event";
import { Viewsheet } from "../../../data/vs/viewsheet";
import { VSLayoutModel } from "../../../data/vs/vs-layout-model";
import { VSLayoutObjectModel } from "../../../data/vs/vs-layout-object-model";
import { Status } from "../../../../status-bar/status";
import { CloseSheetCommand } from "../../ws/socket/close-sheet-command";
import { ExpiredSheetCommand } from "../../ws/socket/expired-sheet/expired-sheet-command";
import { OpenSheetEventValidator } from "../../ws/socket/open-ws/open-sheet-event-validator";
import { SaveSheetCommand } from "../../ws/socket/save-sheet-command";
import { TouchAssetEvent } from "../../ws/socket/touch-asset-event";
import { ChangeCurrentLayoutCommand } from "../command/change-current-layout-command";
import { PopulateVSObjectTreeCommand } from "../command/populate-vs-object-tree-command";
import { ReopenSheetCommand } from "../command/reopen-sheet-command";
import { UpdateZIndexesCommand } from "../command/update-zindexes-command";
import { ComposerObjectService } from "../composer-object.service";
import { NewViewsheetEvent } from "../event/new-viewsheet-event";
import { AssemblyChangedCommand } from "../../../../vs-wizard/model/command/assembly-changed-command";
import { RefreshVsAssemblyEvent } from "../../../../vsobjects/event/refresh-vs-assembly-event";
import { ResizeHandlerService } from "../../resize-handler.service";
import { ComposerVsSearchService } from "../composer-vs-search.service";
import { VSSelectionContainerModel } from "../../../../vsobjects/model/vs-selection-container-model";
import { PrintLayoutSection } from "../../../../vsobjects/model/layout/print-layout-section";
import { VSDependencyChangedCommand } from "../../../../vsobjects/command/vs-dependency-changed-command";
import { LayoutUtil } from "../../../../vsobjects/util/layout-util";

const COLLECT_PARAMS_URI = "/events/vs/collectParameters";

@Component({
   selector: "viewsheet-pane",
   templateUrl: "viewsheet-pane.component.html",
   styleUrls: ["viewsheet-pane.component.scss"],
   providers: [
      DataTipService,
      AdhocFilterService,
      PopComponentService,
      SelectionContainerChildrenService,
      ViewsheetClientService,
      VSChartService,
      ComposerVsSearchService,
      DebounceService,
      {
         provide: ContextProvider,
         useFactory: ComposerContextProviderFactory
      },
      {
         provide: DialogService,
         useFactory: ComposerDialogServiceFactory,
         deps: [NgbModal, SlideOutService, Injector, UIContextService]
      },
      AssemblyActionFactory,
      {
         provide: DndService,
         useClass: VSDndService,
         deps: [ModelService, NgbModal, ViewsheetClientService]
      },
      {
         provide: ChartService,
         useExisting: VSChartService
      }
   ]
})
export class VSPane extends CommandProcessor implements OnInit, OnDestroy, AfterViewInit {
   @Input() vs: Viewsheet;
   @Input() touchDevice: boolean;
   @Input() snapToGrid: boolean = false;
   @Input() snapToObjects: boolean = false;
   @Input() showPaste: boolean = false;
   @Input() deployed: boolean;
   @Input() worksheetPermission: boolean;
   @Input() lastClick: Point = new Point();
   @Input() vsScroll: Point = new Point();
   @Input() containerView: any;
   @Input() set active(active: boolean) {
      if(active) {
         if(this.vsPaneBounds == null && this.vsPane) {
            this.vsPaneBounds = this.vsPane.nativeElement.getBoundingClientRect();
         }

         this.changeDetectorRef.reattach();
      }
      else {
         this.changeDetectorRef.detach();
      }
   }
   @Output() onCopy: EventEmitter<VSObjectModel> = new EventEmitter<VSObjectModel>();
   @Output() onCut: EventEmitter<VSObjectModel> = new EventEmitter<VSObjectModel>();
   @Output() onPaste: EventEmitter<[Viewsheet, Point]> = new EventEmitter<[Viewsheet, Point]>();
   @Output() onRemove: EventEmitter<VSObjectModel> = new EventEmitter<VSObjectModel>();
   @Output() onBringToFront: EventEmitter<VSObjectModel> = new EventEmitter<VSObjectModel>();
   @Output() onBringForward: EventEmitter<VSObjectModel> = new EventEmitter<VSObjectModel>();
   @Output() onSendToBack: EventEmitter<VSObjectModel> = new EventEmitter<VSObjectModel>();
   @Output() onSendBackward: EventEmitter<VSObjectModel> = new EventEmitter<VSObjectModel>();
   @Output() onSheetChange: EventEmitter<Viewsheet> = new EventEmitter<Viewsheet>();
   @Output() onSheetClose: EventEmitter<Viewsheet> = new EventEmitter<Viewsheet>();
   @Output() onSheetReload: EventEmitter<Viewsheet> = new EventEmitter<Viewsheet>();
   @Output() onPreviewViewsheet: EventEmitter<Viewsheet> = new EventEmitter<Viewsheet>();
   @Output() onOpenViewsheetOptions: EventEmitter<Viewsheet> = new EventEmitter<Viewsheet>();
   @Output() onLayoutObjectChange: EventEmitter<any> = new EventEmitter<any>();
   @Output() public onOpenEmbeddedViewsheet = new EventEmitter<string>();
   @Output() onOpenEditPane: EventEmitter<VSObjectModel> = new EventEmitter<VSObjectModel>();
   @Output() onOpenWizardPane: EventEmitter<any> = new EventEmitter<any>();
   @Output() onRefreshViewsheet: EventEmitter<Viewsheet> = new EventEmitter<Viewsheet>();
   @Output() onSaveViewsheet: EventEmitter<Viewsheet> = new EventEmitter<Viewsheet>();
   @Output() onTransformFinished: EventEmitter<Viewsheet> = new EventEmitter<Viewsheet>();
   @Output() onDependencyChanged: EventEmitter<[Viewsheet, boolean]> = new EventEmitter<[Viewsheet, boolean]>();
   @Output() onOpenSheet = new EventEmitter<OpenSheetEvent>();
   @Output() onOpenVSOnPortal = new EventEmitter<string>();
   @Output() onGrayedOutFields: EventEmitter<DataRef[]> = new EventEmitter<DataRef[]>();
   @Output() onOpenScript = new EventEmitter<Viewsheet>();
   @Output() closeEditPane = new EventEmitter<void>();
   @Output() onOpenFormatPane: EventEmitter<VSObjectModel> = new EventEmitter<VSObjectModel>();
   @ViewChild("vsPane") vsPane: ElementRef;
   @ViewChild("variableInputDialog") variableInputDialog: VariableInputDialog;
   @ViewChild(InteractContainerDirective) interactContainer: InteractContainerDirective;
   @ViewChild("notifications") notifications: NotificationsComponent;
   @ViewChild("consoleDialog") consoleDialog: ConsoleDialogComponent;
   variableInputDialogModel: VariableInputDialogModel;
   placeholderDragElementModel: PlaceholderDragElementModel = <PlaceholderDragElementModel> {
      top: 0,
      left: 0,
      width: 0,
      height: 0,
      text: "",
      visible: false
   };

   rulersVisible: boolean = true;
   rulerGuidesVisible: boolean = false;
   rulerGuideTop: number = 0;
   rulerGuideLeft: number = 0;
   rulerGuideWidth: number = 0;
   rulerGuideHeight: number = 0;
   rulerTop: number = 0;
   rulerLeft: number = 0;

   preparingData: boolean = false;
   viewsheetBackground: string;
   templateWidth: number = 0;
   templateHeight: number = 0;
   templateEnabled: boolean = false;
   hasScript: boolean = false;
   initialParametersCollect: boolean = true;
   private mouseUpResizeListener: () => void;

   status: Status;
   status2: Status;
   maxModeAssembly: string;

   objectDraggedIn: string = "";
   objectDraggedInSize: {width: number, height: number};
   vsPaneBounds: ClientRect;
   boundingBox: ClientRect;

   private draggableRestrictionRects: Map<any, {left: number, top: number, right: number, bottom: number}>;
   draggableSnapGuides: {horizontal: number[], vertical: number[]} = {horizontal: [], vertical: []};
   currentSnapGuides: {x: number, y: number} = null;
   selectionBorderOffset: number = 2;
   snapOffset = 0;
   consoleMessages: ConsoleMessage[] = [];
   guideLineColor: string;
   autoFocusSearchTimeout: any;
   searchResultCount: number = 0;
   textLimitConfirmed: boolean = false;
   columnLimitConfirmed: boolean = false;
   mobileDevice = GuiTool.isMobileDevice();
   private orgInfo: CommonKVModel<string, string> = null;
   subscriptions = new Subscription();

   draggableRestriction = (x: number, y: number, element: any) => {
      if(!this.draggableRestrictionRects) {
         this.draggableRestrictionRects = new Map<any, {left: number, top: number, right: number,
                                                        bottom: number}>();
      }

      let draggableRestrictionRect = this.draggableRestrictionRects.get(element);

      if(!draggableRestrictionRect) {
         const containerElement = this.vsPane.nativeElement;
         const containerRect = GuiTool.getElementRect(containerElement);
         const elementRect = GuiTool.getElementRect(element);
         let offsetX = 0;
         let offsetY = 0;

         if(this.vs.currentFocusedAssemblies.length > 1) {
            this.vs.currentFocusedAssemblies
               .filter(a => !a.interactionDisabled)
               .forEach((assembly) => {
                  // @by changhongyang 2017-10-23, use the bounds of the tab when calculating
                  // the draggable restrictions of any tab elements
                  if(assembly.container) {
                     const container = this.vs.getAssembly(assembly.container);

                     if(container && container.objectType === "VSTab") {
                        assembly = container;
                     }
                  }

                  const assemblyElement = containerElement.querySelector(
                     `.object-editor[data-name='${assembly.absoluteName}']`);

                  if(assemblyElement) {
                     const assemblyRect = GuiTool.getElementRect(assemblyElement);
                     offsetX = Math.max(offsetX, elementRect.left - assemblyRect.left);
                     offsetY = Math.max(offsetY, elementRect.top - assemblyRect.top);
                  }
               });
         }

         // offset for the selection border
         const selBorderSize = element.classList.contains("line-resize-container") ? 3 : 2;

         draggableRestrictionRect = {
            top: containerRect.top + offsetY - selBorderSize,
            left: containerRect.left + offsetX - selBorderSize,
            bottom: containerRect.bottom,
            right: containerRect.right
         };

         this.draggableRestrictionRects.set(element, draggableRestrictionRect);
      }

      return draggableRestrictionRect;
   };

   private focusedObjectsSubject: Subscription;
   private click: boolean = false;
   private confirmExpiredDisplayed: boolean = false;
   private heartbeatSubscription: Subscription;
   private renameTransformSubscription: Subscription;
   private transformSubscription: Subscription;
   private loadingEventCount: number = 0;
   private resizeTimeout: any = null;
   private moveTimeout: any = null;
   private closeProgressSubject: Subject<any> = new Subject();
   private moving: boolean = false;
   private refreshSubscription = Subscription.EMPTY;
   private messageCommandsSubscription = Subscription.EMPTY;
   private resizeSubscription = Subscription.EMPTY;
   private _searchResultLabel: string;

   public menuPosition: Point = new Point();
   public menuActions: AssemblyActionGroup[] = [
      new AssemblyActionGroup([
         {
            id: () => "composer vspane paste",
            label: () => "_#(js:Paste)",
            icon: () => "paste-icon",
            enabled: () => this.showPaste,
            visible: () => true,
            action: () => this.paste()
         },
         {
            id: () => "composer vspane options",
            label: () => "_#(js:Options)...",
            icon: () => "setting-icon",
            enabled: () => true,
            visible: () => !this.deployed,
            action: () => this.onOpenViewsheetOptions.emit(this.vs)
         },
         {
            id: () => "composer vspane preview",
            label: () => "_#(js:Preview)",
            icon: () => "eye-icon",
            enabled: () => true,
            visible: () => true,
            action: () => this.onPreviewViewsheet.emit(this.vs)
         },
         {
            id: () => "composer show-format-pane",
            label: () => "_#(js:Format)...",
            icon: () => "fa fa-format",
            enabled: () => true,
            visible: () => true,
            action: () => this.onOpenFormatPane.emit(null)
         }
      ]),
      new AssemblyActionGroup([
         {
            id: () => "composer vspane refresh",
            label: () => "_#(js:Refresh)",
            icon: () => "redo-icon",
            enabled: () => true,
            visible: () => true,
            action: () => this.onRefreshViewsheet.emit(this.vs)
         },
         {
            id: () => "composer vspane zoom-in",
            label: () => "_#(js:Zoom In)",
            icon: () => "plus-icon",
            enabled: () => Number(this.vs.scale.toFixed(2)) >= 0.2 && Number(this.vs.scale.toFixed(2)) < 2.0,
            visible: () => true,
            action: () => this.zoom(false)
         },
         {
            id: () => "composer vspane zoom-out",
            label: () => "_#(js:Zoom Out)",
            icon: () => "minus-icon",
            enabled: () => Number(this.vs.scale.toFixed(2)) > 0.2 && Number(this.vs.scale.toFixed(2)) <= 2.0,
            visible: () => true,
            action: () => this.zoom(true)
         }
      ])
   ];

   constructor(private element: ElementRef,
               private composerObjectService: ComposerObjectService,
               private viewsheetClient: ViewsheetClientService,
               private treeService: VSBindingTreeService,
               private changeDetectorRef: ChangeDetectorRef,
               private modelService: ModelService,
               private modalService: NgbModal,
               private downloadService: DownloadService,
               private dragService: DragService,
               private scaleService: ScaleService,
               private renderer: Renderer2,
               actionFactory: AssemblyActionFactory,
               private dialogService: DialogService,
               private dataTipService: DataTipService,
               private debounceService: DebounceService,
               private uiContextService: UIContextService,
               protected zone: NgZone,
               private domService: DomService,
               private chatService: ChatService,
               private resizeHandlerService: ResizeHandlerService,
               private composerVsSearchService: ComposerVsSearchService,
               private appInfoService: AppInfoService)
   {
      super(viewsheetClient, zone, true);
      actionFactory.stateProvider = {
         isActionEnabled: (id: string, model: VSObjectModel) => this.isActionEnabled(id, model),
         isActionVisible: (id: string, model: VSObjectModel) => true
      };

      this.subscriptions.add(this.appInfoService.getCurrentOrgInfo().subscribe((orgInfo) => {
         this.orgInfo = orgInfo;
      }));
   }

   getAssemblyName(): string {
      return null;
   }

   getSnapGridStyle() {
      let style = {};

      if(this.snapToGrid) {
         style = {
            "background-image": this.getBackgroundImage(this.vs.snapGrid),
         };
      }

      return style;
   }

   getBackgroundImage(snapGrid: number) {
      let url = "url" + "(data:image/svg+xml;charset=UTF-8,%3Csvg%20xmlns%3D%22http%3A%2F%2Fwww.w3.org%2F2000%2Fsvg%22%20width%3D%22"
      +snapGrid+"%22%20height%3D%22"+snapGrid+"%22%3E%3Ccircle%20cx%3D%220.5%22%20cy%3D%220.5%22%20r%3D%220.5%22%20stroke-width%3D%220%22%20fill%3D%22%23999%22%2F%3E%3C%2Fsvg%3E)";

      return url;
   }

   ngOnInit() {
      this.viewsheetClient.connect();

      this.heartbeatSubscription = this.viewsheetClient.onHeartbeat.subscribe(() => {
         this.touchAsset();
      });

      this.renameTransformSubscription = this.viewsheetClient.onRenameTransformFinished.subscribe(
         (message) => {
            if(message.id == this.vs.runtimeId) {
               this.onSaveViewsheet.emit(this.vs);
            }
         });

      this.transformSubscription = this.viewsheetClient.onTransformFinished.subscribe(
         (message) => {
            if(message.id == this.vs.runtimeId) {
               this.onTransformFinished.emit(this.vs);
            }
         });

      this.vs.socketConnection = this.viewsheetClient;

      let size: [number, number] = GuiTool.getViewportSize();
      let mobile: boolean = GuiTool.isMobileDevice();

      if(this.vs.newSheet && !!!this.vs.runtimeId) {
         if(this.vs.autoSaveFile != null) {
            let event: OpenViewsheetEvent = new OpenViewsheetEvent(
               this.vs.id, size[0], size[1], mobile, window.navigator.userAgent, this.vs.meta);
            event.viewer = false;
            event.embeddedViewsheetId = null;
            event.openAutoSaved = true;

            this.vs.socketConnection.sendEvent("/events/open", event);
         }
         else {
            let event: NewViewsheetEvent = new NewViewsheetEvent(
               this.vs.id, 0, 0, mobile, window.navigator.userAgent, this.vs.baseEntry);
            event.viewer = false;
            this.viewsheetClient.sendEvent("/events/composer/viewsheet/new", event);
         }
      }
      else {
         let event: OpenViewsheetEvent = new OpenViewsheetEvent(
            this.vs.id, size[0], size[1], mobile, window.navigator.userAgent, this.vs.meta, this.vs.newSheet);
         event.viewer = false;
         event.embeddedViewsheetId = this.vs.embeddedId;

         this.openExistingViewsheet(event);
      }

      this.focusedObjectsSubject = this.vs.focusedAssemblies
         .subscribe((assemblies) => {
            const groupNotFocused = Tool.clone(assemblies)
               .some(a => a.interactionDisabled && a.objectType == "VSGroupContainer");

            if(groupNotFocused) {
               if(assemblies.length == 1) {
                  assemblies[0].interactionDisabled = false;
               }
               else {
                  this.notifications.info("_#(js:viewer.viewsheet.containerNotFocused)");
               }
            }

            this.updateFormats();
            // schedule as a new microtask in case this is being invoked during change
            // detection
            Promise.resolve(null).then(() => this.updateRulerGuides());
            Promise.resolve(null).then(() => this.updateRulerPosition());
            Promise.resolve(null).then(() => this.refreshStatus());
            Promise.resolve(null).then(() => this.updateSnapGuides());
            this.draggableRestrictionRects = null;
         });

      this.zone.runOutsideAngular(() => {
         this.mouseUpResizeListener = this.renderer.listen(
            "document", "mouseup", (evt: MouseEvent) => {
               const focusInput = document.activeElement instanceof HTMLTextAreaElement ||
                  document.activeElement instanceof HTMLInputElement &&
                  document.activeElement.type == "text";

               // when using mouse to select text, it's very easy to exceed the bounds of
               // text input. don't deselect the text in this case.
               if(!focusInput) {
                  this.resetCursor(evt);
                  this.deselectObjects(evt);
               }
            });
      });

      this.vs.scale = 1;
      this.messageCommandsSubscription = this.vs.getMessageCommandObservable()
         .subscribe(c => this.addConsoleMessage(c));
      this.vs.objectRemoved.subscribe((name) => {
         this.removeVSObject(name);
      });

      this.resizeSubscription = this.resizeHandlerService.anyResizeSubject.subscribe(() => {
         this.draggableRestrictionRects = null;
      });
   }

   ngAfterViewInit() {
      if(this.vsPane != null) {
         this.vsPaneBounds = this.vsPane.nativeElement.getBoundingClientRect();
      }
   }

   zoom(out: boolean) {
      if((this.vs.scale <= 0.2 && out) || (this.vs.scale >= 2.0 && !out)) {
         return;
      }

      this.vs.scale = Tool.numberCalculate(this.vs.scale, 0.2, out);
      this.scaleService.setScale(this.vs.scale);
   }

   ngOnDestroy() {
      super.cleanup();
      this.focusedObjectsSubject.unsubscribe();
      this.heartbeatSubscription.unsubscribe();
      this.renameTransformSubscription.unsubscribe();
      this.refreshSubscription.unsubscribe();
      this.messageCommandsSubscription.unsubscribe();

      if(this.resizeSubscription != null) {
         this.resizeSubscription.unsubscribe();
      }

      this.mouseUpResizeListener();
      this.chatService.closeSession();
      this.subscriptions.unsubscribe();
   }

   /** Open an existing viewsheet. In the case that it is autosaved, show a message. */
   openExistingViewsheet(event: OpenViewsheetEvent) {
      // In the case that this view is reloaded. Use old runtimeId.
      if(this.vs && this.vs.runtimeId) {
         event.runtimeViewsheetId = this.vs.runtimeId;
         this.viewsheetClient.runtimeId = this.vs.runtimeId;
      }

      // when reloading a new sheet, entryId may be null
      if(event.entryId) {
         this.modelService.sendModel("../api/vs/open", event).forEach((res) => {
            let promise: Promise<any> = Promise.resolve(null);
            let validator: OpenSheetEventValidator = <OpenSheetEventValidator> res.body;

            if(validator != null && validator.autoSaveFileExists) {
               promise = promise.then(() => {
                  return this.confirm("_#(js:designer.designer.autosavedFileExists)")
                     .then((val: boolean) => event.openAutoSaved = val);
               });
            }

            promise.then(() => {
               this.vs.socketConnection.sendEvent("/events/open", event);
            });
         });
      }
      else {
         this.vs.socketConnection.sendEvent("/events/open", event);
      }
   }

   detectChanges(callChangeDetector: boolean) {
      this.updateRulerGuides();
      this.updateRulerPosition();
      this.refreshStatus();

      if(callChangeDetector) {
         this.changeDetectorRef.detectChanges();
      }
   }

   private isActionEnabled(id: string, model: VSObjectModel): boolean {
      if(model && this.vs) {
         switch(id) {
         case "vs-object group":
            return this.isGroupActionEnabled(model);
         case "vs-object ungroup":
            return this.isUngroupActionEnabled(model);
         case "vs-object bring-to-front":
         case "vs-object bring-forward":
            return this.isBringToFrontActionEnabled(model);
         case "vs-object send-to-back":
         case "vs-object send-backward":
            return this.isSendToBackActionEnabled(model);
         }
      }

      return true;
   }

   private isGroupActionEnabled(model: VSObjectModel): boolean {
      let count: number = 0;
      let tabParentCount: number = 0;

      const groupingDisabled: boolean =
         this.vs.currentFocusedAssemblies.some((assembly: VSObjectModel) => {
            if((<any> assembly).adhocFilter) {
               // child of selection container shouldn't be grouped with others
            }
            else if(assembly.container) {
               let container: VSObjectModel = this.vs.getAssembly(assembly.container);

               if(container && container.objectType === "VSTab") {
                  tabParentCount++;
               }

               if(!this.vs.isAssemblyFocused(assembly.container)) {
                  count++;
               }
            }
            else {
               count++;
            }

            return assembly.objectType === "VSViewsheet" ||
               assembly.objectType === "VSSelectionContainer";
         });

      return count > 1 && tabParentCount < 2 && !groupingDisabled;
   }

   private isUngroupActionEnabled(model: VSObjectModel): boolean {
      return model.objectType === "VSGroupContainer" || model.grouped;
   }

   private isBringToFrontActionEnabled(model: VSObjectModel): boolean {
      if(model.container &&
         (model.containerType === "VSTab" || model.containerType === "VSSelectionContainer"))
      {
         return false;
      }

      return !!model.objectFormat && model.objectFormat.bringToFrontEnabled;
   }

   private isSendToBackActionEnabled(model: VSObjectModel): boolean {
      if(model.container &&
         (model.containerType === "VSTab" || model.containerType === "VSSelectionContainer"))
      {
         return false;
      }

      return !!model.objectFormat && model.objectFormat.sendToBackEnabled;
   }

   /**
    * Check if is a snapshot.
    * @return true if is a snapshot.
    */
   public isSnapshot(): Boolean {
      return this.vs && this.vs.baseEntry ?
         AssetEntryHelper.isVSSnapshot(this.vs.baseEntry) : false;
   }

   /**
    * Retrieve basic viewsheet info.
    * @param {SetViewsheetInfoCommand} command
    */
   private processSetViewsheetInfoCommand(command: SetViewsheetInfoCommand): void {
      this.vs.label = command.assemblyInfo["name"];
      this.vs.layouts = command.layouts;
      this.vs.baseEntry = command.baseEntry;
      this.viewsheetBackground = command.info["viewsheetBackground"];
      this.vs.statusText = command.info["statusText"];
console.log("======111===");
      if(command.info["lastModifiedTime"] != null) {
         this.vs.statusText = this.vs.statusText +  DateTypeFormatter.format(command.info["lastModifiedTime"], command.info["dateFormat"]);
         console.log("======1111======="+command.info["lastModifiedTime"]);
      }

      this.templateWidth = command.info["templateWidth"];
      this.templateHeight = command.info["templateHeight"];
      this.templateEnabled = command.info["templateEnabled"];
      this.vs.metadata = command.info["metadata"];
      this.vs.messageLevels = command.info["messageLevels"];
      this.vs.snapGrid = command.info["snapGrid"];
      this.hasScript = command.hasScript;
      this.refreshStatus();

      if(command.linkUri) {
         this.vs.linkUri = command.linkUri;
      }

      if(this.vs.currentLayout) {
         this.layoutChanged(this.viewsheetClient.focusedLayoutName, false);
      }

      this.guideLineColor = GuiTool.getContrastColor(this.viewsheetBackground, "#000000");
      this.vs.socketConnection.sendEvent("/events/vs/bindingtree/gettreemodel",
         new RefreshBindingTreeEvent(null, this.deployed));
   }

   private processUpdateLayoutCommand(command: UpdateLayoutCommand) {
      this.viewsheetClient.focusedLayoutName = command.layoutName;
   }

   private processVSDependencyChangedCommand(command: VSDependencyChangedCommand) {
      this.onDependencyChanged.emit([this.vs, command.wizard]);
   }

   // After rename dependency, should reload the viewsheet so it can get latest data.
   private processReopenSheetCommand(command: ReopenSheetCommand) {
      if(command.id == this.vs.id) {
         this.onSheetReload.emit(this.vs);
      }
   }

   /**
    * Receive parameter prompts.
    * @param {CollectParametersCommand} command
    */
   private processCollectParametersCommand(command: CollectParametersCommand): void {
      this.initialParametersCollect = command.isOpenSheet;
      let vars: VariableInfo[] = [];
      let disVars: VariableInfo[] = [];

      command.variables.forEach((variable: VariableInfo) => {
         let index: number = command.disabledVariables.indexOf(variable.name);

         if(index == -1) {
            vars.push(variable);
         }
         else{
            variable.values = [];
            disVars.push(variable);
         }
      });

      if(!command.disableParameterSheet && vars.length > 0) {
         this.enterParameters(vars, disVars, command.isOpenSheet);
      }
      else {
         const variables: VariableInfo[] = [];
         let event: CollectParametersOverEvent = new CollectParametersOverEvent(variables, true, command.isOpenSheet);
         this.viewsheetClient.sendEvent(COLLECT_PARAMS_URI, event);
      }
   }

   private enterParameters(variables: VariableInfo[], disabledVariables: VariableInfo[], openVS: boolean = false) {
      this.variableInputDialogModel = <VariableInputDialogModel> {
         varInfos: variables
      };

      this.modalService.open(this.variableInputDialog, {backdrop: "static"}).result
         .then(
            (model: VariableInputDialogModel) => {
               const vars: VariableInfo[] = model.varInfos.concat(disabledVariables);
               let event: CollectParametersOverEvent =
                  new CollectParametersOverEvent(vars, !this.initialParametersCollect, openVS);
               this.viewsheetClient.sendEvent(COLLECT_PARAMS_URI, event);
               this.initialParametersCollect = false;
            },
            (cancel: String) => {
               if(cancel === "cancelSheet") {
                  this.onSheetClose.emit(this.vs);
               }
            }
         );
   }

   /**
    * @param {InitGridCommand} command
    */
   private processInitGridCommand(command: InitGridCommand): void {
      if(command.initing) {
         this.vs.vsObjects = [];
         this.vs.id = command.entry.identifier;
      }
   }

   /**
    * Used to update undo/redo state of layout pane in vs.
    * @param {UpdateLayoutUndoStateCommand} command
    */
   public processUpdateLayoutUndoStateCommand(command: UpdateLayoutUndoStateCommand): void {
      if(this.vs.currentLayout) {
         this.vs.layoutPoint = command.layoutPoint;
         this.vs.layoutPoints = command.layoutPoints;
         this.vs.socketConnection.lastModified = this.viewsheetClient.lastModified;
         this.onSheetChange.emit(this.vs);
      }
   }

   /**
    * Used to update undo/redo state of vs.
    * @param {UpdateUndoStateCommand} command
    */
   private processUpdateUndoStateCommand(command: UpdateUndoStateCommand): void {
      this.vs.points = command.points;
      this.vs.current = command.current;
      this.vs.currentTS = (new Date()).getTime();
      this.vs.savePoint = command.savePoint;
      this.onSheetChange.emit(this.vs);
   }

   protected isInZone(messageType: string): boolean {
      return messageType != "ClearLoadingCommand" && messageType != "ShowLoadingMaskCommand";
   }

   /**
    * Clear the viewsheet loading icon
    */
   processClearLoadingCommand(command: ClearLoadingCommand): void {
      this.loadingEventCount = Math.max(0, this.loadingEventCount - command.count);

      // clear loading if there are no more loading events waiting for clear command
      if(this.loadingEventCount == 0 && this.vs.loading) {
         this.zone.run(() => {
            this.vs.loading = false;
            this.preparingData = false;
         });
      }
   }

   processShowLoadingMaskCommand(command: ShowLoadingMaskCommand): void {
      // don't increment the second command that turns on preparing data label
      if(!command.preparingData) {
         this.loadingEventCount++;
      }

      this.preparingData = command.preparingData;
      this.vs.loading = true;
      this.changeDetectorRef.detectChanges();
   }

   /**
    * Requests that the server stop the loading of the viewsheet.
    */
   cancelViewsheetLoading(): void {
      this.notifications.success("_#(js:common.viewsheet.cancelled)");
      this.vs.loading = false;
      let event: CancelViewsheetLoadingEvent = new CancelViewsheetLoadingEvent(this.vs.runtimeId);
      this.viewsheetClient.sendEvent("/events/composer/viewsheet/cancelViewsheet", event);
   }

   /**
    * Used to signal that the viewsheet should be closed.
    * @param {CloseSheetCommand} command
    */
   private processCloseSheetCommand(command: CloseSheetCommand) {
      this.onSheetClose.emit(this.vs);
   }

   /**
    * Used to update the viewsheet's save point
    * @param {SaveSheetCommand} command
    */
   private processSaveSheetCommand(command: SaveSheetCommand) {
      this.vs.newSheet = false;
      this.vs.savePoint = command.savePoint;
      this.vs.id = command.id;
      this.notifications.success("_#(js:common.viewsheet.saveSuccess)");

      if(this.vs.gettingStarted) {
         this.onOpenVSOnPortal.emit(this.vs.id);
      }
   }

   /**
    * Used to prompt the user to refresh the viewsheet
    * @param {ExpiredSheetCommand} command
    */
   private processExpiredSheetCommand(command: ExpiredSheetCommand) {
      if(!this.confirmExpiredDisplayed) {
         this.confirmExpiredDisplayed = true;
         this.heartbeatSubscription.unsubscribe();
         this.renameTransformSubscription.unsubscribe();

         const message: string = "_#(js:common.expiredSheets)" +
            ComponentTool.MESSAGEDIALOG_MESSAGE_CONNECTION +
            (!!this.vs && !!this.vs.label ? "Viewsheet " + this.vs.label : "viewsheet");
         this.confirm(message).then((ok) => {
            this.confirmExpiredDisplayed = false;

            if(ok) {
               this.onSheetReload.emit(this.vs);
            }
         });
      }
   }

   /**
    * Used to change currently selected viewsheet layout.
    * @param {ChangeCurrentLayoutCommand} command
    */
   private processChangeCurrentLayoutCommand(command: ChangeCurrentLayoutCommand): void {
      if(!command.layout) {
         this.vs.currentLayout = null;
         this.viewsheetClient.runtimeId = this.vs.runtimeId;
         this.refreshStatus();
      }
      else if(this.vs.currentLayout && ((command.layout.name == this.vs.currentLayout.name)
         || (command.layout.printLayout && this.vs.currentLayout.printLayout)))
      {
         const region = this.vs.currentLayout.currentPrintSection;
         const guideType = this.vs.currentLayout.guideType;
         this.vs.currentLayout = new VSLayoutModel(command.layout);
         this.vs.currentLayout.currentPrintSection = region;
         this.vs.currentLayout.guideType = guideType;
      }
      else {
         this.vs.currentLayout = new VSLayoutModel(command.layout);
         this.vs.currentLayout.guideType = this.vs.currentLayoutGuides;
      }

      this.onSheetChange.emit(this.vs);
   }

   /**
    * Retrieve runtime ID.
    * @param {SetRuntimeIdCommand} command
    */
   private processSetRuntimeIdCommand(command: SetRuntimeIdCommand): void {
      this.vs.runtimeId = command.runtimeId;
      this.viewsheetClient.runtimeId = command.runtimeId;
      this.dialogService.setSheetId(this.vs.runtimeId);

      if(this.vs.bindingTreeInitialLoad) {
         this.vs.bindingTreeInitialLoad = false;
         this.vs.socketConnection.sendEvent("/events/vs/bindingtree/gettreemodel",
            new RefreshBindingTreeEvent(null, this.deployed));
      }
   }

   /**
    * Adds or updates an assembly object
    * @param command the command.
    */
   private processAddVSObjectCommand(command: AddVSObjectCommand): void {
      this.applyAddVSObjectCommand(command);
   }

   private applyAddVSObjectCommand(command: AddVSObjectCommand): void {
      let updated: boolean = false;

      for(let i = 0; i < this.vs.vsObjects.length; i++) {
         if(this.vs.vsObjects[i].absoluteName == command.name) {
            updated = true;
            this.replaceObject(command.model, i);
         }
      }

      if(!updated) {
         this.vs.vsObjects.push(command.model);
         this.vs.variableNames = VSUtil.getVariableList(this.vs.vsObjects, null);
         this.vs.vsObjects.forEach((vsObject, idx) => {
            if(this.vs.currentFocusedAssemblies.length > 0 && vsObject.absoluteName ==
               this.vs.currentFocusedAssemblies[0].absoluteName)
            {
               this.vs.clearFocusedAssemblies();
               this.vs.selectAssembly(vsObject);
            }
         });
      }

      // Update z-indexes
      for(let obj of this.vs.vsObjects) {
         this.composerObjectService.updateLayerMovement(this.vs, obj);
      }

      for(let i = 0; i < this.vs.currentFocusedAssemblies.length; i++) {
         if(this.vs.currentFocusedAssemblies[i].absoluteName == command.model.absoluteName) {
            this.vs.currentFocusedAssemblies[i] = command.model;
            this.vs.focusedAssembliesChanged();
         }
      }

      let object: any = command.model;

      if(object.adhocFilter && !object.container) {
         this.vs.selectAssembly(command.model);
      }

      // Bug #56350, for non-period calendars with no range set, refresh the range from the data
      VSUtil.refreshCalendarRanges(command.model, this.vs.runtimeId, this.vs.socketConnection);
   }

   /**
    * Refresh the assembly after changed from binding pane. Because assembly name
    * maybe changed if assembly type is changed in vs wizard, so here we need to refresh
    * with the latest assembly name.
    */
   processAssemblyChangedCommand(command: AssemblyChangedCommand): void {
      if(!command) {
         return;
      }

      let event: RefreshVsAssemblyEvent = {
         vsRuntimeId: this.vs?.runtimeId,
         assemblyName: command.name
      };

      this.vs.socketConnection.sendEvent("/events/vs/refresh/assembly", event);
   }

   /**
    * Updates the z-indexes of the listed assemblies.
    * @param command the command.
    */
   private processUpdateZIndexesCommand(command: UpdateZIndexesCommand): void {
      let updatedObjects: VSObjectModel[] = [];

      for(let i = 0; i < command.assemblies.length; i++) {
         let object: VSObjectModel = this.vs.vsObjects
            .find((obj) => obj.absoluteName === command.assemblies[i]);

         if(object) {
            object.objectFormat.zIndex = command.zIndexes[i];
            updatedObjects.push(object);
         }
      }

      for(let object of updatedObjects) {
         this.composerObjectService.updateLayerMovement(this.vs, object);
      }
   }

   /**
    * Remove an assembly object
    * @param command the command.
    */
   private processRemoveVSObjectCommand(command: RemoveVSObjectCommand): void {
      this.removeVSObject(command.name);
   }

   removeVSObject(absoulateName: string) {
      this.composerObjectService.removeObjectFromList(this.vs, absoulateName);
      this.dialogService.objectDelete(absoulateName);
      this.dataTipService.clearDataTips(absoulateName);
      this.viewsheetClient.sendEvent("/events/vs/bindingtree/gettreemodel", new RefreshBindingTreeEvent(null));

      // Update z-indexes
      for(let object of this.vs.vsObjects) {
         this.composerObjectService.updateLayerMovement(this.vs, object);
      }
   }

   /**
    * Refresh an assembly object
    * @param command the command.
    */
   private processRefreshVSObjectCommand(command: RefreshVSObjectCommand): void {
      this.applyRefreshVSObjectCommand(command);
   }

   private applyRefreshVSObjectCommand(command: RefreshVSObjectCommand): void {
      let updated: boolean = false;

      for(let i = 0; i < this.vs.vsObjects.length; i++) {
         if(this.vs.vsObjects[i].absoluteName === command.info.absoluteName) {
            this.replaceObject(command.info, i);
            updated = true;
            break;
         }
      }

      for(let i = 0; i < this.vs.currentFocusedAssemblies.length; i++) {
         if(this.vs.currentFocusedAssemblies[i].absoluteName == command.info.absoluteName) {
            this.vs.currentFocusedAssemblies[i] = command.info;
            this.vs.focusedAssembliesChanged();
         }
      }

      // maxmode will be reset after binding finish. (59945)
      if(this.maxModeAssembly && this.maxModeAssembly == command.info.absoluteName) {
         const maxMode = (<any> command.info).maxMode;

         if(maxMode != null && !maxMode) {
            this.maxModeAssembly = null;
         }
      }

      if(command.info.objectType == "VSGroupContainer") {
         this.refreshGroupContainerOrder(command.info);
      }

      if(!updated) {
         this.vs.vsObjects.push(command.info);
         this.vs.variableNames = VSUtil.getVariableList(this.vs.vsObjects, null);
      }

      // Update z-indexes
      for(let object of this.vs.vsObjects) {
         this.composerObjectService.updateLayerMovement(this.vs, object);
      }
   }

   /**
    * fix Bug #25775, after doing group action, change group container's order
    * in the vsobject array in front of its child assemblies.
    */
   private refreshGroupContainerOrder(model: VSObjectModel): void {
      let objectsCopy: VSObjectModel[] = [];

      this.vs.vsObjects.forEach((vsObject, idx) => {
         if(vsObject.container == model.absoluteName &&
            objectsCopy.indexOf(model) == -1)
         {
            objectsCopy.push(model);
         }

         if(objectsCopy.indexOf(vsObject) == -1) {
            objectsCopy.push(vsObject);
         }
      });

      this.vs.vsObjects = objectsCopy;
   }

   /**
    * Rename an assembly object
    * @param command the command.
    */
   private processRenameVSObjectCommand(command: RenameVSObjectCommand): void {
      this.uiContextService.objectRenamed(command.oldName);
      this.dialogService.objectRename(command.oldName, command.newName);

      for(let i = 0; i < this.vs.vsObjects.length; i++) {
         if(this.vs.vsObjects[i].absoluteName === command.oldName) {
            this.vs.vsObjects[i].absoluteName = command.newName;
            break;
         }
      }

      this.vs.variableNames = VSUtil.getVariableList(this.vs.vsObjects, null);
   }

   /**
    * Retrieve object tree for the component tree in the side bar.
    * @param {PopulateVSObjectTreeCommand} command
    */
   private processPopulateVSObjectTreeCommand(command: PopulateVSObjectTreeCommand): void {
      this.vs.objectTree = command.tree;
   }

   /**
    * Retrieve binding tree.
    * @param {RefreshBindingTreeCommand} command
    */
   protected processRefreshBindingTreeCommand(command: RefreshBindingTreeCommand): void {
      if(this.treeService == null) {
         return;
      }

      this.treeService.resetTreeModel(command.treeModel, false);
   }

   /**
    * Retrieve current format to display in toolbar.
    * @param {SetCurrentFormatCommand} command
    */
   protected processSetCurrentFormatCommand(command: SetCurrentFormatCommand): void {
      // reset would return a null model, just keep the current
      if(command.model) {
         this.vs.currentFormat = command.model;
         this.vs.origFormat = Tool.clone(command.model);
      }
   }

   /**
    * Used for opening a preview for a print layout.
    *
    * @param command the command.
    */
   processExportVSCommand(command: ExportVSCommand): void {
      this.downloadService.download(this.vs.linkUri + "reports" + command.url);
   }

   private processMessageCommand(command: MessageCommand): void {
      if(command.message && command.type == "OK") {
         this.notifications.success(command.message);
      }
      else if(command.message && (command.type == "TRACE" || command.type == "DEBUG" ||
              command.type == "INFO"))
      {
         if(Tool.shouldIgnoreMessage(this.textLimitConfirmed, this.columnLimitConfirmed,
            command.message))
         {
            this.addConsoleMessage(command);
            return;
         }

         this.textLimitConfirmed = Tool.getTextLimit(this.textLimitConfirmed, command.message);
         this.columnLimitConfirmed = Tool.getColumnLimit(this.columnLimitConfirmed, command.message);
         command.message = Tool.getLimitedMessage(command.message);
         this.notifications.info(command.message);
      }
      else {
         this.processMessageCommand0(command, this.modalService, this.viewsheetClient);
      }

      this.addConsoleMessage(command);
   }

   private addConsoleMessage(command: MessageCommand): void {
      if(!!command.message && (command.type == "INFO" || command.type == "WARNING" ||
         command.type == "ERROR"))
      {
         this.consoleMessages.push({
            message: command.message,
            type: command.type
         });
      }
   }

   protected processProgress(command: MessageCommand): void {
      // close progress dialog when mv data is created.
      if(!command.message) {
         this.closeProgressSubject.next(null);
         return;
      }

      let checkMv: boolean = false;

      if(command.events) {
         for(let key in command.events) {
            if(command.events.hasOwnProperty(key)) {
               if(key && key.toLowerCase().indexOf("checkmv") != -1) {
                  checkMv = true;
               }

               let evt: any = command.events[key];
               this.viewsheetClient.sendEvent(key, evt);
            }
         }
      }

      if(checkMv) {
         this.showProgressDialog(
            command, "_#(js:Loading)", {"background": "_#(js:em.mv.background)", "cancel": "_#(js:Cancel)"});
      }
   }

   private showProgressDialog(command: MessageCommand, title: string,
                              buttonOptions: {[key: string]: string}): void
   {
      ComponentTool.showMessageDialog(this.modalService, title, command.message,
         buttonOptions, {backdrop: "static" }, this.closeProgressSubject)
         .then((btn: any) => {
            for(let key in command.events) {
               if(command.events.hasOwnProperty(key)) {
                  let evt: any = Tool.clone(command.events[key]);

                  if(btn == "background") {
                     evt.background = true;
                     this.vs.loading = false;
                  }

                  evt.confirmed = true;
                  this.viewsheetClient.sendEvent(key, evt);

                  if(btn == "background") {
                     let evt0: any = command.events[key];
                     evt0.waitFor = true;
                     this.viewsheetClient.sendEvent(key, evt0);
                  }
               }
            }
         })
         .catch(() => {});
   }

   private processVSTrapCommand(command: MessageCommand): void {
      ComponentTool.showTrapAlert(this.modalService, false).then((result: string) => {
         if(result == "yes") {
            for(let key in command.events) {
               if(command.events.hasOwnProperty(key)) {
                  let evt: any = command.events[key];
                  evt.confirmed = true;
                  this.viewsheetClient.sendEvent(key, evt);
               }
            }
         }
      });
   }

   private processSetGrayedOutFieldsCommand(command: SetGrayedOutFieldsCommand): void {
      this.onGrayedOutFields.emit(command.fields);
   }

   private confirm(text: string): Promise<boolean> {
      return ComponentTool.showConfirmDialog(this.modalService, "_#(js:Confirm)", text,
         {"yes": "_#(js:Yes)", "no": "_#(js:No)"})
         .then((result: string) => result === "yes")
         .catch(() => false);
   }

   /**
    * Drop event handler. Will create and place new objects in the viewsheet composer.
    * @param event drag event
    */
   @HostListener("drop", ["$event"])
   drop(event: DragEvent): void {
      event.preventDefault();
      setTimeout(() => this.currentSnapGuides = null, 0);

      this.objectDraggedIn = "";
      let box: ClientRect = this.element.nativeElement.getBoundingClientRect();
      // subtract the ruler
      let left: number = (event.pageX - box.left - 18 + this.vsPane.nativeElement.scrollLeft)
         * (1 / this.vs.scale);
      let top: number = (event.pageY - box.top - 18 + this.vsPane.nativeElement.scrollTop)
         * (1 / this.vs.scale);
      let data: any = null;

      try {
         data = JSON.parse(event.dataTransfer.getData("text"));
      }
      catch(e) {
         console.warn("Invalid drop event on viewsheet pane: ", e);
         return;
      }

      if(!data || !data.dragName) {
         console.warn("Invalid drop event on viewsheet pane: ", event);
         return;
      }

      const dragName = data.dragName[0];

      if(dragName === "dragchart" || dragName === "dragcrosstab" ||
         dragName === "dragfreehandtable" || dragName === "dragtable")
      {
         this.notifications.success("_#(js:viewer.viewsheet.notification.dropDataViewOver)");
      }

      const vsevent: Point = this.interactContainer.snap(new Point(left, top));

      // Account for selection-border offset in initial position
      if(this.snapToGrid) {
         vsevent.x += this.selectionBorderOffset;
         vsevent.y += this.selectionBorderOffset;
      }

      if(dragName == "tableBinding") {
         // Binding data dragged from a selection container child component
         if(data.container) {
            this.composerObjectService.moveFromContainer(
               this.vs, <VSObjectModel>({absoluteName: data.objectName,
                                         objectFormat: {left: vsevent.x, top: vsevent.y}}));
            return;
         }

         // Binding data dragged from a table component
         this.composerObjectService.applyChangeBinding(
            this.viewsheetClient, null, null, vsevent, data.dragSource);
         return;
      }

      let bindings: AssetEntry[] = null;

      if(this.isTargetVSPane(event) || this.isTargetShape(event)) {
         bindings = this.composerObjectService.getDataSource(data);
      }

      if(!bindings) {
         let entry: AssetEntry[] = null;

         if(data.viewsheet) {
            entry = data.viewsheet;

            if(entry.length > 0 && entry[0].identifier == this.vs.id) {
               // if trying to add self into vs, show error dialog
               ComponentTool.showMessageDialog(this.modalService, "_#(js:Error)",
                  "_#(js:common.selfUseForbidden)");
               return;
            }
         }

         const x = Math.max(0, vsevent.x);
         const y = Math.max(0, vsevent.y);
         this.composerObjectService.addNewObject(this.vs, dragName, x, y,
            entry && entry.length > 0 ? entry[0] : null);
         return;
      }

      // Don't do anything with cube measure types and aggregate column
      for(let binding of bindings) {
         if(binding.type === AssetType.COLUMN &&
            (parseInt(binding.properties["cube.column.type"], 10) === 1 ||
            binding.properties.isCalc == "true" &&
            binding.properties.basedOnDetail == "false"))
         {
            return;
         }
         else if((binding.type === AssetType.FOLDER &&
            binding.properties.DIMENSION_FOLDER !== "true") ||
            (binding.type === AssetType.TABLE &&
            binding.properties.CUBE_TABLE === "true"))
         {
            return;
         }
      }

      this.composerObjectService.applyChangeBinding(this.viewsheetClient, null, bindings,
         vsevent);
   }

   @HostListener("dragenter", ["$event"])
   dragenter(event: any): void {
      this.boundingBox = this.element.nativeElement.getBoundingClientRect();
      const dragData = Object.keys(this.dragService.getDragData());
      this.objectDraggedIn = dragData.find(key => key !== "worksheet");
      this.objectDraggedInSize =
         this.composerObjectService.getObjectDefaultSize(this.objectDraggedIn);
   }

   dragover(event: DragEvent): void {
      event.preventDefault();

      if(this.objectDraggedIn) {
         let top: number = event.pageY - this.boundingBox.top - 18 +
            this.vsPane.nativeElement.scrollTop;
         let left: number = event.pageX - this.boundingBox.left - 18 +
            this.vsPane.nativeElement.scrollLeft;
         this.updateDragRulerGuides(top, left);
      }
   }

   mousedown() {
      this.click = true;
   }

   // mouse up handle
   deselectObjects(event: any) {
      if(this.click && this.isTargetVSPane(event)) {
         if(!this.moving) {
            this.vs.clearFocusedAssemblies();
         }

         this.vs.formatPainterMode = false;
         this.vs.painterFormat = null;
      }

      this.click = false;
   }

   resetCursor(event: MouseEvent): void {
      const html: any = window.document.getElementsByTagName("html")[0];
      html.style.cursor = "";
   }

   onKeydown(event: KeyboardEvent) {
      // only handle key events on viewsheetpane if a modal is not open, viewsheet is not in layout,
      // and action is not text editing
      if(!this.isModalOpen() && this.vs.currentLayout == null &&
         !(event.target instanceof HTMLInputElement) &&
         !(event.target instanceof HTMLTextAreaElement))
      {
         // ctrl + a is clicked
         if(event.keyCode == 65 && (event.ctrlKey || event.metaKey)) {
            event.preventDefault();
            event.stopPropagation();
            this.vs.currentFocusedAssemblies = this.vs.vsObjects.slice();
            // prevent default action on browser
            return false;
         }
         // esc
         else if(event.keyCode == 27) {
            this.vs.clearFocusedAssemblies();
            return false;
         }
         // delete in layout mode handled by layout object itself
         else if(this.vs.isFocused && !this.vs.bindingEditMode) {
            this.composerObjectService.handleKeyEvent(event, this.snapToGrid,
                                                      this.interactContainer.snapGridSize);

            // @by changhongyang 2017-10-20, update the ruler guides when using the arrow keys to
            // move assemblies
            if(this.vs.currentFocusedAssemblies.length > 0 && (event.keyCode === 37 ||
               event.keyCode === 38 || event.keyCode === 39 || event.keyCode === 40))
            {
               this.updateRulerGuides();
               this.changeDetectorRef.detectChanges();
            }
         }
      }

      //fix IE 11 cannot Listen the "document: paste" event
      if(this.vs.isFocused && event.keyCode == 86 && event.ctrlKey &&
         !(event.target instanceof HTMLInputElement) &&
         !(event.target instanceof HTMLTextAreaElement) &&
         !this.vs.bindingEditMode)
      {
         this.onPaste.emit([this.vs, null]);
      }

      return false;
   }

   /* removed because it can be accidentally triggered too easily
   @HostListener("dblclick", ["$event"])
   dblClick(event: MouseEvent) {
      if(this.isTargetVSPane(event)) {
         event.preventDefault();
         // Add new text element.
         let box: ClientRect = this.element.nativeElement.getBoundingClientRect();
         let left: number =
            (event.pageX - box.left - 18 + this.vsPane.nativeElement.scrollLeft)
            * (1 / this.vs.scale);
         let top: number =
            (event.pageY - box.top - 18 + this.vsPane.nativeElement.scrollTop)
            * (1 / this.vs.scale);

         if(this.snapToGrid) {
            left = Math.round(left / 20) * 20;
            top = Math.round(top / 20) * 20;
         }

         this.composerObjectService
            .addNewObject(this.vs, "dragtext", left, top, null, true);
      }
   }
   */

   paste(): void {
      const box: ClientRect = this.element.nativeElement.getBoundingClientRect();
      let left: number = this.menuPosition.x - box.left - 18;
      let top: number = this.menuPosition.y - box.top - 18;
      const {scrollTop, scrollLeft} = this.vsPane.nativeElement;
      let point: Point =
         this.interactContainer.snap(new Point(left + scrollLeft, top + scrollTop));
      this.lastClick.x = left;
      this.lastClick.y = top;

      this.onPaste.emit([this.vs, point]);
      setTimeout(() => this.currentSnapGuides = null, 0);
   }

   /**
    * Check if a modal is currently open in the document by checking for the class 'modal-open'
    * that is appended to the document body when a modal is open.
    * @returns {boolean} true if a modal is open
    */
   isModalOpen(): boolean {
      return document.body.classList.contains("modal-open");
   }

   trackByFn(index: number, object: VSObjectModel) {
      return object.absoluteName;
   }

   onAssemblyActionEvent(event: AssemblyActionEvent<VSObjectModel>) {
      switch(event.id) {
      case "vs-object copy":
         this.copyAssembly(event.model);
         break;
      case "vs-object cut":
         this.cutAssembly(event.model);
         break;
      case "vs-object remove":
         this.removeAssembly(event.model);
         break;
      case "vs-object bring-to-front":
         this.bringAssemblyToFront(event.model);
         break;
      case "vs-object bring-forward":
         this.bringAssemblyForward(event.model);
         break;
      case "vs-object send-to-back":
         this.sendAssemblyToBack(event.model);
         break;
      case "vs-object send-backward":
         this.sendAssemblyBackward(event.model);
         break;
      default:
         let editedByWizard: boolean = event.model.objectType == "VSChart"
            && ((<VSChartModel> event.model).editedByWizard
            || event.model.sourceType == SourceInfoType.NONE);

         if(event.id.match(/^.+ edit$/) && !editedByWizard) {
            this.onOpenEditPane.emit(event.model);
         }
      }
   }

   copyAssembly(model: VSObjectModel): void {
      this.vs.selectAssembly(model);
      this.onCopy.emit(model);
   }

   cutAssembly(model: VSObjectModel): void {
      this.vs.selectAssembly(model);
      this.onCut.emit(model);
   }

   removeAssembly(model: VSObjectModel): void {
      this.vs.selectAssembly(model);
      this.onRemove.emit(model);
   }

   bringAssemblyToFront(model: VSObjectModel): void {
      this.onBringToFront.emit(model);
   }

   bringAssemblyForward(model: VSObjectModel): void {
      this.onBringForward.emit(model);
   }

   sendAssemblyToBack(model: VSObjectModel): void {
      this.onSendToBack.emit(model);
   }

   sendAssemblyBackward(model: VSObjectModel): void {
      this.onSendBackward.emit(model);
   }

   assemblyResized(event: any, model: VSObjectModel) {
      let bottom = false;
      let right = false;
      let top = false;
      let left = false;
      let deltaX = 0;
      let deltaY = 0;
      let deltaWidth = 0;
      let deltaHeight = 0;

      if(event && event.edges) {
         bottom = event.edges.bottom;
         right = event.edges.right;
         top = event.edges.top;
         left = event.edges.left;
      }

      if(event && event.deltaRect) {
         deltaX = event.deltaRect.left || 0;
         deltaY = event.deltaRect.top || 0;
         deltaWidth = event.deltaRect.width || 0;
         deltaHeight = event.deltaRect.height || 0;
      }

      this.processAssemblyResize(
         model, bottom, right, top, left, deltaX, deltaY, deltaWidth, deltaHeight);
   }

   private processAssemblyResize(model: VSObjectModel, atBottom: boolean, atRight: boolean,
                                 atTop: boolean, atLeft: boolean, deltaX: number, deltaY: number,
                                 deltaWidth: number, deltaHeight: number)
   {
      if(this.resizeTimeout) {
         clearTimeout(this.resizeTimeout);
         this.resizeTimeout = null;
      }

      if(model) {
         const left = model.objectFormat.left;
         const top = model.objectFormat.top;
         const right = model.objectFormat.left + model.objectFormat.width;
         const bottom = model.objectFormat.top + model.objectFormat.height;
         const {clientWidth, clientHeight, scrollLeft, scrollTop} = this.vsPane.nativeElement;

         // Bug #31065. Resize should only occur when the mouse is pressed(dragging).
         if(atRight && this.click && clientWidth + scrollLeft - right <= 10 && deltaWidth > 0) {
            this.vsPane.nativeElement.scrollLeft = Math.max(0, right - clientWidth);
            this.resizeTimeout = setTimeout(() => {
               this.resizeTimeout = null;
               model.objectFormat.width += 10;
               this.processAssemblyResize(model, atBottom, atRight, atTop, atLeft, 0, 0, 1, 0);
            }, 100);
         }

         if(atRight && this.click && scrollLeft >= right - 10 && deltaWidth < 0) {
            this.vsPane.nativeElement.scrollLeft = Math.max(0, right - 10);
            this.resizeTimeout = setTimeout(() => {
               this.resizeTimeout = null;
               model.objectFormat.width = Math.max(1, model.objectFormat.width - 10);
               this.processAssemblyResize(model, atBottom, atRight, atTop, atLeft, 0, 0, -1, 0);
            }, 100);
         }

         if(atLeft && this.click && scrollLeft >= left - 10 && deltaX < 0) {
            this.vsPane.nativeElement.scrollLeft = Math.max(0, left - 10);
            this.resizeTimeout = setTimeout(() => {
               this.resizeTimeout = null;
               const delta = model.objectFormat.left <= 10 ? model.objectFormat.left - 1 : 10;
               model.objectFormat.left -= delta;
               model.objectFormat.width += delta;
               this.processAssemblyResize(model, atBottom, atRight, atTop, atLeft, -1, 0, 1, 0);
            }, 100);
         }

         if(atLeft && this.click && clientWidth + scrollLeft - left <= 10 && deltaX > 0) {
            this.vsPane.nativeElement.scrollLeft = this.vsPane.nativeElement.scrollLeft + 10;
            this.resizeTimeout = setTimeout(() => {
               this.resizeTimeout = null;
               const delta = model.objectFormat.width <= 10 ? model.objectFormat.width - 1 : 10;
               model.objectFormat.left += delta;
               model.objectFormat.width -= delta;
               this.processAssemblyResize(model, atBottom, atRight, atTop, atLeft, 1, 0, -1, 0);
            }, 100);
         }

         if(atBottom && this.click && clientHeight + scrollTop - bottom <= 10 && deltaHeight > 0) {
            this.vsPane.nativeElement.scrollTop = Math.max(0, bottom - clientHeight);
            this.resizeTimeout = setTimeout(() => {
               this.resizeTimeout = null;
               model.objectFormat.height += 10;
               this.processAssemblyResize(model, atBottom, atRight, atTop, atLeft, 0, 0, 0, 1);
            }, 100);
         }

         if(atBottom && this.click && scrollTop >= bottom - 10 && deltaHeight < 0) {
            this.vsPane.nativeElement.scrollTop = Math.max(0, bottom - 10);
            this.resizeTimeout = setTimeout(() => {
               this.resizeTimeout = null;
               model.objectFormat.height = Math.max(1, model.objectFormat.height - 10);
               this.processAssemblyResize(model, atBottom, atRight, atTop, atLeft, 0, 0, 0, -1);
            }, 100);
         }

         if(atTop && this.click && scrollTop >= top - 10 && deltaY < 0) {
            this.vsPane.nativeElement.scrollTop = Math.max(0, top - 10);
            this.resizeTimeout = setTimeout(() => {
               this.resizeTimeout = null;
               const delta = model.objectFormat.top <= 10 ? model.objectFormat.top - 1 : 10;
               model.objectFormat.top -= delta;
               model.objectFormat.height += delta;
               this.processAssemblyResize(model, atBottom, atRight, atTop, atLeft, 0, -1, 0, 1);
            }, 100);
         }

         if(atTop && this.click && clientHeight + scrollTop - top <= 10 && deltaY > 0) {
            this.vsPane.nativeElement.scrollTop = this.vsPane.nativeElement.scrollTop + 10;
            this.resizeTimeout = setTimeout(() => {
               this.resizeTimeout = null;
               const delta = model.objectFormat.height <= 10 ? model.objectFormat.height - 1 : 10;
               model.objectFormat.top += delta;
               model.objectFormat.height -= delta;
               this.processAssemblyResize(model, atBottom, atRight, atTop, atLeft, 0, 1, 0, -1);
            }, 100);
         }
      }

      this.updateRulerGuides();
   }

   assemblyMoved(event: any, model: VSObjectModel): void {
      let down = false;
      let right = false;
      let offsetX = 0;
      let offsetY = 0;

      if(event) {
         let pointerDeltaX = 0;
         let pointerDeltaY = 0;

         if(event.interaction && event.interaction.pointerDelta) {
            pointerDeltaX = event.interaction.pointerDelta.client.x;
            pointerDeltaY = event.interaction.pointerDelta.client.y;
         }

         right = event.dx > 0 || (event.dx === 0 && pointerDeltaX > 0);
         down = event.dy > 0 || (event.dy === 0 && pointerDeltaY > 0);

         if(event.interaction && event.interaction.downEvent) {
            offsetX = event.interaction.downEvent.offsetX || 0;
            offsetY = event.interaction.downEvent.offsetY || 0;
         }
      }

      this.processAssemblyMoved(model, right, down, !right, !down, offsetX, offsetY);
   }

   private processAssemblyMoved(model: VSObjectModel, moveRight: boolean, moveDown: boolean,
                                moveLeft: boolean, moveUp: boolean,
                                offsetX: number, offsetY: number): void
   {
      if(this.moveTimeout) {
         clearTimeout(this.moveTimeout);
         this.moveTimeout = null;
      }

      if(model) {
         this.moving = true;
         let right = model.objectFormat.left + model.objectFormat.width;
         let bottom = model.objectFormat.top + model.objectFormat.height;
         let left = model.objectFormat.left;
         let top = model.objectFormat.top;

         // calculate right, bottom, left, top based on all selected assemblies
         this.vs.currentFocusedAssemblies
            .filter(a => !a.interactionDisabled && a !== model)
            .forEach((assembly) => {
               const right2 = assembly.objectFormat.left + assembly.objectFormat.widht;
               const bottom2 = assembly.objectFormat.top + assembly.objectFormat.height;
               const left2 = assembly.objectFormat.left;
               const top2 = assembly.objectFormat.top;
               right = right2 > right ? right2 : right;
               bottom = bottom2 > bottom ? bottom2 : bottom;
               left = left2 < left ? left2 : left;
               top = top2 < top ? top2 : top;
            });

         const {clientWidth, clientHeight, scrollLeft, scrollTop} = this.vsPane.nativeElement;
         const autoRight = moveRight && clientWidth + scrollLeft < right + 15;
         const autoDown = moveDown && clientHeight + scrollTop < bottom + 15;
         const autoLeft = moveLeft && scrollLeft >= left - 15 && scrollLeft > 0;
         const autoUp = moveUp && scrollTop >= top - 15 && scrollTop > 0;

         if(autoRight || autoDown || autoLeft || autoUp) {
            this.moveTimeout = setTimeout(() => {
               this.moveTimeout = null;

               if(autoRight) {
                  this.vsPane.nativeElement.scrollLeft =
                     Math.max(0, this.vsPane.nativeElement.scrollLeft + 10);
               }
               else if(autoLeft) {
                  this.vsPane.nativeElement.scrollLeft =
                     Math.max(0, this.vsPane.nativeElement.scrollLeft - 10);
               }

               if(autoDown) {
                  this.vsPane.nativeElement.scrollTop =
                     Math.max(0, this.vsPane.nativeElement.scrollTop + 10);
               }
               else if(autoUp) {
                  this.vsPane.nativeElement.scrollTop =
                     Math.max(0, this.vsPane.nativeElement.scrollTop - 10);
               }

               // move each selected assembly in the same direction
               this.vs.currentFocusedAssemblies
                  .filter(a => !a.interactionDisabled)
                  .map(a => a.dragObj ? a.dragObj : a)
                  .forEach((assembly) => {
                     if(autoRight) {
                        assembly.objectFormat.left += 10;
                     }
                     else if(autoLeft) {
                        assembly.objectFormat.left -= 10;
                     }

                     if(autoDown) {
                        assembly.objectFormat.top += 10;
                     }
                     else if(autoUp) {
                        assembly.objectFormat.top -= 10;
                     }

                     this.processAssemblyMoved(assembly, autoRight, autoDown, autoLeft,
                        autoUp, offsetX, offsetY);
                  });
            }, 50);
         }
      }
      else {
         this.moving = false;
      }

      this.updateRulerGuides();
   }

   updateRulerGuides(): void {
      this.rulerGuidesVisible = false;
      this.rulerGuideTop = 0;
      this.rulerGuideLeft = 0;
      this.rulerGuideWidth = 0;
      this.rulerGuideHeight = 0;

      if(this.vs.currentFocusedAssemblies.length > 0 ||
         (!!this.vs.currentLayout && !!this.vs.currentLayout.focusedObjects &&
            this.vs.currentLayout.focusedObjects.length > 0))
      {
         let top: number = Number.MAX_VALUE;
         let left: number = Number.MAX_VALUE;
         let bottom: number = Number.MIN_VALUE;
         let right: number = Number.MIN_VALUE;
         const scale: number = this.vs.scale ? this.vs.scale : 1;

         if(!!this.vs.currentLayout) {
            this.vs.currentLayout.focusedObjects.forEach((layoutObj: VSLayoutObjectModel) => {
               top = Math.min(top, layoutObj.top * scale);
               left = Math.min(left, layoutObj.left * scale);
               bottom = Math.max(bottom, (layoutObj.top + layoutObj.height) * scale);
               right = Math.max(right, (layoutObj.left + layoutObj.width) * scale);
            });
         }
         else if(this.vs.currentFocusedAssemblies.length > 0) {
            this.vs.currentFocusedAssemblies.forEach((assembly: VSObjectModel) => {
               const format = assembly.objectFormat;

               if(format) {
                  top = Math.min(top, format.top * scale);
                  left = Math.min(left, format.left * scale);
                  bottom = Math.max(bottom, (format.top + format.height) * scale);
                  right = Math.max(right, (format.left + format.width) * scale);
               }
            });
         }

         this.rulerGuidesVisible = true;
         this.rulerGuideTop = top;
         this.rulerGuideLeft = left;
         this.rulerGuideWidth = right - left;
         this.rulerGuideHeight = bottom - top;
      }
   }

   updateDragRulerGuides(top: number, left: number): void {
      this.rulerGuidesVisible = true;
      const diff = Math.abs(this.rulerGuideTop - top) + Math.abs(this.rulerGuideLeft - left);
      this.rulerGuideTop = top;
      this.rulerGuideLeft = left;

      if(this.objectDraggedInSize) {
         this.rulerGuideWidth = this.objectDraggedInSize.width;
         this.rulerGuideHeight = this.objectDraggedInSize.height;
      }

      if(diff > 10) {
         this.debounceService.debounce("updateDragRulerGuides", () => {
            this.changeDetectorRef.detectChanges();
         }, 300, []);
      }
   }

   private updateSnapGuides(): void {
      this.draggableSnapGuides.horizontal = [];
      this.draggableSnapGuides.vertical = [];
      const {horizontal, vertical} = this.draggableSnapGuides;

      this.vs.vsObjects.forEach((assembly) => {
         if(!this.vs.currentFocusedAssemblies.some(
               (focused) => focused.absoluteName === assembly.absoluteName ||
                  focused.absoluteName === assembly.container))
         {
            let {top, left} = assembly.objectFormat;
            let width = LayoutUtil.getWidth(assembly);
            let height = LayoutUtil.getHeight(assembly);

            if(assembly.objectType === "VSViewsheet") {
               const vs = <VSViewsheetModel> assembly;
               top += vs.iconHeight + 5; // +5 is a magic number from the vs-viewsheet template
               width = vs.bounds.width;
               height = vs.bounds.height;
            }
            else if(assembly.objectType === "VSLine") {
               const line = <VSLineModel> assembly;
               top = Math.min(line.startTop, line.endTop);
               left = Math.min(line.startLeft, line.endLeft);
               width = Math.max(line.startLeft, line.endLeft) - left;
               height = Math.max(line.startTop, line.endTop) - top;
            }

            vertical.push(left);
            vertical.push(left + width);
            horizontal.push(top);
            horizontal.push(top + height);

            // mid points marked as negative value
            if(width >= 20) {
               vertical.push(-(left + width / 2));
            }

            if(height >= 10) {
               horizontal.push(-(top + height / 2));
            }
         }
      });

      horizontal.sort((a, b) => a - b);

      for(let i = 1; i < horizontal.length; i++) {
         if(horizontal[i] === horizontal[i - 1]) {
            horizontal.splice(i, 1);
            i -= 1;
         }
      }

      vertical.sort((a, b) => a - b);

      for(let i = 1; i < vertical.length; i++) {
         if(vertical[i] === vertical[i - 1]) {
            vertical.splice(i, 1);
            i -= 1;
         }
      }

      this.domService.requestRead(() => {
         const activeElement = this.vsPane?.nativeElement?.querySelector(".active .object");

         if(activeElement) {
            this.snapOffset = (activeElement.offsetWidth - activeElement.clientWidth) / 2;
         }
         else {
            this.snapOffset = 0;
         }
      });
   }

   onSnap(snap: {x: number, y: number}): void {
      this.currentSnapGuides = snap;
   }

   @HostListener("click", ["$event"])
   clickEvent(event?: MouseEvent) {
      this.updateFormats(event);
   }

   updateFormats(event?: MouseEvent, targetObj?: VSObjectModel) {
      if(!this.vs.runtimeId) {
         return;
      }

      if(event) {
         let box: ClientRect = this.element.nativeElement.getBoundingClientRect();
         let left: number = event.pageX - box.left;
         let top: number = event.pageY - box.top;

         this.lastClick.x = left;
         this.lastClick.y = top;
         this.vsScroll.x = this.vsPane.nativeElement.scrollLeft;
         this.vsScroll.y = this.vsPane.nativeElement.scrollTop;
      }

      if(this.vs.formatPainterMode && !this.vs.painterFormat && !!this.vs.currentFormat) {
         this.vs.painterFormat = this.vs.currentFormat;
      }

      if(this.vs.formatPainterMode && event && targetObj && targetObj.objectType !== "VSViewsheet") {
         let vsevent: FormatVSObjectEvent = VSUtil.prepareFormatEvent([targetObj]);
         vsevent.format = this.vs.painterFormat;
         vsevent.origFormat = null;
         vsevent.copyFormat = true;
         this.vs.socketConnection.sendEvent("/events/composer/viewsheet/format", vsevent);
      }
      else {
         let object: VSObjectModel = this.vs.currentFocusedAssemblies[0];
         let vsevent: GetVSObjectFormatEvent = VSUtil.prepareGetFormatEvent(object);
         this.vs.socketConnection.sendEvent("/events/composer/viewsheet/getFormat", vsevent);
         this.refreshStatus();
      }
   }

   private isTargetVSPane(event: any): boolean {
      return !this.vsPane || event && event.target &&
         event.target.parentElement == this.vsPane.nativeElement;
   }

   // check if drop target is a shape or image
   private isTargetShape(event: Event): boolean {
      if(!event.target) {
         return false;
      }

      const classList = (<Element> event.target).classList;
      return (classList && (classList.contains("vs-rectangle__box-shadow") ||
         classList.contains("image-content") ||
         classList.contains("line-resize-container"))) ||
         (<Element> event.target).nodeName === "ellipse";
   }

   private replaceObject(newModel: VSObjectModel, index: number): void {
      this.vs.vsObjects[index] = VSUtil.replaceObject(this.vs.vsObjects[index], newModel);
      this.vs.updateSelectedAssembly(this.vs.vsObjects[index]);
      this.refreshStatus();
   }

   get layoutToolbarVisible(): boolean {
      return this.vs.layouts && this.vs.layouts.length > 1;
   }

   get mobileToolbarVisible(): boolean {
      return this.touchDevice && this.vs.currentFocusedAssemblies &&
         this.vs.currentFocusedAssemblies.length > 0 && this.mobileDevice;
   }

   layoutChanged(name: string, switchPane: boolean = true): void {
      if(switchPane) {
         this.vs.layoutPoint = -1;
         this.vs.layoutPoints = 0;
      }

      // Bug #44789. Don't change it direct, because need reset check point in
      // changeLayout event controller. this will changed by UpdateLayoutCommand
      // this.viewsheetClient.focusedLayoutName = name;
      this.viewsheetClient.sendEvent("/events/composer/vs/changeLayout/" + name);

      setTimeout(() => {
         if(this.vsPane && this.vsPane.nativeElement) {
            this.vsPane.nativeElement.scrollLeft = 0;
            this.vsPane.nativeElement.scrollTop = 0;
            this.containerView.scrollTop = 0;
         }
      }, 0);
   }

   get layoutName(): string {
      return this.vs.currentLayout ? this.vs.currentLayout.name : "_#(js:Master)";
   }

   private touchAsset() {
      if(this.vs.runtimeId) {
         let event = new TouchAssetEvent();
         event.setDesign(true);
         event.setChanged(this.vs.isModified() && this.vs.autoSaveTS != this.vs.currentTS);
         event.setUpdate(false);
         this.vs.autoSaveTS = this.vs.currentTS;
         this.vs.socketConnection.sendEvent("/events/composer/touch-asset", event);
      }
   }

   openEmbeddedViewsheet(assetId: string): void {
      this.onOpenEmbeddedViewsheet.emit(assetId);
   }

   openEditPane(model: VSObjectModel): void {
      this.onOpenEditPane.emit(model);
      this.refreshSubscription.unsubscribe();
      this.viewsheetClient.sendEvent("/events/vs/binding/open/" + model.absoluteName);
   }

   openWizardPane(evt: any) {
      evt = !!evt ? evt : {};
      evt.linkUri = this.vs.linkUri;
      evt.runtimeId = this.vs.runtimeId;
      evt.assetId = this.vs.id;
      this.onOpenWizardPane.emit(evt);
   }

   onSelectionBox(event: SelectionBoxEvent) {
      let scaledBox = event.box;

      if(this.vs.scale != 1) {
         scaledBox = new Rectangle(scaledBox.x * (1 / this.vs.scale),
                                   scaledBox.y * (1 / this.vs.scale),
                                   scaledBox.width * (1 / this.vs.scale),
                                   scaledBox.height * (1 / this.vs.scale));
      }

      const selectedAssemblies = this.vs.vsObjects.filter((vsObject) => {
         if(vsObject.container && !vsObject.active) {
            return false;
         }

         const format = vsObject.objectFormat;
         let vsObjectRect = (vsObject.objectType === "VSViewsheet")
            ? new Rectangle(format.left, format.top,
                            (<VSViewsheetModel> vsObject).bounds.width,
                            (<VSViewsheetModel> vsObject).bounds.height)
            : new Rectangle(format.left, format.top, format.width, format.height);

         if(vsObject.objectType === "VSLine") {
            const lineObj: VSLineModel = vsObject as VSLineModel;
            const x: number = lineObj.objectFormat.left;
            const y: number = lineObj.objectFormat.top;
            const start: Point = new Point(lineObj.startLeft + x, lineObj.startTop + y);
            const end: Point = new Point(lineObj.endLeft + x, lineObj.endTop + y);
            const line: Line = new Line(start, end);
            return line.intersectsRect(scaledBox);
         }
         else {
            return vsObjectRect.intersects(scaledBox);
         }
      });

      const nameComparator = (a1, a2) => a1.absoluteName === a2.absoluteName;
      const currentFocusedAssemblies = this.vs.currentFocusedAssemblies;

      if(selectedAssemblies.length !== currentFocusedAssemblies.length &&
         Tool.intersectionWith(selectedAssemblies, currentFocusedAssemblies, nameComparator))
      {
         selectedAssemblies.forEach(assembly => {
            if(assembly.objectType == "VSGroupContainer") {
               assembly.interactionDisabled = false;
            }
         });

         this.vs.currentFocusedAssemblies = selectedAssemblies;
      }
   }

   private refreshStatus(): void {
      if(!!this.vs.currentLayout) {
         this.refreshStatusByLayout();
      }
      else {
         this.refreshStatusByVs();
      }

   }

   private refreshStatusByVs() {
      // no assembly selected
      if(this.vs.currentFocusedAssemblies.length === 0) {
         this.status = new Status(this.vs.statusText);
         this.status2 = new Status("");

         if(this.vs.baseEntry) {
            let text = this.vs.baseEntry.description;

            if(this.vs.baseEntry.alias && text) {
               const path = text.split("/");
               path[path.length - 1] = this.vs.baseEntry.alias;
               text = path.join("/");
            }

            this.status2 = new Status(text, false,
               this.vs.baseEntry.type === AssetType.WORKSHEET ?
                  () => this.openWorksheet() : null);
         }
      }
      else {
         let statusText = "";
         let status2Text = "";
         // group container format only changed if selected by itself
         const ignoreGroupContainer: boolean = this.vs.currentFocusedAssemblies.length > 1 &&
            this.vs.currentFocusedAssemblies.some(
               obj => obj.objectType != "VSGroupContainer");

         for(let vsObject of this.vs.currentFocusedAssemblies) {
            let objectStatusText = "";
            let objectStatus2Text = vsObject.absoluteName + vsObject.advancedStatus;

            if(vsObject.selectedRegions && vsObject.selectedRegions.length > 0) {
               for(let region of vsObject.selectedRegions) {
                  let text = "";

                  if((vsObject.objectType === "VSSelectionList" ||
                     vsObject.objectType === "VSSelectionTree" ||
                     vsObject.objectType === "VSRadioButton" ||
                     vsObject.objectType === "VSCheckBox") &&
                     region.type != TableDataPathTypes.TITLE)
                  {
                     if(region.path.length > 0) {
                        let levelStr = region.level > 0 ? "[" + region.level + "]" : "";
                        text = vsObject.absoluteName + " => <b>" + region.path[0] +
                           levelStr + "</b>";
                     }
                     else {
                        text = vsObject.absoluteName + " => <b>Cell</b>";
                     }
                  }
                  else if(vsObject.objectType === "VSTab") {
                     text = vsObject.absoluteName + " => <b>Active Tab</b>";
                  }
                  else {
                     text = vsObject.absoluteName + " => <b>" +
                        DataPathConstants.getTableDataPathString(region) + "</b>";
                  }

                  objectStatusText += objectStatusText === "" ? text : ", " + text;
               }
            }
            else if(vsObject.objectType === "VSChart" &&
                    ChartTool.getSelectedRegions(vsObject.chartSelection).length > 0)
            {
               objectStatusText = vsObject.absoluteName + " => <b>" +
                  ChartTool.getChartSelectionString(vsObject) + "</b>";
            }
            else if(!ignoreGroupContainer || vsObject.objectType != "VSGroupContainer") {
               objectStatusText = "<b>" + vsObject.absoluteName + "</b>";
            }

            statusText += statusText === "" ? objectStatusText : ", " + objectStatusText;
            status2Text += status2Text === "" ? objectStatus2Text : ", " + objectStatus2Text;
         }

         this.status = new Status(this.trimComma(statusText), true);
         this.status2 = new Status(this.trimComma(status2Text));
      }
   }

   private refreshStatusByLayout(): void {
      this.status = new Status(this.vs.statusText);
      let layout = this.vs.currentLayout;

      if(layout.focusedObjects.length !== 0) {
         let statusText = "";

         for(let obj of layout.focusedObjects) {
            if(!obj) {
               continue;
            }

            statusText += statusText === "" ? obj.name : ", " + obj.name;
         }

         this.status2 = new Status(this.trimComma(statusText), true);
      }
   }

   private trimComma(str: string): string {
      str = str.trim();
      return str.endsWith(",") ? str.substring(0, str.length - 1) : str;
   }

   private openWorksheet(): void {
      this.onOpenSheet.emit({
         type: "worksheet",
         assetId: this.vs.baseEntry.identifier,
      });
   }

   updateRulerPosition(): void {
      this.domService.requestRead(() => {
         this.rulerTop = this.vsPane ? this.vsPane.nativeElement.scrollTop : 0;
         this.rulerLeft = this.vsPane ? this.vsPane.nativeElement.scrollLeft : 0;
      });
   }

   getDataSourceCSSIcon(): string {
      if(!this.vs) {
         return "";
      }

      const node: TreeNodeModel = { data: this.vs.baseEntry };
      return GuiTool.getTreeNodeIconClass(node, "");
   }

   processOpenBindingPaneCommand(payload: {assemblyName: string}) {
      this.refreshSubscription.unsubscribe();
      this.onOpenEditPane.emit(this.vs.getAssembly(payload.assemblyName));
   }

   popupNotifications(warning: any) {
      if(warning.type == "info") {
         this.notifications.info(warning.msg);
      }
      else if(warning.type == "warning") {
         this.notifications.warning(warning.msg);
      }
      else if(warning.type == "danger") {
         this.notifications.danger(warning.msg);
      }
   }

   onMaxModeChange(event: {assembly: string, maxMode: boolean}): void {
      if(event.maxMode) {
         this.maxModeAssembly = event.assembly;

         // clear the selected assemblies focus, because the z-index of selected objects is top.
         if(this.vs) {
            this.vs.clearFocusedAssemblies();
         }
      }
      else {
         this.maxModeAssembly = null;
      }
   }

   openConsoleDialog(): void {
      const options: NgbModalOptions = {
         backdrop: "static",
         windowClass: "console-dialog"
      };
      this.modalService.open(this.consoleDialog, options).result
         .then((messageLevels: string[]) => {
            this.vs.messageLevels = messageLevels;
         }, () => {});
   }

   getTemplateWidth(): number {
      return this.templateWidth * this.vs.scale;
   }

   getTemplateHeight(): number {
      return this.templateHeight * this.vs.scale;
   }

   get displayPlaceholderDragElementModel(): PlaceholderDragElementModel {
      let displayModel = Tool.clone(this.placeholderDragElementModel);

      if(this.vsPane?.nativeElement && displayModel) {
         displayModel.left += this.vsPane.nativeElement.scrollLeft;
         displayModel.top += this.vsPane.nativeElement.scrollTop;
      }

      return displayModel;
   }

   openFormatPane(model: VSObjectModel): void {
      this.vs.selectAssembly(model);
      this.onOpenFormatPane.emit(model);
   }

   @HostListener("document:keyup.esc", ["$event"])
   onKeyUp($event: KeyboardEvent): void {
      if(this.composerVsSearchService.isSearchMode()) {
         this.composerVsSearchService.changeSearchMode();
      }
   }

   changeSearchMode(): void {
      this.composerVsSearchService.changeSearchMode();

      if(this.composerVsSearchService.isSearchMode()) {
         this._searchResultLabel = "0/0";
         this.searchResultCount = 0;
      }
   }

   isSearchMode(): boolean {
      return this.composerVsSearchService.isSearchMode();
   }

   search(data: string): void {
      this.composerVsSearchService.searchString = data;
      this.scrollToMatchedAssembly(this.composerVsSearchService.focusIndex);
   }

   nextFocus(): void {
      this.composerVsSearchService.nextFocus();
      this.scrollToMatchedAssembly(this.composerVsSearchService.focusIndex);
   }

   previousFocus(): void {
      this.composerVsSearchService.previousFocus();
      this.scrollToMatchedAssembly(this.composerVsSearchService.focusIndex);
   }

   scrollToMatchedAssembly(index: number): void {
      if(this.autoFocusSearchTimeout) {
         clearTimeout(this.autoFocusSearchTimeout);
      }

      let searchString = this.composerVsSearchService.searchString;
      let matchedObjects: any[];

      this.autoFocusSearchTimeout = setTimeout(() => {
         if(this.vs?.currentLayout) {
            let layout = this.vs?.currentLayout;
            let layoutObjects = layout.objects;

            if(layout.currentPrintSection == PrintLayoutSection.HEADER) {
               layoutObjects = layout.headerObjects;
            }
            else if(layout.currentPrintSection == PrintLayoutSection.FOOTER) {
               layoutObjects = layout.footerObjects;
            }

            matchedObjects = layoutObjects
               .filter(o => this.composerVsSearchService.matchName(o.name))
               .sort((a, b) => {
                  return this.compareObjectByPosition(new Point(a.left, a.top), new Point(b.left, b.top));
               });
         }
         else {
            matchedObjects = this.vs.vsObjects
               .flatMap(o => {
                  if(o.objectType == "VSSelectionContainer") {
                     let selectionContainer = <VSSelectionContainerModel> o;
                     let objs: VSObjectModel[] = [];

                     if(selectionContainer.childObjects != null) {
                        objs = [...selectionContainer.childObjects];
                     }

                     objs.push(o);

                     return objs;
                  }

                  return o;
               })
               .filter(o => this.composerVsSearchService.matchName(o.absoluteName))
               .sort((a, b) => {
                  let pintA = new Point(a.objectFormat.left, a.objectFormat.top);
                  let pintB = new Point(b.objectFormat.left, b.objectFormat.top);

                  return this.compareObjectByPosition(pintA, pintB);
               });
         }


         if(!searchString || !matchedObjects || matchedObjects.length == 0) {
            this._searchResultLabel = "0/0";
            this.searchResultCount = 0;
            return;
         }

         if(matchedObjects.length - 1 < Math.abs(index)) {
            index = index % matchedObjects.length;
         }

         if(index < 0) {
            index = matchedObjects.length + index;
         }

         let obj = matchedObjects[index];
         this.composerVsSearchService.focusAssembly(obj?.absoluteName || obj?.name);
         let rec: Rectangle;

         if(this.vs.currentLayout) {
            rec = new Rectangle(obj?.left, obj?.top, obj?.width, obj?.height);
         }
         else {
            rec = new Rectangle(obj?.objectFormat?.left, obj?.objectFormat?.top, obj?.objectFormat?.width,
               obj.objectFormat.height);
         }

         this.searchResultCount = matchedObjects.length;
         this._searchResultLabel = Math.abs(index) + 1 + "/" + this.searchResultCount;
         this.scrollToAssembly(rec);
      }, 0);
   }

   scrollToAssembly(objRectangle: Rectangle): void {
      if(!objRectangle || !this.vs) {
         return;
      }

      let paneRect = GuiTool.getElementRect(this.vsPane.nativeElement);
      let rectangle = new Rectangle(this.vsPane.nativeElement.scrollLeft, this.vsPane.nativeElement.scrollTop,
         paneRect.width, paneRect.height);
      const lastSpaceToEdge: number = 50;

      if(rectangle.y > objRectangle.y) {
         this.vsPane.nativeElement.scrollTop = objRectangle.y - lastSpaceToEdge;
      }
      else if(rectangle.y + rectangle.height < objRectangle.y + objRectangle.height) {
         this.vsPane.nativeElement.scrollTop = objRectangle.y -
            (rectangle.height - objRectangle.height - lastSpaceToEdge);
      }

      if(rectangle.x > objRectangle.x) {
         this.vsPane.nativeElement.scrollLeft = objRectangle.x - 50;
      }
      else if(rectangle.x + rectangle.width < objRectangle.x + objRectangle.width) {
         this.vsPane.nativeElement.scrollLeft = objRectangle.x -
            (rectangle.width - objRectangle.width - lastSpaceToEdge);
      }
   }

   private compareObjectByPosition(a: Point, b: Point): number {
      if(a.y > b.y) {
         return 1;
      }
      else if(a.y < b.y) {
         return -1;
      }

      if(a.x > b.x) {
         return 1;
      }
      else if(a.x < b.x) {
         return -1;
      }

      return 0;
   }

   getStatusForStatusBar(): Status {
      return this.status;
   }

   getStatus2ForStatusBar(): Status {
      return this.status2;
   }

   getSearchString(): string {
      return this.composerVsSearchService.searchString || "_#(js:Search)";
   }

   isVisible(obj: VSObjectModel): boolean {
      let show = obj.active ? true : false;

      if(this.composerVsSearchService.isSearchMode() && this.composerVsSearchService.searchString) {
         show = show || this.composerVsSearchService.assemblyVisible(obj);
      }

      return show;
   }

   searchInputKeyUp(event: KeyboardEvent) {
      if(event.code == "Enter") {
         if(event.shiftKey) {
            this.previousFocus();
         }
         else {
            this.nextFocus();
         }
      }
   }

   getSearchResultLabel(): string {
      return this._searchResultLabel;
   }

   isDefaultOrgAsset() {
      let assetEntry: AssetEntry = createAssetEntry(this.vs.id);
      return assetEntry?.organization != this.orgInfo.key;
   }
}
