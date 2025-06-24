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
import { DOCUMENT } from "@angular/common";
import { HttpClient, HttpParams } from "@angular/common/http";
import {
   AfterContentInit,
   AfterViewChecked,
   AfterViewInit,
   ChangeDetectorRef,
   Component,
   ElementRef,
   EventEmitter,
   HostListener,
   Inject,
   Injector,
   Input,
   NgZone,
   OnDestroy,
   OnInit,
   Optional,
   Output,
   QueryList,
   Renderer2,
   TemplateRef,
   ViewChild,
   ViewChildren, ViewContainerRef
} from "@angular/core";
import { DomSanitizer, SafeStyle, Title } from "@angular/platform-browser";
import { Router } from "@angular/router";
import {
   NgbDatepickerConfig,
   NgbModal,
   NgbModalOptions,
   NgbTooltipConfig
} from "@ng-bootstrap/ng-bootstrap";
import { ResizeSensor } from "css-element-queries";
import { from, Observable, of, Subject, Subscription, timer } from "rxjs";
import { map, mergeMap } from "rxjs/operators";
import {
   convertToKey,
   KEY_DELIMITER
} from "../../../../em/src/app/settings/security/users/identity-id";
import { AssetEntry, createAssetEntry } from "../../../../shared/data/asset-entry";
import { DownloadService } from "../../../../shared/download/download.service";
import { FeatureFlagsService } from "../../../../shared/feature-flags/feature-flags.service";
import { DateTypeFormatter } from "../../../../shared/util/date-type-formatter";
import { Tool } from "../../../../shared/util/tool";
import { AssemblyAction } from "../common/action/assembly-action";
import { AssemblyActionGroup } from "../common/action/assembly-action-group";
import { Dimension } from "../common/data/dimension";
import { VariableInfo } from "../common/data/variable-info";
import { VSObjectFormatInfoModel } from "../common/data/vs-object-format-info-model";
import { DndService } from "../common/dnd/dnd.service";
import { VSDndService } from "../common/dnd/vs-dnd.service";
import { CollectParametersOverEvent } from "../common/event/collect-parameters-over-event";
import { AssetLoadingService } from "../common/services/asset-loading.service";
import { FirstDayOfWeekService } from "../common/services/first-day-of-week.service";
import { FullScreenService } from "../common/services/full-screen.service";
import { OpenComposerService } from "../common/services/open-composer.service";
import { PagingControlService } from "../common/services/paging-control.service";
import { UIContextService } from "../common/services/ui-context.service";
import { ComponentTool } from "../common/util/component-tool";
import { GuiTool } from "../common/util/gui-tool";
import {
   CommandProcessor,
   StompClientService,
   ViewsheetClientService
} from "../common/viewsheet-client";
import { ClearScrollCommand } from "../common/viewsheet-client/clear-scroll-command";
import { MessageCommand } from "../common/viewsheet-client/message-command";
import { ComposerRecentService } from "../composer/gui/composer-recent.service";
import { OpenComposerCommand } from "../composer/gui/vs/command/open-composer-command";
import { UpdateZIndexesCommand } from "../composer/gui/vs/command/update-zindexes-command";
import { EditViewsheetEvent } from "../composer/gui/vs/event/edit-viewsheet-event";
import { ExpiredSheetCommand } from "../composer/gui/ws/socket/expired-sheet/expired-sheet-command";
import { TouchAssetEvent } from "../composer/gui/ws/socket/touch-asset-event";
import { EmbedErrorCommand } from "../embed/embed-error-command";
import { ChartTool } from "../graph/model/chart-tool";
import { ChartService } from "../graph/services/chart.service";
import { PageTabService } from "../viewer/services/page-tab.service";
import { ViewDataService } from "../viewer/services/view-data.service";
import { VariableInputDialogModel } from "../widget/dialog/variable-input-dialog/variable-input-dialog-model";
import { VariableInputDialog } from "../widget/dialog/variable-input-dialog/variable-input-dialog.component";
import { ExpandStringDirective } from "../widget/expand-string/expand-string.directive";
import { ActionsContextmenuComponent } from "../widget/fixed-dropdown/actions-contextmenu.component";
import { DropdownOptions } from "../widget/fixed-dropdown/dropdown-options";
import { DropdownRef } from "../widget/fixed-dropdown/fixed-dropdown-ref";
import { FixedDropdownService } from "../widget/fixed-dropdown/fixed-dropdown.service";
import { PreviousSnapshotType } from "../widget/hyperlink/previous-snapshot";
import { NotificationsComponent } from "../widget/notifications/notifications.component";
import { DebounceService } from "../widget/services/debounce.service";
import { ModelService } from "../widget/services/model.service";
import { ScaleService } from "../widget/services/scale/scale-service";
import { VSScaleService } from "../widget/services/scale/vs-scale.service";
import { ShareConfig } from "../widget/share/share-config";
import { ShareService } from "../widget/share/share.service";
import {
   DialogService,
   ViewerDialogServiceFactory
} from "../widget/slide-out/dialog-service.service";
import { SlideOutOptions } from "../widget/slide-out/slide-out-options";
import { SlideOutService } from "../widget/slide-out/slide-out.service";
import { AbstractVSActions } from "./action/abstract-vs-actions";
import { AssemblyActionFactory } from "./action/assembly-action-factory.service";
import { AddVSObjectCommand } from "./command/add-vs-object-command";
import { AnnotationChangedCommand } from "./command/annotation-changed-command";
import { ClearLoadingCommand } from "./command/clear-loading-command";
import { CollectParametersCommand } from "./command/collect-parameters-command";
import { DelayVisibilityCommand } from "./command/delay-visibility-command";
import { InitGridCommand } from "./command/init-grid-command";
import { OpenAnnotationFormatDialogCommand } from "./command/open-annotation-format-dialog-command";
import { RefreshVSObjectCommand } from "./command/refresh-vs-object-command";
import { RemoveVSObjectCommand } from "./command/remove-vs-object-command";
import { SetComposedDashboardCommand } from "./command/set-composed-dashboard-command";
import { SetCurrentFormatCommand } from "./command/set-current-format-command";
import { SetExportTypesCommand } from "./command/set-export-types-command";
import { SetPermissionsCommand } from "./command/set-permissions-command";
import { SetRuntimeIdCommand } from "./command/set-runtime-id-command";
import { SetViewsheetInfoCommand } from "./command/set-viewsheet-info-command";
import { ShowLoadingMaskCommand } from "./command/show-loading-mask-command";
import { UpdateSharedFiltersCommand } from "./command/update-shared-filters-command";
import { UpdateUndoStateCommand } from "./command/update-unto-state-command";
import {
   ComposerToken,
   ContextProvider,
   EmbedToken,
   ViewerContextProviderFactory
} from "./context-provider.service";
import { ViewsheetInfo } from "./data/viewsheet-info";
import { AnnotationFormatDialogModel } from "./dialog/annotation/annotation-format-dialog-model";
import { AnnotationFormatDialog } from "./dialog/annotation/annotation-format-dialog.component";
import { ProfilingDialog } from "./dialog/profiling-dialog.component";
import { RichTextService } from "./dialog/rich-text-dialog/rich-text.service";
import { AddAnnotationEvent } from "./event/annotation/add-annotation-event";
import { RemoveAnnotationEvent } from "./event/annotation/remove-annotation-event";
import { ToggleAnnotationStatusEvent } from "./event/annotation/toggle-annotation-status-event";
import { UpdateAnnotationFormatEvent } from "./event/annotation/update-annotation-format-event";
import { CancelViewsheetLoadingEvent } from "./event/cancel-viewsheet-loading-event";
import { DelayVisibilityEvent } from "./event/delay-visibility-event";
import { FormatVSObjectEvent } from "./event/format-vs-object-event";
import { GetVSObjectFormatEvent } from "./event/get-vs-object-format-event";
import { OpenPreviewViewsheetEvent } from "./event/open-preview-viewsheet-event";
import { OpenViewsheetEvent } from "./event/open-viewsheet-event";
import { VSChartFlyoverEvent } from "./event/vs-chart-event";
import { VsDeletedMatchedBookmarksEvent } from "./event/vs-deleted-matched-bookmarks-event";
import { VSEditBookmarkEvent } from "./event/vs-edit-bookmark-event";
import { VSRefreshEvent } from "./event/vs-refresh-event";
import { ViewerToolbarButtonDefinition } from "./iframe/viewer-toolbar-button-definition";
import { ViewerToolbarMessageService } from "./iframe/viewer-toolbar-message.service";
import { BaseTableModel } from "./model/base-table-model";
import { EmailDialogModel } from "./model/email-dialog-model";
import { ExportDialogModel } from "./model/export-dialog-model";
import { FileFormatType } from "./model/file-format-type";
import { FocusObjectEventModel } from "./model/focus-object-event-model";
import { GuideBounds } from "./model/layout/guide-bounds";
import { ZoomOptions } from "./model/layout/zoom-options";
import { MessageDialogModel } from "./model/message-dialog-model";
import { PagingControlModel } from "./model/paging-control-model";
import { ProfileTableDataEvent } from "./model/profile-table-data-event";
import { RemoveAnnotationsCondition } from "./model/remove-annotations-condition";
import { ScheduleDialogModel } from "./model/schedule/schedule-dialog-model";
import { VSBookmarkInfoModel, VSBookmarkType } from "./model/vs-bookmark-info-model";
import { VSChartModel } from "./model/vs-chart-model";
import { VSObjectModel } from "./model/vs-object-model";
import { VSSelectionContainerModel } from "./model/vs-selection-container-model";
import { VSChartService } from "./objects/chart/services/vs-chart.service";
import { AdhocFilterService } from "./objects/data-tip/adhoc-filter.service";
import { DataTipInLayoutCheckResult } from "./objects/data-tip/data-tip-in-layout-check-result";
import { DataTipService } from "./objects/data-tip/data-tip.service";
import { PopComponentService } from "./objects/data-tip/pop-component.service";
import { MiniToolbarService } from "./objects/mini-toolbar/mini-toolbar.service";
import { NavigationKeys } from "./objects/navigation-keys";
import { SelectionContainerChildrenService } from "./objects/selection/services/selection-container-children.service";
import { SelectionMobileService } from "./objects/selection/services/selection-mobile.service";
import { ActionHandler } from "./objects/table/action-handler";
import { CalcTableActionHandler } from "./objects/table/calc-table-action-handler";
import { CrosstabActionHandler } from "./objects/table/crosstab-action-handler";
import { TableActionHandler } from "./objects/table/table-action-handler";
import { ShowHyperlinkService } from "./show-hyperlink.service";
import { ToolbarActionsHandler } from "./toolbar-actions-handler";
import { CheckFormDataService } from "./util/check-form-data.service";
import { FormInputService } from "./util/form-input.service";
import { GlobalSubmitService } from "./util/global-submit.service";
import { ViewerResizeService } from "./util/viewer-resize.service";
import { VSUtil } from "./util/vs-util";
import { VsToolbarButtonDirective } from "./vs-toolbar-button.directive";

declare const window: any;
declare var globalPostParams: { [name: string]: string[] } | null;

const EXPORT_DIALOG_URI: string = "vs/export-dialog-model";
const EMAIL_DIALOG_URI: string = "vs/email-dialog-model";
const SCHEDULE_DIALOG_URI: string = "vs/schedule-dialog-model";
const COLLECT_PARAMS_URI: string = "/events/vs/collectParameters";
const IMPORT_XLS_URI: string = "vs/importXLS";
const CHECK_ASSEMBLY_IN_LAYOUT_URI: string = "vs/layouts/check-assembly-in-layout";
const CHECK_FORM_TABLES: string = "../api/vs/checkFormTables";
const REFRESH_VS_URI: string = "/events/vs/refresh";
const TOUCH_ASSET_URI: string = "/events/composer/touch-asset";
const REFRESH_VS_PREVIEW_URI: string = "/events/composer/viewsheet/preview/refresh";
const UNDO_URI: string = "/events/undo";
const REDO_URI: string = "/events/redo";
const OPEN_VS_URI: string = "/events/open";
const OPEN_VS_PREVIEW_URI: string = "/events/composer/viewsheet/preview";
const EDIT_VS_URI: string = "/events/composer/editViewsheet";
const TOGGLE_STATUS_URI: string = "/events/annotation/toggle-status";
const UPDATE_ANNOTATION_FORMAT_URI: string = "/events/annotation/update-format";
const ADD_VS_ANNOTATION_URI: string = "/events/annotation/add-viewsheet-annotation";
const CANCEL_VS_URI: string = "/events/composer/viewsheet/cancelViewsheet";
const VS_FORMAT_URI: string = "/events/composer/viewsheet/format";
const VS_GET_FORMAT_URI: string = "/events/composer/viewsheet/getFormat";
const CLOSE_VIEWSHEET_SOCKET_URI: string = "/events/composer/viewsheet/close";
const GET_PROFILE_TABLE_URL = "../api/portal/profile/table";

const BOOKMARK_URIS = {
   "save": "vs/bookmark/save-bookmark",
   "get": "vs/bookmark/get-bookmarks",
   "goto": "vs/bookmark/goto-bookmark",
   "setDefault": "vs/bookmark/set-default-bookmark",
   "add": "vs/bookmark/add-bookmark",
   "delete": "vs/bookmark/delete-bookmark",
   "edit": "vs/bookmark/edit-bookmark",
   "check-deleted": "vs/bookmark/check-bookmark-deleted",
   "check-changed": "vs/bookmark/check-bookmark-changed",
   "delete-matched": "vs/bookmark/delete-matched-bookmarks"
};

export interface ScrollViewportRect {
   top: number;
   left: number;
   width: number;
   height: number;
}

@Component({
   selector: "viewer-app",
   templateUrl: "viewer-app.component.html",
   styleUrls: ["viewer-app.component.scss"],
   providers: [
      ViewsheetClientService,
      DataTipService,
      AdhocFilterService,
      PopComponentService,
      VSChartService,
      AssemblyActionFactory,
      SelectionContainerChildrenService,
      CheckFormDataService,
      VSChartService,
      DebounceService,
      FullScreenService,
      ViewerResizeService,
      FormInputService,
      GlobalSubmitService,
      ViewerToolbarMessageService,
      {
         provide: DndService,
         useClass: VSDndService,
         deps: [ModelService, NgbModal, ViewsheetClientService]
      },
      {
         provide: ScaleService,
         useClass: VSScaleService
      },
      {
         provide: ContextProvider,
         useFactory: ViewerContextProviderFactory,
         deps: [[new Optional(), ComposerToken], [new Optional(), EmbedToken]]
      },
      {
         provide: DialogService,
         useFactory: ViewerDialogServiceFactory,
         deps: [NgbModal, SlideOutService, Injector, UIContextService]
      },
      {
         provide: ChartService,
         useExisting: VSChartService
      },
      ComposerRecentService,
      SelectionMobileService
   ]
})
export class ViewerAppComponent extends CommandProcessor implements OnInit, AfterViewInit,
   AfterViewChecked, AfterContentInit, OnDestroy
{
   @ViewChild("exportDialog") exportDialog: TemplateRef<any>;
   @ViewChild("emailDialog") emailDialog: TemplateRef<any>;
   @ViewChild("scheduleDialog") scheduleDialog: TemplateRef<any>;
   @ViewChild("bookmarkDialog") bookmarkDialog: TemplateRef<any>;
   @ViewChild("shareEmailDialog") shareEmailDialog: TemplateRef<any>;
   @ViewChild("shareHangoutsDialog") shareHangoutsDialog: TemplateRef<any>;
   @ViewChild("shareSlackDialog") shareSlackDialog: TemplateRef<any>;
   @ViewChild("shareLinkDialog") shareLinkDialog: TemplateRef<any>;
   @ViewChild("removeBookmarksDialog") removeBookmarksDialog: TemplateRef<any>;
   @ViewChild("viewerContainer") viewerContainer: ElementRef;
   @ViewChild("viewerRoot") viewerRoot: ElementRef;
   @ViewChild("variableInputDialog") variableInputDialog: VariableInputDialog;
   @ViewChild("notifications") notifications: NotificationsComponent;
   @ViewChild("viewerToolbar") viewerToolbar: ElementRef;
   @ViewChild("fileSelector") importExcelFileSelector: ElementRef<HTMLInputElement>;
   @ViewChild("scaleContainer") scaleContainer: ElementRef<HTMLInputElement>;
   @ViewChildren(VsToolbarButtonDirective) toolbarButtons: QueryList<VsToolbarButtonDirective>;
   @Input() touchDevice: boolean = false;
   @Input() annotationChanged: boolean = false;
   @Input() preview: boolean = false;
   @Input() linkView: boolean = false;
   @Input() layoutName: string = null;
   @Input() guideType: GuideBounds = GuideBounds.GUIDES_NONE;
   @Input() id: string;
   @Input() assetId: string;
   @Input() queryParameters: Map<string, string[]>;
   @Input() vsObjects: VSObjectModel[] = [];
   @Input() clientId: string;
   @Input() securityEnabled: boolean;
   @Input() principal: string;
   @Input() toolbarPermissions: string[];
   @Input() designLastModified: number = -1;
   @Input() fullScreen: boolean = false;
   @Input() inPortal = false; // currently opened as child route of portal
   @Input() inDashboard = false; // currently opened in the dashboard tab of portal
   @Input() tabsHeight: number = 0;
   @Input() dashboardName: string = null;
   @Input() fullscreenId: string;
   @Input() designSaved: boolean;
   @Input() isIframe = false;
   @Input() hideToolbar: boolean = false;
   @Input() hideMiniToolbar: boolean = false;
   @Input() globalLoadingIndicator: boolean = false;
   @Input() viewerOffsetFunc: () => { x: number, y: number, width: number, height: number, scrollLeft: number, scrollTop: number };
   @Output() onAnnotationChanged = new EventEmitter<boolean>();
   @Output() runtimeIdChange = new EventEmitter<string>();
   @Output() socket = new EventEmitter<ViewsheetClientService>();
   @Output() closeClicked = new EventEmitter<boolean>();
   @Output() closeCurrentTab = new EventEmitter<string>();
   @Output() onEditTable = new EventEmitter<{ model: BaseTableModel, isMetadata: boolean }>();
   @Output() onEditChart = new EventEmitter<{ model: VSChartModel, isMetadata: boolean }>();
   @Output() onOpenViewsheet = new EventEmitter<string>();
   @Output() fullScreenChange = new EventEmitter<boolean>();
   @Output() onMessageCommand = new EventEmitter<MessageCommand>();
   @Output() onOpenViewsheetOptionDialog = new EventEmitter<Dimension>();
   @Output() onEmbedError = new EventEmitter<string>();
   @Output() onLoadingStateChanged = new EventEmitter<{ name: string, loading: boolean }>();
   @Output() onDataTipPopComponentVisible = new EventEmitter<boolean>();

   @Input()
   get runtimeId(): string {
      return this._runtimeId;
   }

   set runtimeId(value: string) {
      this._runtimeId = value;
      this.dialogService.container = `.viewer-container[runtime-id="${value}"]`;
   }

   name: string;
   previewBaseId: string;
   accessible: boolean = false;
   exportTypes: { label: string, value: string }[] = [];
   viewsheetLoading: boolean = false;
   preparingData: boolean = false;
   toolbarVisible: boolean = true;
   hideLoadingDisplay: boolean = false;
   profilingVisible: boolean = false;
   editable: boolean = true;
   snapshot: boolean = false;
   annotated: boolean = false;
   showAnnotations: boolean = false;
   isMetadata: boolean = false;
   undoEnabled: boolean = false;
   redoEnabled: boolean = false;
   maxMode: boolean = false;
   /** Toolbar Dialog/Models */
   exportDialogModel: ExportDialogModel;
   emailDialogModel: EmailDialogModel;
   sendFunction:
      (model: EmailDialogModel, commitFn: Function, stopLoadFn: Function) => void;
   scheduleDialogModel: ScheduleDialogModel;
   vsBookmarkList: VSBookmarkInfoModel[] = [];
   propertyBookmark: VSBookmarkInfoModel = <VSBookmarkInfoModel>{};
   /** END Toolbar Dialog/Models */
   variableInputDialogModel: VariableInputDialogModel;
   vsObjectActions: AbstractVSActions<any>[] = [];
   updateEnabled: boolean;
   touchInterval: number;
   virtualScroll = false;
   viewsheetBackground: string;
   backgroundImage: SafeStyle;
   backgroundImageRepeat: string;
   scale: number = 1;
   scaleToScreen: boolean = false;
   fitToWidth: boolean = false;
   balancePadding: boolean = false;
   //is open the formatPane
   openFormatPane: boolean = false;
   selectObjectModel: VSChartModel = null;
   currentFormat: VSObjectFormatInfoModel = <VSObjectFormatInfoModel>{};
   origFormat: VSObjectFormatInfoModel = <VSObjectFormatInfoModel>{};
   selectedAssemblies: number[];
   selectedActions: AbstractVSActions<any>;
   allAssemblyBounds: { top: number, left: number, bottom: number, right: number };
   submitClicked: Subject<boolean> = new Subject<boolean>();
   expired = false;
   transformFinished = false;
   editBookmarkFinished = false;
   mobileDevice: boolean = GuiTool.isMobileDevice();
   gotoBindingPane: boolean = false;
   shareConfig: ShareConfig;
   showScroll: boolean = false;
   // store the size the viewsheet is opened/refreshed with
   appSize: Dimension = new Dimension(0, 0);
   private initing: boolean = true;
   private serverUpdateIntervalId: any;
   private _active: boolean = true;
   private loadingEventCount: number = 0;
   private closeProgressSubject: Subject<any> = new Subject();
   public vsInfo: ViewsheetInfo = new ViewsheetInfo([], null, null, null, this.getOrgId());
   private composedDashboard = false;
   private subscriptions = new Subscription();
   private drillDown: boolean = false;
   toolbarTransform: SafeStyle = null;
   bodyTransform: SafeStyle = null;
   private topY: number = null;
   viewsheetName = "";
   private _scrollLeft: number = 0;
   private _scrollTop: number = 0;
   _showHints: boolean = false;
   private drillFrom: string;
   private wallboard = false;
   public zoomOptions = ZoomOptions;
   public cancelled = false;
   private clickOnVS: boolean = false;
   private currOrgID: string = null;

   // Keyboard nav - Section 508 compliance
   keyNavigation: Subject<FocusObjectEventModel> =
      new Subject<FocusObjectEventModel>();
   focusedObject: VSObjectModel;
   private innerIndex: number = -1;
   private clickListener: () => void;
   private focusedToolbarButton = -1;
   private _runtimeId: string;
   waiting = false;
   exporting = false;
   mobileToolbarActions: AssemblyActionGroup[] = [];
   showing: AssemblyActionGroup[] = [];
   more: AssemblyActionGroup[] = [];
   mobileActionSubscription: Subscription;
   latestMobileMouseEvent: MouseEvent;
   pageControlStartX: number = 0;
   pageControlStartY: number = 0;
   buttonSize: number = 80;
   embed: boolean;
   scrollViewport: ScrollViewportRect;

   textLimitConfirmed: boolean = false;
   columnLimitConfirmed: boolean = false;
   selectedPopComponent: string = null;
   selectedDataTipView: string = null;
   isDefaultOrgAsset: boolean = false;

   constructor(public viewsheetClient: ViewsheetClientService,
               private stompClientService: StompClientService,
               private dataTipService: DataTipService,
               private popComponentService: PopComponentService,
               private modelService: ModelService,
               private modalService: NgbModal,
               private dialogService: DialogService,
               private dropdownService: FixedDropdownService,
               private ngbDatepickerConfig: NgbDatepickerConfig,
               private downloadService: DownloadService,
               private actionFactory: AssemblyActionFactory,
               private http: HttpClient,
               private changeDetectorRef: ChangeDetectorRef,
               private formDataService: CheckFormDataService,
               private debounceService: DebounceService,
               private scaleService: ScaleService,
               private contextProvider: ContextProvider,
               private viewDataService: ViewDataService,
               private fullScreenService: FullScreenService,
               private router: Router,
               private renderer: Renderer2,
               private zone: NgZone,
               private sanitizer: DomSanitizer,
               private titleService: Title,
               private hyperlinkService: ShowHyperlinkService,
               private viewerResizeService: ViewerResizeService,
               private firstDayOfWeekService: FirstDayOfWeekService,
               private tooltipConfig: NgbTooltipConfig,
               private shareService: ShareService,
               private openComposerService: OpenComposerService,
               private richTextService: RichTextService,
               private viewerToolbarMessageService: ViewerToolbarMessageService,
               @Inject(DOCUMENT) private document: Document,
               private composerRecentService: ComposerRecentService,
               private pageTabService: PageTabService,
               private pagingControlService: PagingControlService,
               private selectionMobileService: SelectionMobileService,
               private miniToolbarService: MiniToolbarService,
               private featureFlagService: FeatureFlagsService,
               private assetLoadingService: AssetLoadingService,
               private viewContainerRef: ViewContainerRef)
   {
      super(viewsheetClient, zone, true);
      tooltipConfig.tooltipClass = "top-tooltip";
      GuiTool.isTouchDevice().then(
         (value: boolean) => {
            this.touchDevice = value;

            // give some time for zoom position to be known (safari)
            setTimeout(() => this.pinchZoom(null), 200);
         },
         (error: any) => {
            console.error("Failed to determine if touch-based device: ", error);
         });

      // Need to set a default min and max date otherwise the range is only 20 years.
      ngbDatepickerConfig.minDate = {year: 1900, month: 1, day: 1};
      ngbDatepickerConfig.maxDate = {year: 2099, month: 12, day: 31};
      this.embed = this.contextProvider.embed;

      this.http.get<string>("../api/em/navbar/organization").subscribe((org)=>{this.currOrgID = org;});
   }

   getAssemblyName(): string {
      return null;
   }

   ngOnInit(): void {
      this.actionFactory.securityEnabled = this.securityEnabled;
      this.vsInfo = new ViewsheetInfo(this.vsObjects, null, false, this.runtimeId, this.getOrgId());
      this.scaleToScreen = !!this.viewDataService?.data?.scaleToScreen;
      this.fitToWidth = !!this.viewDataService?.data?.fitToWidth;

      if(this.vsObjects) {
         this.vsObjectActions = [];

         this.vsObjects.forEach((model) => {
            this.vsObjectActions.push(this.actionFactory.createActions(model));
         });
      }

      if(this.assetId) {
         const asset: AssetEntry = createAssetEntry(this.assetId);

         if(!this.preview && !this.embed) {
            this.titleService.setTitle(asset.path);
         }

         this.viewsheetName = asset.path.replace(/^.+\/([^/]+)$/, "$1");
      }

      this.actionFactory.stateProvider = {
         isActionVisible: (id: string, model: VSObjectModel) =>
            AbstractVSActions.isActionVisible(this.toolbarPermissions, id),
         isActionEnabled: (id: string, model: VSObjectModel) => true
      };

      this.annotationChanged = false;

      // Subscribe to heartbeat and touch asset to prevent expiration
      this.subscriptions.add(this.viewsheetClient.onHeartbeat.subscribe(() => {
         let event = new TouchAssetEvent();
         event.setDesign(false);
         event.setChanged(false);
         event.setUpdate(false);
         event.setWallboard(this.wallboard);
         this.viewsheetClient.sendEvent(TOUCH_ASSET_URI, event);
      }));

      this.subscriptions.add(this.fullScreenService.fullScreenChange.subscribe(
         () => this.onFullScreenChange()));

      this.subscriptions.add(this.hyperlinkService.drillDownSubject.subscribe(
         () => this.drillDown = true));

      this.subscriptions.add(this.viewsheetClient.onRenameTransformFinished.subscribe(
      (message) => {
         if(message.bookmark != undefined) {
            const currentBookmark = this.vsBookmarkList.find((vsBookmark) => vsBookmark.currentBookmark);

            if(currentBookmark != null && message.bookmark == currentBookmark.name && message.id == this.runtimeId) {
               this.editBookmarkFinished = true;
            }
         }
         else if(message.id == this.runtimeId) {
            this.transformFinished = true;
         }
      }));

      this.subscriptions.add(this.viewsheetClient.onTransformFinished.subscribe(
      (message) => {
         if(message.id == this.runtimeId) {
            this.transformFinished = true;
         }
      }));

      this.firstDayOfWeekService.getFirstDay().subscribe((model) => {
         this.ngbDatepickerConfig.firstDayOfWeek = model.isoFirstDay;
      });

      this.shareService.getConfig(this.getOrgId()).subscribe(config => this.shareConfig = config);

      if(this.isIframe && this.fullScreenService.fullScreenMode && !this.fullScreen) {
         this.fullScreenService.exitFullScreen();
      }

      this.setMobileToolbarActions();
      this.composerRecentService.currentUser = this.principal;
   }

   ngAfterViewInit(): void {
      // Do not know the size of the viewsheet pane until after view is init
      if(this.preview && this.runtimeId) {
         this.viewsheetClient.connect();
         this.viewsheetClient.beforeDestroy = () => this.beforeDestroy();
         this.socket.emit(this.viewsheetClient);
         this.previewBaseId = this.runtimeId;
         this.openPreviewViewsheet();
      }
      else if(this.runtimeId) {
         this.viewsheetClient.connect();
         this.viewsheetClient.beforeDestroy = () => this.beforeDestroy();
         this.viewsheetClient.runtimeId = this.runtimeId;
         this.openViewsheet(this.runtimeId);
         this.showBookmarks();
      }
      else if(this.assetId) {
         if(this.embed) {
            this.subscriptions.add(this.viewsheetClient.connectionError().subscribe((error) => {
               this.onEmbedError.emit(error);
            }));
         }

         this.viewsheetClient.connect(this.embed);
         this.viewsheetClient.beforeDestroy = () => this.beforeDestroy();
         this.openViewsheet();
      }
      else {
         console.error("The runtime or asset identifier must be provided");
      }

      if(this.viewerRoot?.nativeElement) {
         this.zone.runOutsideAngular(() => {
            new ResizeSensor(this.viewerRoot.nativeElement, () => {
               this.onViewerRootResizeEvent();
            });
         });
      }

      this.dataTipService.viewerOffsetFunc = this.setDataTipOffsets.bind(this);
      this.popComponentService.viewerOffsetFunc = this.setDataTipOffsets.bind(this);
      this.popComponentService.getComponentModelFunc = this.getComponentModel.bind(this);

      if(this.embed) {
         this.handleDataTipPopComponentChanges();
      }
   }

   ngAfterContentInit(): void {
      this.scrollViewport = this.getScrollViewport();
   }

   ngAfterViewChecked(): void {
      this.setViewerToolbarDefinitions();
   }

   ngOnDestroy(): void {
      // for some reason dialogService is not destroyed (angular 5)
      this.dialogService.ngOnDestroy();

      this.clearServerUpdateInterval();
      this.subscriptions.unsubscribe();

      if(!!this.mobileActionSubscription) {
         this.mobileActionSubscription.unsubscribe();
      }

      if(!!this.clickListener) {
         this.clickListener();
      }

      this.assetLoadingService.setLoading(this.inDashboard ?
         this.dashboardName : this.assetId, false);

      this.debounceService.cancel(this.runtimeId + "_notify_parent_frame");

      if(this.inDashboard && window != window.parent) {
         window.parent.postMessage({"dashboardClosed": this.runtimeId}, "*");
      }
   }

   @Input()
   set active(value: boolean) {
      if(this._active != value) {
         this._active = value;

         if(!this._active) {
            this.changeDetectorRef.detach();
         }
         else {
            this.changeDetectorRef.reattach();
         }

         // update preview viewsheet when it is changed to focused sheet,
         if(value && this.preview) {
            // need to update on next tick as viewerRoot will be hidden when active just changes
            setTimeout(() => {
               const oldScaleSize = this.getScaleSize();
               this.setAppSize();
               const newScaleSize = this.getScaleSize();

               // refresh if preview is out of date or viewport size has changed
               if(this.designLastModified > this.viewsheetClient.lastModified ||
                  !Tool.isEquals(oldScaleSize, newScaleSize) || this.designSaved)
               {
                  this.designSaved = false;
                  let event = new OpenPreviewViewsheetEvent(null, newScaleSize.width,
                     newScaleSize.height, this.mobileDevice, window.navigator.userAgent);
                  event.layoutName = this.layoutName;
                  this.viewsheetClient.sendEvent(REFRESH_VS_PREVIEW_URI, event);
               }
            }, 0);
         }
      }
   }

   get active(): boolean {
      return this._active;
   }

   get mobileToolbarVisible(): boolean {
      return this.touchDevice && this.selectedActions != null &&
         (this.selectedPopComponent == null && this.selectedDataTipView == null ||
         this.popComponentService.isPopComponentShow(this.selectedPopComponent) ||
         this.dataTipService.isDataTipShow(this.selectedDataTipView));
   }

   @HostListener("window:pointerup", ["$event"])
   pinchZoom(event) {
      if(this.touchDevice) {
         let ratio: number;
         let left: number;
         let top: number;
         this.toolbarTransform = null;
         this.bodyTransform = null;
         const rect = this.viewerToolbar.nativeElement.getBoundingClientRect();
         const screenSize = this.screenSize;

         if(window.visualViewport) {
            ratio = 1 / window.visualViewport.scale;

            // original dist to top of page
            if(this.topY == null) {
               this.topY = rect.top;
            }

            left = Math.round(window.visualViewport.pageLeft / ratio);
            top = Math.round(Math.max(0, window.visualViewport.pageTop - this.topY) / ratio);
         }
         else if(window.innerWidth && window.innerWidth != screenSize.width) {
            ratio = window.innerWidth / screenSize.width;

            // original dist to top of page
            if(this.topY == null) {
               this.topY = rect.top + window.pageYOffset;
            }

            left = Math.round(window.pageXOffset / ratio);
            top = Math.round(Math.max(0, window.pageYOffset - this.topY) / ratio);
         }

         if(ratio != null) {
            this.toolbarTransform = this.sanitizer.bypassSecurityTrustStyle(
               "scale(" + ratio + ") translate(" + left + "px, " + top + "px)");
            const shrunk = Math.round((rect.height / ratio) - rect.height);
            this.bodyTransform = this.sanitizer.bypassSecurityTrustStyle(
               "translate(0, " + -shrunk + "px)");
         }
      }
   }

   get screenSize(): Dimension {
      let landscape: boolean = window.orientation == 90 || window.orientation == -90;

      if(landscape && GuiTool.isIOS()) {
         return new Dimension(screen.height, screen.width);
      }

      return new Dimension(screen.width, screen.height);
   }

   @HostListener("window:touchend", ["$event"])
   scrolled(event) {
      this.checkZoom();
   }

   // check for zoom level until it stabilize
   private checkZoom() {
      const checkScroll = () => {
         const otrans = this.toolbarTransform;
         this.pinchZoom(event);
         const ntrans = this.toolbarTransform;

         // swipe scroll may take a couple of seconds to stop, wait until the scrolling
         // ends to make sure the toolbar is at the correct position
         if(!Tool.isEquals(otrans, ntrans)) {
            setTimeout(checkScroll, 200);
         }
      };

      setTimeout(checkScroll, 200);
   }

   onViewerRootResizeEvent() {
      this.updateScrollViewport();

      // need to check if viewer root has changed size, binding to window resize is not
      // enough as the viewer root can be resized by other methods
      if(this._active && this.scaleToScreen && this.viewerRoot && this.appSize &&
         (this.viewerRoot.nativeElement.offsetWidth != this.appSize.width ||
            this.viewerRoot.nativeElement.offsetHeight != this.appSize.height) &&
         (!!this.vsObjects && this.vsObjects.length > 0))
      {
         this.onViewerRootResize();
      }

      if(this.viewerRoot) {
         this.zone.run(() => {
            this.viewerResizeService.fireResized(
               this.viewerRoot.nativeElement.offsetWidth,
               this.viewerRoot.nativeElement.offsetHeight);
         });
      }
   }

   @HostListener("window:beforeunload", ["$event"])
   beforeunloadHandler(event) {
      // this message may or may not be shown depending on the browser
      const confirmMsg = "Annotations on this page have changed. " +
         "Do you want to ignore the changes and exit?";

      if(this.annotationChanged) {
         event.returnValue = confirmMsg;
         return confirmMsg;
      }

      return null;
   }

   /**
    * When you press the delete key in the viewer remove all selected annotations
    *
    * @param {KeyboardEvent} event
    */
   @HostListener("keyup", ["$event"])
   onKeyUp(event: KeyboardEvent) {
      // delete key
      if(event.keyCode === 46) {
         this.removeAnnotations();
      }
   }

   onToolbarButtonFocus(button: VsToolbarButtonDirective) {
      this.focusedToolbarButton = this.toolbarButtons.toArray().findIndex(b => b == button);
   }

   private nextToolbarButton(): void {
      this.focusedToolbarButton = this.focusedToolbarButton + 1;

      if(this.focusedToolbarButton >= this.toolbarButtons.length) {
         this.focusedToolbarButton = -1;
      }

      while(this.focusedToolbarButton >= 0 &&
      this.toolbarButtons.toArray()[this.focusedToolbarButton].disabled)
      {
         this.focusedToolbarButton = this.focusedToolbarButton + 1;

         if(this.focusedToolbarButton == this.toolbarButtons.length) {
            this.focusedToolbarButton = -1;
         }
      }
   }

   private previousToolbarButton(): void {
      this.focusedToolbarButton = this.focusedToolbarButton - 1;

      while(this.focusedToolbarButton >= 0 &&
      this.toolbarButtons.toArray()[this.focusedToolbarButton].disabled)
      {
         this.focusedToolbarButton = this.focusedToolbarButton - 1;
      }
   }

   onKeyDown(event: KeyboardEvent) {
      if(!this.saveCurrentBookmarkDisabled() && event.keyCode === 83 && event.ctrlKey &&
         !this.isDefaultOrgAsset)
      {
         event.preventDefault();
         this.saveBookmark();
      }

      if(event.ctrlKey || event.metaKey) {
         // ctrl-z
         if(event.keyCode == 90 &&
            !this.isPermissionForbidden("PageNavigation", "Previous") &&
            this.undoEnabled)
         {
            this.previousPage();
         }
         // ctrl-y
         else if(event.keyCode == 89 &&
            !this.isPermissionForbidden("PageNavigation", "Next") &&
            this.redoEnabled)
         {
            this.nextPage();
         }
      }

      // Switch focus to next component
      if(this.accessible && event.keyCode === 9) { // tab
         const shift: boolean = event.shiftKey;
         const selectionContainer: boolean = this.focusedObject &&
            this.focusedObject.objectType == "VSSelectionContainer";
         let changeAssembly: boolean = true;

         if(this.focusedToolbarButton >= 0) {
            if(shift) {
               this.previousToolbarButton();
            }
            else {
               this.nextToolbarButton();
            }
         }
         else if(!this.focusedObject && !shift && this.toolbarButtons.length) {
            this.nextToolbarButton();
         }

         if(this.focusedToolbarButton >= 0) {
            this.toolbarButtons.toArray()[this.focusedToolbarButton].focus();
            event.stopPropagation();
            event.preventDefault();
            return;
         }

         if(selectionContainer) {
            const assembly: VSSelectionContainerModel =
               this.focusedObject as VSSelectionContainerModel;
            this.innerIndex = shift ? this.innerIndex - 1 : this.innerIndex + 1;
            changeAssembly = this.innerIndex < 0 ||
               this.innerIndex >= assembly.childObjects.length;
         }

         if(changeAssembly) {
            this.innerIndex = -1;
            let index: number = !!this.focusedObject ?
               this.vsObjects.findIndex((object: VSObjectModel) => {
                  return object.absoluteName == this.focusedObject.absoluteName;
               }) : -1;
            let nextIndex: number = shift ?
               this.getPreviousSelectableAssembly(index == -1 ? this.vsObjects.length - 1 : --index) :
               this.getNextSelectableAssembly(++index);

            if(nextIndex == -1) {
               this.focusedObject = null;
            }
            else {
               this.focusedObject = this.vsObjects[nextIndex];
            }
         }

         let evt: FocusObjectEventModel = {
            focused: this.focusedObject,
            key: shift ? NavigationKeys.SHIFT_TAB : NavigationKeys.TAB,
            index: this.innerIndex
         };

         this.keyNavigation.next(evt);

         if(!!this.focusedObject) {
            event.stopPropagation();
            event.preventDefault();
         }
      }
      else if(this.accessible && this.focusedObject) {
         let evt: any = {
            focused: this.focusedObject,
            key: NavigationKeys.UP,
            index: this.innerIndex
         };
         let keyHit: boolean = false;

         if(event.keyCode === 37) { // left
            evt.key = NavigationKeys.LEFT;
            this.keyNavigation.next(evt);
            keyHit = true;
         }
         else if(event.keyCode === 38) { // up
            this.keyNavigation.next(evt);
            keyHit = true;
         }
         else if(event.keyCode === 39) { // right
            evt.key = NavigationKeys.RIGHT;
            this.keyNavigation.next(evt);
            keyHit = true;
         }
         else if(event.keyCode === 40) { // down
            evt.key = NavigationKeys.DOWN;
            this.keyNavigation.next(evt);
            keyHit = true;
         }
         else if(event.keyCode === 32) { // spacebar
            evt.key = NavigationKeys.SPACE;
            this.keyNavigation.next(evt);
            keyHit = true;
         }

         const textInput: boolean = !!this.focusedObject &&
            this.focusedObject.objectType == "VSTextInput";
         const comboBoxSafe: boolean = !!this.focusedObject &&
         this.focusedObject.objectType == "VSComboBox" ?
            event.keyCode != 38 && event.keyCode != 40 : true;

         if(keyHit && !textInput && comboBoxSafe) {
            event.preventDefault();
         }
      }

      if(this.accessible && !!this.focusedObject && !this.clickListener) {
         this.clickListener = this.renderer.listen(
            "document", "click", (e) => {
               this.clickListener();
               this.focusedObject = null;
               this.clickListener = null;
               this.changeDetectorRef.detectChanges();
            });
      }
   }

   /**
    * Get next assembly in the list to focus on.
    * @param {number} index
    * @returns {number}
    */
   private getNextSelectableAssembly(index: number): number {
      for(let i = index; i < this.vsObjects.length; i++) {
         if(VSUtil.isKeyNavEnabled(this.vsObjects[i]) && this.vsObjects[i].visible) {
            return i;
         }
      }

      return -1;
   }

   /**
    * Get previous assembly in the list to focus on.
    * @param {number} index
    * @returns {number}
    */
   private getPreviousSelectableAssembly(index: number): number {
      for(let i = index; i >= 0; i--) {
         if(VSUtil.isKeyNavEnabled(this.vsObjects[i]) && this.vsObjects[i].visible) {
            return i;
         }
      }

      return -1;
   }

   selectAssembly(payload: [number, AbstractVSActions<any>, MouseEvent]): void {
      const [index, actions, event] = payload;
      let name: string = this.vsInfo.vsObjects[index].absoluteName;
      this.selectedActions = actions;
      this.selectedPopComponent = this.popComponentService.getPopComponent() == name ? name : null;
      this.selectedDataTipView = this.dataTipService.dataTipName == name ? name : null;

      if(this.isMobile() && !!this.selectedActions) {
         this.latestMobileMouseEvent = event;
         this.addMobileActionSubsciption();
         this.showScrollButton(event);
      }

      if(!!event && (event.ctrlKey || event.button === 2 ||
         GuiTool.parentContainsClass(<Element> event.target, "linked-header-indicator")) &&
         this.selectedAssemblies)
      {
         const currentIndex = this.selectedAssemblies.indexOf(index);

         if(currentIndex === -1) {
            this.selectedAssemblies.push(index);
         }
      }
      else {
         this.selectedAssemblies = [index];
         this.selectionMobileService.toggleSelectionMaxMode(this.vsInfo.vsObjects[index]);
      }
   }

   clearSelectedAssemblies(event: MouseEvent, mobileToolbarClose: boolean = false): void {
      if(!event || !GuiTool.parentContainsClass(event.target as Element, "vs-object") &&
         // if the element is orphaned (e.g. input in form), it may be caused by ctrl-click
         // on a selected cell, which causes the input is removed on click
         (<Element>event.target).parentElement != null && !(this.maxMode && this.mobileDevice))
      {
         if(this.vsInfo != null && this.clickOnVS) {
            for(let obj of this.vsInfo.vsObjects) {
               if(obj != null && obj.objectType == "VSChart" &&
                  (obj as VSChartModel).chartSelection != null)
               {

                  let chartModel = obj as VSChartModel;
                  chartModel.selectedRegions = [];
                  chartModel.titleSelected = null;
                  chartModel.chartSelection.chartObject = null;
                  chartModel.chartSelection.regions = [];

                  if(chartModel.clearCanvasSubject) {
                     chartModel.clearCanvasSubject.next(null);
                  }

                  if(chartModel.hasFlyovers && !!chartModel.lastFlyover) {
                     chartModel.lastFlyover = null;
                     const chartEvent = new VSChartFlyoverEvent(chartModel, "");
                     this.viewsheetClient.sendEvent("/events/vschart/flyover", chartEvent);
                  }
               }
            }
         }

         if(mobileToolbarClose) {
            this.selectionMobileService.resetSelectionMaxMode();
         }

         this.selectedAssemblies = null;
         this.selectedActions = null;
      }
   }

   // if using selection box to select chart, maybe it click on vs, should not clear selection.
   mousedown(event: MouseEvent): void {
      this.clickOnVS = !GuiTool.parentContainsClass(event.target as Element, "vs-object");
   }

   /**
    * Update data from server at the specified interval if update is enabled.
    */
   setServerUpdateInterval(): void {
      this.clearServerUpdateInterval();

      if(this.updateEnabled) {
         // clear old server update interval
         let interval: number = this.touchInterval ? this.touchInterval * 1000 : 60000;
         this.serverUpdateIntervalId = setInterval(() => {
            let event = new TouchAssetEvent();
            event.setDesign(false);
            event.setChanged(false);
            event.setUpdate(true);
            event.setWidth(this.viewerRoot.nativeElement.offsetWidth);
            event.setHeight(this.viewerRoot.nativeElement.offsetHeight);
            this.viewsheetClient.sendEvent(TOUCH_ASSET_URI, event);
         }, interval);
      }
   }

   clearServerUpdateInterval(): void {
      if(this.serverUpdateIntervalId != null && !isNaN(this.serverUpdateIntervalId)) {
         clearInterval(this.serverUpdateIntervalId);
      }
   }

   get previousURLEnable(): boolean {
      let snapshots =
         !!this.viewDataService.data ? this.viewDataService.data.previousSnapshots : [];
      let lastSnapshot =
         !!snapshots && snapshots.length > 0 ? snapshots[snapshots.length - 1] : null;

      if(!lastSnapshot) {
         return false;
      }

      return !lastSnapshot ? false : JSON.parse(lastSnapshot).type != PreviousSnapshotType.VS;
   }

   back() {
      const snapshots: string[] = this.viewDataService.data.previousSnapshots;
      this.hyperlinkService.backToPreviousSheet(this.router, snapshots, this.runtimeId,
         this.drillFrom);
   }

   previousPage(): void {
      if(this.undoEnabled && !this.maxMode) {
         this.viewsheetClient.sendEvent(UNDO_URI);
      }
   }

   nextPage(): void {
      this.viewsheetClient.sendEvent(REDO_URI);
   }

   editViewsheet(): void {
      if(this.composedDashboard) {
         this.openComposer(this.assetId, this.composedDashboard);
      }
      else {
         this.openComposerService.composerOpen.subscribe(open => {
            if(open) {
               let event = new EditViewsheetEvent(this.assetId);
               this.viewsheetClient.sendEvent(EDIT_VS_URI, event);
            }
            else {
               this.openComposer(this.assetId);
            }
         });
      }
   }

   reopenExpiredViewsheet(): void {
      this.fullscreenId = null;
      this.vsObjects.splice(0, this.vsObjects.length);
      this.vsObjectActions = [];

      if(this.preview) {
         this.openPreviewViewsheet();
      }
      else {
         this.openViewsheet();
      }

      this.expired = false;
      this.transformFinished = false;
      this.editBookmarkFinished = false;
   }

   refreshViewsheet(confirmed: boolean = false, resizing: boolean = false): void {
      const event = new VSRefreshEvent();
      event.setConfirmed(confirmed);
      event.setResizing(resizing);

      if(this.scaleToScreen) {
         this.setAppSize();
         let width: number;
         let height: number;

         if(this.preview && this.layoutName || !this.scaleToScreen) {
            const scaleSize = this.getScaleSize();
            width = scaleSize.width;
            height = scaleSize.height;
         }
         else {
            // if not in layout, use the offset size for scaling, it includes the scrollbars, which
            // is correct
            width = this.viewerRoot.nativeElement.offsetWidth;
            height = this.viewerRoot.nativeElement.offsetHeight;
         }

         event.setWidth(width);
         event.setHeight(height);
      }

      this.formDataService.resetCount();
      this.viewsheetClient.sendEvent(REFRESH_VS_URI, event);
   }

   zoom(out: boolean) {
      if((this.scale <= 0.2 && out) || (this.scale >= 2.0 && !out)) {
         return;
      }

      this.scale = out ? this.scale - 0.2 : this.scale + 0.2;
      this.scaleService.setScale(this.scale);
   }

   zoomLayout(zoom: ZoomOptions): void {
      if(zoom == ZoomOptions.ZOOM_OUT) {
         this.zoom(true);
      }
      else if(zoom == ZoomOptions.ZOOM_IN) {
         this.zoom(false);
      }
      else {
         this.scale = zoom;
         this.scaleService.setScale(this.scale);
      }
   }

   isZoomItemSelected(zoom: ZoomOptions): boolean {
      return this.scale == zoom;
   }

   zoomOutEnabled(): boolean {
      return Number(this.scale.toFixed(2)) > 0.2 && Number(this.scale.toFixed(2)) <= 2.0;
   }

   zoomInEnabled(): boolean {
      return Number(this.scale.toFixed(2)) >= 0.2 && Number(this.scale.toFixed(2)) < 2.0;
   }

   isMobile(): boolean {
      return GuiTool.isMobileDevice();
   }

   emailViewsheet(): void {
      const modelUri: string = "../api/" + EMAIL_DIALOG_URI + "/" + Tool.byteEncode(this.runtimeId);

      this.sendFunction =
         (model: EmailDialogModel, commitFn: Function, stopLoadFn: Function) => {
            this.modelService.sendModel(modelUri, model).pipe(
               map(res => res.body))
               .subscribe((message: MessageDialogModel) => {
                  stopLoadFn();
                  ComponentTool.showMessageDialog(this.modalService,
                     ComponentTool.getDialogTitle(message.type), message.message)
                     .then((result: string) => {
                        if(message.success && result === "ok") {
                           commitFn();
                        }
                     });
               });
         };

      this.waiting = true;
      this.modelService.getModel(modelUri)
         .subscribe(
            (data: EmailDialogModel) => {
               this.waiting = false;
               this.emailDialogModel = data;
               const options: SlideOutOptions = {
                  backdrop: "static",
                  popup: true,
                  size: "lg"
               };
               this.dialogService.open(this.emailDialog, options).result.then(
                  (result: EmailDialogModel) => {
                     this.emailDialogModel = null;
                  },
                  () => {
                     this.emailDialogModel = null;
                  }
               );
            },
            (err) => {
               this.waiting = false;
               console.error("Failed to load email dialog model: ", err);
            }
         );
   }

   scheduleViewsheet(): void {
      this.waiting = true;
      this.modelService.getModel("../api/" + SCHEDULE_DIALOG_URI)
         .subscribe(
            (data: ScheduleDialogModel) => {
               this.waiting = false;
               this.scheduleDialogModel = data;
               this.scheduleDialogModel.bookmark = DateTypeFormatter.format(new Date(), "YYYY-MM-DD HH:mm:ss");
               const options: SlideOutOptions = {backdrop: "static", popup: true};
               this.dialogService.open(this.scheduleDialog, options).result.then(
                  (result: ScheduleDialogModel) => {
                     this.scheduleDialogModel = null;
                     const eventUri: string = "/events/" + SCHEDULE_DIALOG_URI;
                     this.viewsheetClient.sendEvent(eventUri, result);
                  },
                  () => {
                     this.scheduleDialogModel = null;
                  }
               );
            },
            (err) => {
               this.waiting = false;
               console.error("Failed to load email dialog model: ", err);
            }
         );
   }

   printViewsheet(): void {
      const url = "../export/viewsheet/" + Tool.byteEncode(this.runtimeId) +
         "?match=true&current=true&previewPrintLayout=true";
      GuiTool.openBrowserTab(url);
   }

   exportViewsheet(): void {
      this.waiting = true;
      const modelUri: string = "../api/" + EXPORT_DIALOG_URI + "/" +
         Tool.byteEncode(this.runtimeId);
      const params = new HttpParams()
         .set("orgId", createAssetEntry(this.assetId).organization);

      this.modelService.getModel(modelUri, params)
         .subscribe(
            (data: ExportDialogModel) => {
               this.waiting = false;
               this.exportDialogModel = data;
               const options: SlideOutOptions = {backdrop: "static", popup: true};
               this.dialogService.open(this.exportDialog, options).result.then(
                  (result: ExportDialogModel) => {
                     this.exportDialogModel = null;

                     const format = result.fileFormatPaneModel;

                     let url = "../export/viewsheet/" + Tool.byteEncode(this.runtimeId) +
                        "?format=" + format.formatType +
                        "&match=" + format.matchLayout +
                        "&expandSelections=" + !!format.expandSelections +
                        "&current=" + format.includeCurrent +
                        "&bookmarks=" + encodeURIComponent(format.selectedBookmarks.join(",")) +
                        "&onlyDataComponents=" + !!format.onlyDataComponents +
                        "&exportAllTabbedTables=" + format.exportAllTabbedTables;

                     if(this.securityEnabled) {
                        url += "&organizationId=" + createAssetEntry(this.assetId).organization;
                     }

                     if(format.formatType == FileFormatType.EXPORT_TYPE_CSV &&
                        !!format.csvConfig)
                     {
                        let csvConfig = format.csvConfig;

                        if(csvConfig.delimiter) {
                           url += "&delimiter=" + csvConfig.delimiter;
                        }

                        if(csvConfig.quote) {
                           const quote = encodeURIComponent(csvConfig.quote);
                           url += "&quote=" + quote;
                        }

                        if(csvConfig.selectedAssemblies &&
                           csvConfig.selectedAssemblies.length > 0)
                        {
                           url += "&tableAssemblies=" +
                              encodeURIComponent(csvConfig.selectedAssemblies.join(","));
                        }

                        url += "&keepHeader=" + csvConfig.keepHeader +
                           "&tabDelimited=" + csvConfig.tabDelimited;
                     }

                     if(format.formatType == FileFormatType.EXPORT_TYPE_SNAPSHOT &&
                        this.vsObjects.length > 0 && !!this.vsObjects[0].cubeType)
                     {
                        ComponentTool.showMessageDialog(this.modalService, "_#(js:Error)",
                           "_#(js:cube.not.supported)");
                        return;
                     }

                     this.exporting = true;

                     // pdf export is opened in browser, other formats are downloaded
                     if(format.formatType == FileFormatType.EXPORT_TYPE_PDF) {
                        GuiTool.openBrowserTab(url);
                     }
                     else {
                        this.downloadService.download(url);
                     }

                     // send with a slight delay to allow the export to start first
                     setTimeout(() => this.checkExportStatus(), 1000);
                  },
                  () => {
                     this.exportDialogModel = null;
                  }
               );
            },
            (err) => {
               this.waiting = false;
               console.error("Failed to load export dialog model: ", err);
            }
         );
   }

   importExcel(): void {
      if(this.importExcelFileSelector != null) {
         this.importExcelFileSelector.nativeElement.click();
      }
   }

   importExcelFile(event: any): void {
      if(event) {
         let fileList: FileList = event.target.files;

         if(fileList.length > 0) {
            let file: File = fileList[0];
            let formData: FormData = new FormData();
            formData.append("file", file);
            const extension: string = file.name.split(".").pop();
            this.http.post("../api/" + IMPORT_XLS_URI + "/upload/" + extension
               + "/" + Tool.byteEncode(this.runtimeId), formData)
               .subscribe(
                  (data: any) => {
                     let uri: string = "/events/" + IMPORT_XLS_URI + "/" + extension;
                     this.viewsheetClient.sendEvent(uri);
                  },
                  (err: any) => {
                     console.error("File import was unsuccessful.");
                  }
               );
         }

         //change <input> value to be zero so that change event will be triggered
         //no matter what the next value selected is
         event.target.value = null;
      }
   }

   /**
    * Send the new toggle annotation status to the server
    */
   toggleAnnotations(): void {
      const event = new ToggleAnnotationStatusEvent(!this.showAnnotations);
      this.viewsheetClient.sendEvent(TOGGLE_STATUS_URI, event);
   }

   showBookmarks(gotoBookmark: boolean = true): void {
      this.http.get<boolean>("../api/vs/bookmark/isDefaultOrgAsset/" + Tool.byteEncode(this.runtimeId)).subscribe( (isDefaultOrgAsset) => this.isDefaultOrgAsset = isDefaultOrgAsset);

      let bookmarkName: string = null;
      let bookmarkUser: string = null;

      if(!!this.queryParameters) {
         this.queryParameters.forEach((paramValue, paramKey) => {
            if(paramKey == "bookmarkName") {
               bookmarkName = paramValue[0];
            }
            else if(paramKey == "bookmarkUser") {
               bookmarkUser = paramValue[0];
            }
         });
      }

      this.getBookmarks().subscribe(data => {
            this.vsBookmarkList = data;

            if(bookmarkName != null && gotoBookmark) {
               let bookmark = this.vsBookmarkList.find(
                  f => f.name == bookmarkName && (!bookmarkUser || bookmarkUser == convertToKey(f.owner))
               );

               if(bookmark != null) {
                  this.gotoBookmark(bookmark);
               }
            }
         },
         (err: any) => {
            console.error("Failed to get bookmarks: ", err);
         }
      );
   }

   private getBookmarks(): Observable<VSBookmarkInfoModel[]> {
      const modelUri: string = "../api/" + BOOKMARK_URIS["get"] + "/" +
         Tool.byteEncode(this.runtimeId);
      const params = new HttpParams().set("localTimeZone",
         Intl.DateTimeFormat().resolvedOptions().timeZone);

      return this.modelService.getModel(modelUri, params);
   }

   isBookmarkHome(name: string): boolean {
      return name == VSBookmarkInfoModel.HOME;
   }

   saveCurrentBookmarkDisabled(): boolean {
      if(!this.securityEnabled) {
         return true;
      }

      if(this.isPermissionForbidden("AllBookmark", "Bookmark")) {
         return true;
      }

      for(let bookmark of this.vsBookmarkList) {
         if(bookmark.currentBookmark) {
            return bookmark.name != VSBookmarkInfoModel.HOME &&
               convertToKey(bookmark.owner) != this.principal && bookmark.readOnly;
         }
      }

      return false;
   }

   /**
    * Save the current bookmark
    */
   async saveBookmark(): Promise<any> {
      if(this.saveCurrentBookmarkDisabled()) {
         return;
      }

      const bookmarks = await this.getBookmarks().toPromise();
      const bookmark = bookmarks.find((vsBookmark) => vsBookmark.currentBookmark);

      // Check whether user has the permission to save this bookmark. if not,
      // pop up whether user want to save as a new bookmark.
      const noPermissionMessage: string = "You do not have permission to save this bookmark. Create a new bookmark?";
      const isUserScope: boolean = !!this.assetId && this.assetId.startsWith("4^");

      if(this.isBookmarkHome(bookmark.name) && !this.isPermissionForbidden("Viewsheet_Write")
         && !isUserScope)
      {
         //pop up dialog of whether user want to save as a new bookmark
         ComponentTool.showConfirmDialog(this.modalService, "_#(js:Confirm)", noPermissionMessage)
            .then((buttonClicked) => {
               if(buttonClicked === "ok") {
                  this.addBookmark();
               }
            });
         return;
      }

      //Check whether the bookmark is already deleted by owner
      const deletedModelUri: string = "../api/" + BOOKMARK_URIS["check-deleted"] + "/" +
         Tool.byteEncode(this.runtimeId);
      const message: string = ExpandStringDirective.expandString(
         "_#(js:viewer.viewsheet.bookmark.readd)", [bookmark.name]);

      //Check whether the bookmark is changed by others before saving
      const changedModelUri: string = "../api/" + BOOKMARK_URIS["check-changed"] + "/" +
         Tool.byteEncode(this.runtimeId);
      const params = new HttpParams()
         .set("name", bookmark.name)
         .set("owner", convertToKey(bookmark.owner));

      this.waiting = true;
      this.modelService.getModel(deletedModelUri, params)
         .subscribe(
            (isDeleted: string) => {
               this.waiting = false;
               if(isDeleted === "true") {
                  //If it is deleted by owner, pop up the confirm dialog
                  ComponentTool.showConfirmDialog(this.modalService, "_#(js:Confirm)", message)
                     .then((buttonClicked) => {
                        if(buttonClicked === "ok") {
                           const event: VSEditBookmarkEvent = new VSEditBookmarkEvent();
                           event.setInstruction("save");
                           event.setBookmarkConfirmed("Readd");
                           event.setConfirmed(true);
                           event.setVSBookmarkInfoModel(bookmark);
                           event.setClientId(this.clientId);
                           this.viewsheetClient.sendEvent("/events/" + BOOKMARK_URIS["save"],
                              event);
                        }
                     });
               }
               else {
                  this.modelService.getModel(changedModelUri, params)
                     .subscribe(
                        (isChanged: string) => {
                           if(isChanged === "true") {
                              //If it is changed by others concurrently, pop up the confirm dialog
                              this.showBookmarkChangedDialog(this.modalService, bookmark.label).then((choice: string) => {
                                 if(choice === "override") {
                                    //set BookmarkConfirmed flag to "Override"
                                    const event: VSEditBookmarkEvent = new VSEditBookmarkEvent();
                                    event.setInstruction("save");
                                    event.setBookmarkConfirmed("Override");
                                    event.setConfirmed(true);
                                    event.setVSBookmarkInfoModel(bookmark);
                                    event.setClientId(this.clientId);
                                    this.viewsheetClient.sendEvent("/events/" +
                                       BOOKMARK_URIS["save"], event);
                                 }
                                 else if(choice === "refresh") {
                                    this.refreshViewsheet(true);
                                 }
                                 else if(choice === "cancel") {
                                    //cancel
                                 }
                              });
                           }
                           else {
                              //If not changed by others, just save it.
                              this.sendBookmarkEvent("save", bookmark);
                           }
                        },
                        (err: any) => {
                           console.error("Failed to check bookmark changed state: ", err);
                        }
                     );
               }
            },
            (err: any) => {
               this.waiting = false;
               console.error("Failed to check bookmark deleted state: ", err);
            }
         );
   }

   /**
    * Open a different bookmark. Prompt the user to save annotations if unsaved
    *
    * @param bookmark the bookmark to open
    * @param check whether to verify the status of the bookmark
    */
   gotoBookmark(bookmark: VSBookmarkInfoModel, check?: boolean): void {
      if(check) {
         const deletedModelUri: string = "../api/" + BOOKMARK_URIS["check-deleted"] + "/" +
            Tool.byteEncode(this.runtimeId);
         const message: string = ExpandStringDirective.expandString(
            "_#(js:viewer.viewsheet.bookmark.deletedWarning)", [bookmark.name]);
         const params = new HttpParams()
            .set("name", bookmark.name)
            .set("owner", convertToKey(bookmark.owner));

         this.modelService.getModel(deletedModelUri, params)
            .subscribe(
               (isDeleted: string) => {
                  this.waiting = false;

                  if(isDeleted === "true") {
                     ComponentTool.showMessageDialog(this.modalService, "_#(js:Warning)", message);
                  }
                  else {
                     this.doGotoBookmark(bookmark);
                  }
               },
               (err: any) => {
                  this.waiting = false;
                  console.error("Failed to check bookmark deleted state: ", err);
               }
            );
      }
      else {
         this.doGotoBookmark(bookmark);
      }
   }

   doGotoBookmark(bookmark: VSBookmarkInfoModel): void {
      this.vsBookmarkList.forEach((bm) => bm.currentBookmark = false);
      bookmark.currentBookmark = true;

      if(this.annotationChanged && !this.isDefaultOrgAsset) {
         ComponentTool.showAnnotationChangedDialog(this.modalService).then((value) => {
            if(value) {
               this.annotationChanged = false;
               this.sendBookmarkEvent("goto", bookmark);
            }
         });
      }
      else {
         this.annotationChanged = false;
         this.sendBookmarkEvent("goto", bookmark);
      }
   }

   /**
    * Set the default bookmark
    *
    * @param bookmark the bookmark to set as default
    */
   setDefaultBookmark(bookmark: VSBookmarkInfoModel): void {
      this.sendBookmarkEvent("setDefault", bookmark);

      for(let vsBookmark of this.vsBookmarkList) {
         vsBookmark.defaultBookmark = Tool.isEquals(vsBookmark, bookmark);
      }
   }

   /**
    * Remove a bookmark
    * @param bookmark the bookmark to remove
    * @param event
    */
   deleteBookmark(bookmark: VSBookmarkInfoModel, event?: MouseEvent): void {
      if(event) {
         event.stopPropagation();
      }

      const message = Tool.formatCatalogString("_#(js:viewer.viewsheet.bookmark.deleteSelected)", [bookmark.name])

      ComponentTool.showConfirmDialog(this.modalService, "_#(js:Confirm)", message,
         {"yes": "_#(js:Yes)", "no": "_#(js:No)"})
         .then((buttonClicked) => {
            if(buttonClicked === "yes") {
               this.sendBookmarkEvent("delete", bookmark);

               if(bookmark.currentBookmark) {
                  let home = this.vsBookmarkList
                     .find(bk => this.isBookmarkHome(bk.name));

                  if(home != null) {
                     this.gotoBookmark(home);
                  }
               }
            }
         });
   }

   deleteBookMarks(con: RemoveAnnotationsCondition) {
      let event: VsDeletedMatchedBookmarksEvent = {
         condition: con,
         confirmed: false,
         windowHeight: this.appSize.height,
         windowWidth: this.appSize.width,
         mobile: this.mobileDevice,
         userAgent: window.navigator.userAgent
      };

      const message = "_#(js:viewer.viewsheet.bookmark.deleteMatched)";

      ComponentTool.showConfirmDialog(this.modalService, "_#(js:Confirm)", message,
          {"yes": "_#(js:Yes)", "no": "_#(js:No)"})
          .then((buttonClicked) => {
             if(buttonClicked === "yes") {
                this.viewsheetClient.sendEvent("/events/" + BOOKMARK_URIS["delete-matched"], event);
             }
          });
   }

   editBookmark(bookmark: VSBookmarkInfoModel,
                mouseEvent?: MouseEvent): void
   {
      if(mouseEvent) {
         mouseEvent.stopPropagation();
      }

      this.propertyBookmark = Tool.clone(bookmark);
      const options: SlideOutOptions = {backdrop: "static", popup: true};
      this.dialogService.open(this.bookmarkDialog, options).result.then(
         (result: VSBookmarkInfoModel) => {
            let event: VSEditBookmarkEvent = new VSEditBookmarkEvent();
            event.setInstruction("edit");
            event.setVSBookmarkInfoModel(result);
            event.setOldName(bookmark.name);
            this.viewsheetClient.sendEvent("/events/" + BOOKMARK_URIS["edit"], event);
         },
         () => {
            // cancel
         }
      );
   }

   addBookmark(): void {
      if(this.isAddBookmarkDisabled()) {
         return;
      }

      let date: Date = new Date();
      let dateName: string = date.getFullYear() + "-" +
         ("00" + (date.getMonth() + 1)).slice(-2) + "-" +
         ("00" + date.getDate()).slice(-2) +
         " " +
         ("00" + date.getHours()).slice(-2) + ":" +
         ("00" + date.getMinutes()).slice(-2) + ":" +
         ("00" + date.getSeconds()).slice(-2);

      let idx = this.principal.lastIndexOf(KEY_DELIMITER);
      let ownerName = idx != -1 ? this.principal.substring(0, idx) : this.principal;
      let orgId = idx != -1 ? this.principal.substring(idx + 3) : null;

      this.propertyBookmark = <VSBookmarkInfoModel>{
         name: dateName,
         owner: {
            name: ownerName,
            orgID: orgId
         },
         readOnly: false,
         type: VSBookmarkType.PRIVATE
      };

      const options: SlideOutOptions = {backdrop: "static", popup: true};
      this.dialogService.open(this.bookmarkDialog, options).result.then(
         (result: VSBookmarkInfoModel) => {
            let event: VSEditBookmarkEvent = new VSEditBookmarkEvent();
            event.setInstruction("add");
            event.setVSBookmarkInfoModel(result);
            this.viewsheetClient.sendEvent("/events/" + BOOKMARK_URIS["add"], event);
         },
         () => {
            // cancel
         }
      );
   }

   toggleFullScreen(): void {
      this.applyFullScreen(!this.fullScreen);
   }

   private applyFullScreen(fullScreen: boolean): void {
      if(fullScreen) {
         this.fullScreenService.enterFullScreenForElement(this.viewContainerRef.element.nativeElement);
      }
      else {
         this.fullScreenService.exitFullScreen();
      }
   }

   private get fullScreenApplied(): boolean {
      return this.fullScreenService.fullScreenMode;
   }

   onFullScreenChange(): void {
      this.fullScreen = this.fullScreenApplied;
      this.fullScreenChange.emit(this.fullScreen);

      if(!!this.viewerRoot) {
         this.onViewerRootResize(true);
      }
   }

   private beforeDestroy(): void {
      // fix Bug #24447, don't close the viewsheet before going to binding
      // pane, else will cause sheet expired error.
      if(!this.gotoBindingPane && !this.drillDown) {
         this.closeViewsheetOnServer();
      }
   }

   /**
    * Close the viewsheet on the server side.
    */
   private closeViewsheetOnServer(): void {
      this.viewsheetClient.sendEvent(CLOSE_VIEWSHEET_SOCKET_URI);
   }

   closeViewsheet(): void {
      if(this.selectionMobileService.hasAutoMaxMode(this.vsInfo)) {
         this.selectionMobileService.resetSelectionMaxMode();

         return;
      }
      const inTabs = this.tabsHeight > 0;

      if(inTabs) {
         this.closeCurrentTab.emit("");
      }
      else {
         const params = new HttpParams().set("runtimeId", Tool.byteEncode(this.runtimeId));
         this.modelService.getModel(CHECK_FORM_TABLES, params)
            .pipe(
               mergeMap((showConfirm) => {
                  if(showConfirm) {
                     const msg: string = "_#(js:common.warnUnsavedChanges)";
                     return from(
                        ComponentTool.showConfirmDialog(this.modalService, "_#(js:Confirm)", msg)
                     ).pipe(map((result) => result === "ok"));
                  }
                  else if(this.annotationChanged) {
                     return from(ComponentTool.showAnnotationChangedDialog(this.modalService));
                  }
                  else {
                     return of(true);
                  }
               })
            )
            .subscribe((close: boolean) => {
               if(close) {
                  this.closeViewsheetOnServer();
                  this.onAnnotationChanged.emit(false);
                  this.closeClicked.emit(true);
               }
            });
      }
   }

   onOpenChartFormatPane(model: VSChartModel): void {
      this.selectObjectModel = model;
      this.updateData("getCurrentFormat");
      this.openFormatPane = true;
   }

   openConditionDialog(model: BaseTableModel): void {
      const handler = this.createTableActionHandler(model);

      if(handler) {
         this.formDataService.isFormTableChanged(this.viewsheetClient.runtimeId, model.absoluteName)
            .then((confirmed: boolean) => {
               if(confirmed) {
                  handler.showConditionDialog(
                     this.viewsheetClient.runtimeId, model.absoluteName,
                     VSUtil.getVariableList(this.vsObjects, model.absoluteName));
               }
            });
      }
   }

   openHighlightDialog(model: BaseTableModel): void {
      const handler = this.createTableActionHandler(model);

      if(handler) {
         handler.showHighlightDialog(
            this.viewsheetClient.runtimeId, model.absoluteName, model.firstSelectedRow,
            model.firstSelectedColumn, VSUtil.getVariableList(this.vsObjects, model.absoluteName));
      }
   }

   private createTableActionHandler(model: BaseTableModel): ActionHandler {
      switch(model.objectType) {
      case "VSTable":
         return new TableActionHandler(
            this.modelService, this.viewsheetClient, this.dialogService, this.contextProvider);
      case "VSCrosstab":
         return new CrosstabActionHandler(
            this.modelService, this.viewsheetClient, this.dialogService, this.contextProvider);
      case "VSCalcTable":
         return new CalcTableActionHandler(
            this.modelService, this.viewsheetClient, this.dialogService, this.contextProvider);
      }

      return null;
   }

   //set current select area formatInfoModel
   private processSetCurrentFormatCommand(command: SetCurrentFormatCommand): void {
      this.currentFormat = command.model;
      this.origFormat = Tool.clone(command.model);
   }

   // noinspection JSUnusedGlobalSymbols
   processOpenAnnotationFormatDialogCommand(command: OpenAnnotationFormatDialogCommand): void {
      const dialog = ComponentTool.showDialog(this.modalService, AnnotationFormatDialog,
         (model: AnnotationFormatDialogModel) => {
            const event = new UpdateAnnotationFormatEvent(command.assemblyName, model);
            this.viewsheetClient.sendEvent(UPDATE_ANNOTATION_FORMAT_URI, event);
         });

      dialog.model = command.formatDialogModel;
      dialog.annotationType = command.annotationType;
      dialog.forChart = command.forChart;
   }

   // noinspection JSUnusedGlobalSymbols
   /**
    * Sets the runtime identifier of the viewsheet instance.
    *
    * @param command the command object containing the identifier.
    */
   processSetRuntimeIdCommand(command: SetRuntimeIdCommand): void {
      if(this.assetId) {
         this.composerRecentService.addRecentlyViewed(createAssetEntry(this.assetId));
      }

      this.viewsheetClient.runtimeId = command.runtimeId;
      this.runtimeId = command.runtimeId;
      this.runtimeIdChange.emit(command.runtimeId);
      command.permissions = command.permissions || [];
      command.permissions.push("Toolbar");
      this.processSetPermissionsCommand({permissions: command.permissions});
      this.showBookmarks();
   }

   // noinspection JSUnusedGlobalSymbols
   /**
    * Receive parameter prompts.
    * @param {CollectParametersCommand} command
    */
   processCollectParametersCommand(command: CollectParametersCommand): void {
      let vars: VariableInfo[] = [];
      let disVars: VariableInfo[] = [];

      command.variables.forEach((variable: VariableInfo) => {
         let index: number = command.disabledVariables.indexOf(variable.name);

         if(index == -1) {
            vars.push(variable);
         }
         else {
            variable.values = [];
            disVars.push(variable);
         }
      });

      if(!command.disableParameterSheet && vars.length > 0) {
         this.enterParameters(vars, disVars, command.isOpenSheet);
      }
   }

   processUpdateSharedFiltersCommand(command: UpdateSharedFiltersCommand): void {
      if(this.contextProvider.viewer || this.contextProvider.preview) {
         this.refreshViewsheet();
      }
   }

   processDelayVisibilityCommand(command: DelayVisibilityCommand): void {
      timer(command.delay).subscribe(() => {
         const event: DelayVisibilityEvent = { assemblies: command.assemblies };
         this.viewsheetClient.sendEvent("/events/vs/showDelayedVisibility", event);
      });
   }

   /**
    * Adds or updates an assembly object.
    *
    * @param command the command.
    */
   processAddVSObjectCommand(command: AddVSObjectCommand): void {
      let updated: boolean = false;

      for(let i = 0; i < this.vsObjects.length; i++) {
         if(this.vsObjects[i].absoluteName == command.name) {
            // if the existing model is newer than the one being processed, ignore the later version. (63107)
            if(command.model.genTime < this.vsObjects[i].genTime) {
               return;
            }

            updated = true;
            this.formDataService.replaceObject(Tool.clone(this.vsObjects[i]), command.model);
            this.vsObjects[i] = VSUtil.replaceObject(Tool.clone(this.vsObjects[i]), command.model);
            this.vsObjectActions[i] = this.actionFactory.createActions(this.vsObjects[i]);

            if(this.selectedActions &&
               this.selectedActions.getModel().absoluteName == command.name)
            {
               this.selectedActions = this.vsObjectActions[i];
               this.addMobileActionSubsciption();
            }
         }
         else {
            // sheetMaxMode is global so should apply it to all
            this.vsObjects[i].sheetMaxMode = command.model.sheetMaxMode;
         }
      }

      // sheetMaxMode is global so should apply it to all
      this.vsObjects.forEach(obj => obj.sheetMaxMode = command.model.sheetMaxMode);

      if(!updated) {
         if(command.model.objectType === "VSGroupContainer") {
            this.vsObjects.unshift(command.model);
            this.vsObjectActions.unshift(this.actionFactory.createActions(command.model));
         }
         else {
            this.vsObjects.push(command.model);
            this.vsObjectActions.push(this.actionFactory.createActions(command.model));
         }

         this.formDataService.addObject(command.model);
      }

      this.dataTipService.registerDataTip(command.model.dataTip, command.name);
      this.registerDataTipVisible(command.model.dataTip);
      this.popComponentService.registerPopComponent(
         command.model.popComponent, command.name,
         command.model.objectFormat.top, command.model.objectFormat.left,
         command.model.objectFormat.width, command.model.objectFormat.height,
         command.model.absoluteName, command.model, command.model.container);
      this.popComponentService.setPopLocation((command.model as VSObjectModel).popLocation);
      this.registerPopCompVisible(command.model.popComponent);
      this.calculateAllAssemblyBounds();

      // data tip inside tab is problematic
      this.vsObjects
         .filter(obj => obj.containerType == "VSTab")
         .forEach(obj => this.dataTipService.clearDataTipChild(obj.absoluteName));

      // Bug #56350, for non-period calendars with no range set, refresh the range from the data
      VSUtil.refreshCalendarRanges(command.model, this.runtimeId, this.viewsheetClient);
   }

   // noinspection JSUnusedGlobalSymbols
   processRefreshVSObjectCommand(command: RefreshVSObjectCommand): void {
      for(let i = 0; i < this.vsObjects.length; i++) {
         if(this.vsObjects[i].absoluteName === command.info.absoluteName) {
            this.formDataService.replaceObject(Tool.clone(this.vsObjects[i]), command.info);
            this.vsObjects[i] = VSUtil.replaceObject(Tool.clone(this.vsObjects[i]), command.info);
            this.vsObjectActions[i] = this.actionFactory.createActions(this.vsObjects[i]);
            this.calculateAllAssemblyBounds();

            if(this.selectedActions &&
               this.selectedActions.getModel().absoluteName == this.vsObjects[i].absoluteName)
            {
               this.selectedActions = this.vsObjectActions[i];
               this.addMobileActionSubsciption();
            }

            break;
         }
      }
   }

   // noinspection JSUnusedGlobalSymbols
   /**
    * Configures the layout grid.
    *
    * @param command the command.
    */
   processInitGridCommand(command: InitGridCommand): void {
      this.toolbarVisible = command.toolbarVisible;
      this.stompClientService.reloadOnFailure = command.wallboard;
      this.wallboard = !!command.wallboard;
      this.hyperlinkService.singleClick = command.singleClick;

      if(command.entry && command.entry.alias) {
         if(!this.embed) {
            this.titleService.setTitle(command.entry.alias);
         }

         this.viewsheetName = command.entry.alias.replace(/^.+\/([^/]+)$/, "$1");
      }

      // Bug #28945, allow vs to linger for 30s so a newly opened vs can get the shared filters
      if(this.viewsheetClient && command.hasSharedFilters) {
         this.viewsheetClient.destroyDelayTime = 30000;
      }
   }

   // noinspection JSUnusedGlobalSymbols
   /**
    * Sets the permissions for the current user on the viewsheet.
    *
    * @param command the command.
    */
   processSetPermissionsCommand(command: SetPermissionsCommand): void {
      this.toolbarPermissions = command.permissions || [];
      this.toolbarVisible = this.toolbarPermissions.indexOf("Toolbar") < 0;
      this.hyperlinkService.portalRepositoryPermission =
         this.toolbarPermissions.indexOf("PortalRepository") < 0;
      this.profilingVisible = !!command.permissions && command.permissions.indexOf("Profiling") > 0;
   }

   processSetExportTypesCommand(command: SetExportTypesCommand): void {
      this.exportTypes = [];

      const asset: AssetEntry = createAssetEntry(this.assetId);
      let orgID = asset.organization;

      for(let i = 0; i < command.exportTypes.length; i++) {
         if(!(orgID == this.currOrgID) && command.exportTypes[i] == "Snapshot") {
            //skip export as snapshot when vs does not match current org, globally visible
            continue;
         }

         this.exportTypes.push({label: command.exportLabels[i], value: command.exportTypes[i]});
      }
   }

   // noinspection JSUnusedGlobalSymbols
   /**
    * Processes a show message command.
    *
    * @param command the command to process.
    */
   processMessageCommand(command: MessageCommand): void {
      if(this.preview) {
         this.onMessageCommand.emit(command);
      }

      if(!command.message) {
         // close progress dialog when mv data is created.
         this.closeProgressSubject.next(null);
         console.warn("clear loading should be replaced with a dedicated command");
         return;
      }

      if(Tool.shouldIgnoreMessage(this.textLimitConfirmed, this.columnLimitConfirmed,
         command.message))
      {
         return;
      }

      this.textLimitConfirmed = Tool.getTextLimit(this.textLimitConfirmed, command.message);
      this.columnLimitConfirmed = Tool.getColumnLimit(this.columnLimitConfirmed, command.message);
      command.message = Tool.getLimitedMessage(command.message);

      let title = "";
      let isConfirm: boolean;
      let isOverride: boolean;

      switch(command.type) {
         case "OK":
            this.notifications.success(command.message);
            return;
         case "TRACE":
         case "DEBUG":
         case "INFO":
            this.notifications.info(command.message);
            return;
         case "CONFIRM":
            title = "_#(js:Confirm)";
            isConfirm = true;
            break;
         case "PROGRESS":
            this.processProgress(command);
            return;
         case "OVERRIDE":
            title = ComponentTool.getDialogTitle(command.type);
            isOverride = true;
            break;
         default:
            title = ComponentTool.getDialogTitle(command.type);
      }

      if(isConfirm) {
         const buttons = {"ok": "_#(js:Yes)", "cancel": "_#(js:No)"};
         ComponentTool.showConfirmDialog(this.modalService, title, command.message, buttons)
            .then((result: string) => {
               if(result === "ok") {
                  // process confirm
                  for(let key in command.events) {
                     if(command.events.hasOwnProperty(key)) {
                        let evt: any = command.events[key];
                        evt.confirmed = true;
                        this.viewsheetClient.sendEvent(key, evt);
                     }
                  }
               }
               else if(result === "cancel") {
                  for(let key in command.events) {
                     if(command.events.hasOwnProperty(key)) {
                        let evt: any = command.events[key];
                        if(evt.confirmEvent) {
                           evt.confirmed = false;
                           this.viewsheetClient.sendEvent(key, evt);
                        }
                     }
                  }
               }
            });
      }
      else if(isOverride) {
         ComponentTool.showMessageDialog(this.modalService, title, command.message,
            {"yes": "_#(js:Yes)", "no": "_#(js:No)"});
      }
      else {
         ComponentTool.showMessageDialog(this.modalService, title, command.message)
            .then(() => {
            })
            .catch((error: any) => {
               console.error("Failed to show message dialog: ", error);
            });
      }
   }

   processSetComposedDashboardCommand(command: SetComposedDashboardCommand): void {
      this.composedDashboard = true;
   }

   protected processProgress(command: MessageCommand): void {
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
         this.showProgressDialog(command, "_#(js:Loading)", {"cancel": "_#(js:Cancel)"});
      }
   }

   private showProgressDialog(command: MessageCommand, title: string,
                              buttonOptions: { [key: string]: string }): void
   {
      ComponentTool.showMessageDialog(this.modalService, title, command.message,
         buttonOptions, {backdrop: "static"}, this.closeProgressSubject)
         .then((btn: any) => {
            for(let key in command.events) {
               if(command.events.hasOwnProperty(key)) {
                  let evt: any = Tool.clone(command.events[key]);

                  if(btn == "background") {
                     evt.background = true;
                     this.viewsheetLoading = false;
                     this.assetLoadingService.setLoading(this.inDashboard ?
                        this.dashboardName : this.assetId, false);
                     this.loadingStateChanged(false);
                  }

                  evt.confirmed = btn == "cancel";
                  this.viewsheetClient.sendEvent(key, evt);

                  if(btn == "background") {
                     evt.waitFor = true;
                     this.viewsheetClient.sendEvent(key, evt);
                  }
               }
            }
         });
   }

   // noinspection JSUnusedGlobalSymbols
   /**
    * Used to update undo/redo state of vs.
    * @param {UpdateUndoStateCommand} command
    */
   processUpdateUndoStateCommand(command: UpdateUndoStateCommand): void {
      this.undoEnabled = command.current > 0;
      this.redoEnabled = command.current < command.points - 1;
   }

   // noinspection JSUnusedGlobalSymbols
   /**
    * If the annotation state has changed we need to warn the user before the viewsheet
    * is closed so they can save it in a bookmark
    */
   processAnnotationChangedCommand(command: AnnotationChangedCommand): void {
      this.annotationChanged = command.isChanged && !this.isDefaultOrgAsset;
      this.onAnnotationChanged.emit(this.annotationChanged);

      if(GuiTool.isIFrame()) {
         window.viewsheetModified = this.annotationChanged;
      }
   }

   protected isInZone(messageType: string): boolean {
      return messageType != "ClearLoadingCommand" && messageType != "ShowLoadingMaskCommand";
   }

   // noinspection JSUnusedGlobalSymbols, JSUnusedLocalSymbols
   /**
    * Clear the viewsheet loading icon
    */
   processClearLoadingCommand(command: ClearLoadingCommand): void {
      this.loadingEventCount = Math.max(0, this.loadingEventCount - command.count);

      // clear loading if there are no more loading events waiting for clear command
      if(this.loadingEventCount == 0) {
         // viewsheet has gone through initial loading if a clear loading command is processed
         this.initing = false;

         if(this.viewsheetLoading) {
            this.viewsheetLoading = false;
            this.preparingData = false;
            this.assetLoadingService.setLoading(this.inDashboard ?
               this.dashboardName : this.assetId, false);
            this.loadingStateChanged(false);
            this.changeDetectorRef.detectChanges();
         }
      }
   }

   processShowLoadingMaskCommand(command: ShowLoadingMaskCommand): void {
      // don't increment the second command that turns on preparing data label
      if(!command.preparingData) {
         this.loadingEventCount++;
      }

      this.preparingData = command.preparingData;
      this.viewsheetLoading = true;
      this.assetLoadingService.setLoading(this.inDashboard ?
         this.dashboardName : this.assetId, true);
      this.loadingStateChanged(true);
      this.changeDetectorRef.detectChanges();
   }

   /**
    * Needs to be implemented in the viewer so that annotation assemblies can be removed
    * when changing bookmarks
    *
    * @param command the command containing the name of the objects to remove
    */
   processRemoveVSObjectCommand(command: RemoveVSObjectCommand): void {
      const index = this.vsObjects
         .map((object) => object.absoluteName)
         .indexOf(command.name);

      if(index > -1) {
         this.formDataService.removeObject(this.vsObjects[index]);
         this.vsObjects.splice(index, 1);
         this.vsObjectActions.splice(index, 1);
         this.calculateAllAssemblyBounds();
         this.selectedAssemblies = this.selectedAssemblies
            ? this.selectedAssemblies.filter(n => n != index) : null;

         if(this.selectedActions &&
            this.selectedActions.getModel().absoluteName == command.name)
         {
            this.selectedActions = null;
         }
      }
   }

   // noinspection JSUnusedGlobalSymbols
   /**
    * Set info for the viewsheet
    *
    * @param {SetViewsheetInfoCommand} command
    */
   processSetViewsheetInfoCommand(command: SetViewsheetInfoCommand): void {
      this.annotated = command.annotated;
      this.showAnnotations = command.annotation;
      this.isMetadata = command.info["isMetadata"];
      this.scaleToScreen = command.info["scaleToScreen"];
      this.viewsheetBackground = command.info["viewsheetBackground"];
      this.name = command.assemblyInfo["name"];
      this.accessible = command.info["accessible"];
      this.fitToWidth = command.info["fitToWidth"];
      this.balancePadding = command.info["balancePadding"];
      this.virtualScroll = command.info["virtualScroll"];

      if(command.info["hasWatermark"]) {
         const imageUrl = "url('assets/elastic_watermark.png')";
         this.backgroundImage = this.sanitizer.bypassSecurityTrustStyle(imageUrl);
         this.backgroundImageRepeat = "repeat";
      }

      if(this.vsInfo.linkUri != command.linkUri) {
         this.vsInfo = new ViewsheetInfo(this.vsObjects,
            command.linkUri, false, this.runtimeId, this.getOrgId());
      }

      // reset server update interval if any values are changed
      if(this.updateEnabled != command.info["updateEnabled"] ||
         this.touchInterval != command.info["touchInterval"])
      {
         this.updateEnabled = command.info["updateEnabled"];
         this.touchInterval = command.info["touchInterval"];
         this.setServerUpdateInterval();
      }

      if(this.accessible && !this.inPortal) {
         const body: HTMLElement = this.document.body;
         body.classList.add("accessible");
      }

      this.pageTabService.updateTabLabel(this.assetId, this.name);
   }

   processClearScrollCommand(command: ClearScrollCommand): void {
      this._scrollLeft = 0;
      this._scrollTop = 0;
   }

   processUpdateZIndexesCommand(command: UpdateZIndexesCommand): void {
      for(let i = 0; i < command.assemblies.length; i++) {
         const target = command.assemblies[i];
         const idx = this.vsObjects.findIndex((obj) => obj.absoluteName === target);

         if(idx >= 0) {
            this.vsObjects[idx].objectFormat.zIndex = command.zIndexes[i];
            this.vsObjectActions[idx] = this.actionFactory.createActions(this.vsObjects[idx]);
         }
      }
   }

   private processExpiredSheetCommand(command: ExpiredSheetCommand) {
      this.expired = true;
      this.subscriptions.unsubscribe();
   }

   /**
    * Open the dropdown menu with given actions if the background is right clicked
    *
    * @param payload event payload that carries the source contextmenu event and the
    *                list of assembly actions to use for the context menu. Also has a
    *                actionsProvider property that returns the correct set of actions for
    *                the context menu (whether it be the toolbar actions, menu actions,
    *                or click actions).
    */
   public onOpenContextMenu(payload: {
      actions: AbstractVSActions<any>,
      event: MouseEvent
   }): void
   {
      const event = payload.event;
      let actions: AssemblyActionGroup[];

      if(payload.actions) {
         const sub = payload.actions.onAssemblyActionEvent.subscribe((evt) => {
            switch(evt.id) {
            case "viewer annotation":
               this.showAnnotationDialog(event);
               break;
            }
         });

         if(event.type === "click") {
            actions = [new AssemblyActionGroup([payload.actions.clickAction])];
         }
         else {
            actions = payload.actions.menuActions;
         }

         const dropdown: DropdownRef = this.showContextMenu(actions, event);
         this.miniToolbarService.hiddenFreeze(payload.actions?.getModel()?.absoluteName);

         const sub2 = dropdown.closeEvent.subscribe(() => {
            sub.unsubscribe();
            sub2.unsubscribe();
            this.miniToolbarService.hiddenUnfreeze(payload.actions?.getModel()?.absoluteName);
         });
      }
   }

   public showViewsheetContextMenu(event: MouseEvent): void {
      if(event.target == this.viewerToolbar.nativeElement ||
         event.target == this.viewerRoot.nativeElement ||
         event.target == this.scaleContainer.nativeElement)
      {
         const actions = [].concat(new AssemblyActionGroup([
            {
               id: () => "viewer refresh",
               label: () => "_#(js:Refresh)",
               icon: () => "refresh-icon",
               enabled: () => true,
               visible: () => true,
               action: () => this.refreshViewsheet()
            },
            {
               id: () => "viewer zoom-in",
               label: () => "_#(js:Zoom In)",
               icon: () => "zoom-in-icon",
               enabled: () => Number(this.scale.toFixed(2)) >= 0.2 && Number(this.scale.toFixed(2)) < 2.0,
               visible: () => true,
               action: () => this.zoom(false)
            },
            {
               id: () => "viewer zoom-out",
               label: () => "_#(js:Zoom Out)",
               icon: () => "zoom-out-icon",
               enabled: () => Number(this.scale.toFixed(2)) > 0.2 && Number(this.scale.toFixed(2)) <= 2.0,
               visible: () => true,
               action: () => this.zoom(true)
            },
            {
               id: () => "viewer annotation",
               label: () => "_#(js:Add Annotation)",
               icon: () => "annotation-icon",
               enabled: () => true,
               visible: () => this.securityEnabled,
               action: (sourceEvent) => this.showAnnotationDialog(sourceEvent),
            }
         ]));

         this.showContextMenu(actions, event);
      }
   }

   showAnnotationDialog(sourceEvent: MouseEvent) {
      const viewerRoot = this.viewerRoot.nativeElement;
      const backgroundRect = viewerRoot.getBoundingClientRect();

      this.subscriptions.add(
         this.richTextService.showAnnotationDialog((content) => {
            const x = sourceEvent.clientX - backgroundRect.left + viewerRoot.scrollLeft;
            const y = sourceEvent.clientY - backgroundRect.top + viewerRoot.scrollTop;
            this.addAnnotation(content, x, y);
         }).subscribe()
      );
   }

   addMobileActionSubsciption() {
      if(this.selectedActions && this.latestMobileMouseEvent) {
         if(!!this.mobileActionSubscription) {
            this.mobileActionSubscription.unsubscribe();
         }

         this.mobileActionSubscription = this.selectedActions
            .onAssemblyActionEvent.subscribe((evt) =>
            {
               switch(evt.id) {
               case "viewer annotation":
                  this.showAnnotationDialog(this.latestMobileMouseEvent);
                  break;
            }
         });
      }
   }

   showScrollButton(event: any) {
      if(!event) {
         return;
      }

      let x = event.clientX;
      let y = event.clientY;

      if(!this.pagingControlService.xDirectionInView(event)) {
         x = window.innerWidth - this.buttonSize;
      }

      if(!this.pagingControlService.yDirectionInView(event)) {
         y = window.innerHeight - this.buttonSize;
      }

      if(this.pagingControlService.hasDropdownOrTooltip) {
         y -= this.buttonSize;
      }

      this.pageControlStartX = x;
      this.pageControlStartY = y;
   }

   /**
    * Check if the request permission is forbidden
    *
    * @param {string[]} checkPermissions the permission to check
    *
    * @returns {boolean} true if it is forbidden, false otherwise
    */
   isPermissionForbidden(...checkPermissions: string[]): boolean {
      if(checkPermissions.some(p => p == "ExportVS") &&
         this.exportTypes && this.exportTypes.length == 0)
      {
         return true;
      }

      if(checkPermissions.some(p => p == "Schedule") && this.exportTypes &&
         (this.exportTypes.length == 0 || this.exportTypes.length == 1 &&
            this.exportTypes[0].value.toLowerCase() == "snapshot"))
      {
         return true;
      }

      return this.toolbarPermissions && checkPermissions.some((p) => {
         return this.toolbarPermissions.indexOf(p) > -1;
      });
   }

   /**
    * Send a viewsheet annotation to the server with the given content and position
    *
    * @param content the html content for the annotation
    * @param x       the offset from the left of the viewsheet
    * @param y       the offset from the top of the viewsheet
    */
   private addAnnotation(content: string, x: number, y: number): void {
      const event = new AddAnnotationEvent(content, x, y);
      this.viewsheetClient.sendEvent(ADD_VS_ANNOTATION_URI, event);
   }

   /**
    * Iterate through the list of selected objects and remove all the selected annotations.
    */
   public removeAnnotations(): void {
      const event = RemoveAnnotationEvent.create(this.vsObjects, this.selectedAssemblies);

      if(event) {
         if(this.vsObjects && this.selectedAssemblies) {
            for(let index of this.selectedAssemblies) {
               let model = this.vsObjects[index];
               model.selectedAnnotations = [];
            }
         }

         this.viewsheetClient.sendEvent(RemoveAnnotationEvent.REMOVE_ANNOTATION_URI, event);
      }
   }

   /**
    * Interface with the dropdown service
    *
    * @param {AssemblyActionGroup[]} actions
    * @param {MouseEvent} event
    */
   private showContextMenu(actions: AssemblyActionGroup[], event: MouseEvent): DropdownRef {
      let options: DropdownOptions = {
         position: {x: event.clientX, y: event.clientY},
         contextmenu: true
      };

      let dropdownRef = this.dropdownService.open(ActionsContextmenuComponent, options);
      let contextmenu: ActionsContextmenuComponent = dropdownRef.componentInstance;
      contextmenu.sourceEvent = event;
      contextmenu.actions = actions;
      event.preventDefault();
      return dropdownRef;
   }

   private openViewsheet(runtimeId: string = null): void {
      // wait a tick to ensure that the parent divs are properly sized
      const waitResize = this.scaleToScreen && this.inPortal && !this.fitToWidth ? 100 : 0;
      setTimeout(() => this.openViewsheet0(runtimeId), waitResize);
   }

   /**
    * Requests that the server open the viewsheet.
    */
   private openViewsheet0(runtimeId: string = null): void {
      this.setAppSize();
      let event: OpenViewsheetEvent = new OpenViewsheetEvent(
         this.assetId, this.appSize.width, this.appSize.height, this.mobileDevice,
         window.navigator.userAgent);
      event.fullScreenId = this.fullscreenId;
      event.runtimeViewsheetId = runtimeId;
      event.embed = this.contextProvider.embed;

      if(!!this.fullscreenId && !this.viewsheetClient.runtimeId) {
         this.viewsheetClient.runtimeId = this.fullscreenId;
      }
      else if(!!runtimeId && !this.viewsheetClient.runtimeId) {
         this.viewsheetClient.runtimeId = runtimeId;
      }

      if(this.queryParameters) {
         this.queryParameters.forEach((paramValue, paramKey) => {
            event.checkQueryParameters(paramValue, paramKey);
         });

         if(this.queryParameters.get("drillfrom") && this.queryParameters.get("drillfrom").length) {
            event.drillFrom = this.queryParameters.get("drillfrom")[0];
         }

         if(this.queryParameters.get("hideLoadingDisplay") && this.queryParameters.get("hideLoadingDisplay").length) {
            this.hideLoadingDisplay = this.queryParameters.get("hideLoadingDisplay")[0] === "true";
         }
      }

      if(globalPostParams) {
         for(let name in globalPostParams) {
            if(globalPostParams.hasOwnProperty(name)) {
               event.checkQueryParameters(globalPostParams[name], name);
            }
         }

         globalPostParams = null;
      }

      this.drillFrom = event.drillFrom;
      this.viewsheetClient.sendEvent(OPEN_VS_URI, event);
   }

   /**
    * Requests that the server open the preview viewsheet.
    */
   private openPreviewViewsheet(): void {
      this.setAppSize();
      const scaleSize: Dimension = this.getScaleSize();
      let event: OpenPreviewViewsheetEvent = new OpenPreviewViewsheetEvent(
         null, scaleSize.width, scaleSize.height, this.mobileDevice, window.navigator.userAgent);
      event.runtimeViewsheetId = this.previewBaseId;
      event.layoutName = this.layoutName;
      this.viewsheetClient.sendEvent(OPEN_VS_PREVIEW_URI, event);
   }

   /**
    * Requests that the server stop the loading of the viewsheet.
    */
   cancelViewsheetLoading(): void {
      this.cancelled = true;
      this.assetLoadingService.setLoading(this.inDashboard ?
         this.dashboardName : this.assetId, false);
      this.changeDetectorRef.detectChanges();
      let event: CancelViewsheetLoadingEvent =
         new CancelViewsheetLoadingEvent(this.runtimeId, this.preview, this.initing);
      this.viewsheetClient.sendEvent(CANCEL_VS_URI, event);
   }

   /**
    * Send a bookmark event to the server
    *
    * @param instruction the type of event to send
    * @param bookmark the bookmark for the event
    */
   private sendBookmarkEvent(instruction: "save" | "goto" | "setDefault" | "delete",
                             bookmark: VSBookmarkInfoModel): void
   {
      let event: VSEditBookmarkEvent = new VSEditBookmarkEvent();
      event.setInstruction(instruction);
      event.setVSBookmarkInfoModel(bookmark);
      event.setClientId(this.clientId);
      event.setWindowWidth(this.appSize.width);
      event.setWindowHeight(this.appSize.height);
      event.setMobile(this.mobileDevice);
      event.setUserAgent(window.navigator.userAgent);
      this.viewsheetClient.sendEvent("/events/" + BOOKMARK_URIS[instruction], event);
   }

   private enterParameters(variables: VariableInfo[], disabledVariables: VariableInfo[], openVS: boolean = false) {
      this.variableInputDialogModel = <VariableInputDialogModel>{
         varInfos: variables
      };

      const options: SlideOutOptions = {backdrop: "static", popup: true};
      this.dialogService.open(this.variableInputDialog, options).result
         .then(
            (model: VariableInputDialogModel) => {
               this.setAppSize();
               const vars: VariableInfo[] = model.varInfos.concat(disabledVariables);
               let event: CollectParametersOverEvent = new CollectParametersOverEvent(vars, false, openVS);
               event.width = this.appSize.width;
               event.height = this.appSize.height;
               this.viewsheetClient.sendEvent(COLLECT_PARAMS_URI, event);
            },
            () => {
               // Error
            }
         );
   }

   private showBookmarkChangedDialog(modalService: NgbModal, bookmarkName: string): Promise<string>
   {
      const message: string = ExpandStringDirective.expandString(
         "_#(js:viewer.viewsheet.bookmark.updated)", [bookmarkName]);
      return ComponentTool.showConfirmDialog(modalService, "_#(js:Confirm)", message, {
         "override": "_#(js:Override)",
         "refresh": "_#(js:Refresh)",
         "cancel": "_#(js:Cancel)"
      });
   }

   /**
    * For layout preview, the dataTip component should be added to layout pane explicitly.
    * Otherwise, the dataTip view is not enabled.
    * @param {string} dataTip name
    */
   private registerDataTipVisible(dataTip: string): void {
      if(!dataTip) {
         return;
      }

      const modelUri: string = "../api/" + CHECK_ASSEMBLY_IN_LAYOUT_URI + "/" + this.layoutName
         + "/" + dataTip + "/" + Tool.byteEncode(this.runtimeId);

      this.modelService.getModel(modelUri)
         .subscribe(
            (result: DataTipInLayoutCheckResult) => {
               this.dataTipService.registerDataTipVisible(dataTip, result.isAssemblyInLayout);
            },
            (err: any) => {
               console.error("Failed to check whether dataTip assembly is in layout pane: ", err);
            }
         );
   }

   /**
    * For layout preview, the dataTip component should be added to layout pane explicitly.
    * Otherwise, the dataTip view is not enabled.
    * @param {string} popComp name
    */
   private registerPopCompVisible(popComp: string): void {
      if(!popComp) {
         return;
      }

      const modelUri: string = "../api/" + CHECK_ASSEMBLY_IN_LAYOUT_URI + "/" + this.layoutName
         + "/" + popComp + "/" + Tool.byteEncode(this.runtimeId);

      this.modelService.getModel(modelUri)
         .subscribe(
            (result: DataTipInLayoutCheckResult) => {
               this.popComponentService.registerPopComponentVisible(
                  popComp, result.isAssemblyInLayout);
            },
            (err: any) => {
               console.error("Failed to check whether dataTip assembly is in layout pane: ", err);
            }
         );
   }

   setDataTipOffsets(): { x: number, y: number, width: number, height: number, scrollLeft: number, scrollTop: number } {
      if(this.viewerOffsetFunc) {
         return this.viewerOffsetFunc();
      }

      let originRect = this.viewerRoot.nativeElement.getBoundingClientRect();
      return {
         x: originRect.left,
         y: originRect.top,
         width: originRect.width,
         height: originRect.height,
         scrollLeft: this.viewerRoot.nativeElement.scrollLeft,
         scrollTop: this.viewerRoot.nativeElement.scrollTop,
      };
   }

   getComponentModel(name: string): VSObjectModel {
      if(!this.vsInfo) {
         return null;
      }

      return this.vsInfo.vsObjects.find((obj) => obj?.absoluteName === name);
   }

   setAppSize(): void {
      this.appSize = new Dimension(this.viewerRoot.nativeElement.offsetWidth,
         this.viewerRoot.nativeElement.offsetHeight);
   }

   getScaleSize(): Dimension {
      if(this.preview && this.layoutName) {
         return VSUtil.getLayoutPreviewSize(
            this.appSize.width, this.appSize.height, this.guideType);
      }
      else {
         return this.appSize;
      }
   }

   onViewerRootResize(force: boolean = false): void {
      // set app size right away so other events that trigger AfterViewChecked
      // don't cause the callback function to delay longer
      const oldSize = this.appSize;
      this.setAppSize();

      if(!force && oldSize &&
         oldSize.width === this.appSize.width &&
         (this.fitToWidth || oldSize.height === this.appSize.height))
      {
         // no actual change
         return;
      }

      this.debounceService.debounce(this.runtimeId + "_resize", () => {
         let viewerRootWidth: number = this.viewerRoot.nativeElement.clientWidth;
         let viewerRootHeight: number = this.viewerRoot.nativeElement.clientHeight;

         // viewerRoot has 0/0 width/height when it is hidden/not active
         if(viewerRootWidth != 0 && viewerRootHeight != 0) {
            //If oldSize is 0/0, viewsheet on mobile may be rendered with desktop layout.
            //Reload the viewsheet to make sure its layout is correct.
            if(oldSize.width == 0 && oldSize.height == 0 && this.mobileDevice) {
               this.openViewsheet();
            }
            else if(!!this.runtimeId) {
               this.refreshViewsheet(false, true);
            }
         }
      }, 300, []);
   }

   getVariables(): string[] {
      return VSUtil.getVariableList(this.vsObjects, this.selectObjectModel.absoluteName);
   }

   updateData(event: string): void {
      switch(event) {
      case "getCurrentFormat":
         this.getCurrentFormat();
         break;
      case "updateFormat":
         this.updateFormat(this.currentFormat);
         break;
      case "reset":
         this.updateFormat(null);
         break;
      }
   }

   updateFormat(fmt: VSObjectFormatInfoModel) {
      if(!!fmt && !!!fmt["type"]) {
         return;
      }

      let event: FormatVSObjectEvent = VSUtil.prepareFormatEvent([this.selectObjectModel]);
      event.format = fmt;
      event.origFormat = this.origFormat;
      event.reset = !fmt;

      this.viewsheetClient.sendEvent(VS_FORMAT_URI, event);
   }

   closeFormatPane() {
      this.openFormatPane = false;
   }

   getCurrentFormat(fmt?: VSChartModel) {
      if(!this.selectObjectModel || this.selectObjectModel.objectType != "VSChart") {
         return;
      }

      this.selectObjectModel = fmt ? fmt : this.selectObjectModel;
      let object = this.selectObjectModel;
      let vsevent: GetVSObjectFormatEvent =
         new GetVSObjectFormatEvent(object.absoluteName);
      let chart: VSChartModel = <VSChartModel>object;

      if(chart.chartSelection) {
         if(chart.chartSelection && !chart.titleSelected) {
            const selection = chart.chartSelection;

            if(selection.chartObject && selection.chartObject.areaName === "plot_area" &&
               !!selection.regions && selection.regions.length > 0 &&
               (ChartTool.areaType(chart, selection.regions[0]) === "label" ||
                  ChartTool.areaType(chart, selection.regions[0]) === "vo" ||
                  ChartTool.areaType(chart, selection.regions[0]) === "text"))
            {
               vsevent.region = ChartTool.areaType(chart, selection.regions[0]);
            }
            else {
               vsevent.region = !!selection.chartObject ?
                  selection.chartObject.areaName : null;
            }

            if(vsevent.region == "legend_content") {
               vsevent.index = ChartTool.getCurrentLegendIndex(chart.chartSelection,
                  chart.legends);
            }
            else if(!!chart.chartSelection.regions
               && chart.chartSelection.regions.length > 0)
            {
               vsevent.index = chart.chartSelection.regions[0].index;
            }

            let columnName: string = null;
            let dimensionColumn: boolean = false;

            if(!!chart.chartSelection.regions
               && chart.chartSelection.regions.length > 0)
            {
               columnName = ChartTool.dimIdx(chart, chart.chartSelection.regions[0]) >= 0
                  ? ChartTool.getDim(chart, chart.chartSelection.regions[0])
                  : ChartTool.getMea(chart, chart.chartSelection.regions[0]);
               dimensionColumn = chart.chartSelection.regions[0].valIdx != null &&
                  chart.chartSelection.regions[0].valIdx != -1;
            }

            vsevent.columnName = columnName;
            vsevent.dimensionColumn = dimensionColumn;
         }
      }

      vsevent.dataPath = object.selectedRegions ? object.selectedRegions[0] : null;
      this.viewsheetClient.sendEvent(VS_GET_FORMAT_URI, vsevent);
   }

   private calculateAllAssemblyBounds() {
      const oldBounds = this.scaleToScreen && this.allAssemblyBounds ?
         Object.assign({}, this.allAssemblyBounds) : null;

      if(this.vsObjects.length === 0) {
         this.allAssemblyBounds = {top: 0, left: 0, bottom: 0, right: 0};
      }
      else {
         this.allAssemblyBounds = this.vsObjects.reduce((bounds, object) => {
            // Bug #59114, ignore the assembly if not visible, matching the logic in
            // VSEventUtil.getBoundingAssemblies
            if(!object.visible) {
               return bounds;
            }

            const {top, left, width, height} = object.objectFormat;

            if(isNaN(bounds.top) || top < bounds.top) {
               bounds.top = top;
            }

            if(isNaN(bounds.left) || left < bounds.left) {
               bounds.left = left;
            }

            bounds.bottom = Math.max(bounds.bottom, top + height);
            bounds.right = Math.max(bounds.right, left + width);
            return bounds;
         }, {top: NaN, left: NaN, bottom: 0, right: 0});
      }

      if(this.scaleToScreen) {
         if(oldBounds && (oldBounds.right > this.allAssemblyBounds.right ||
            oldBounds.bottom > this.allAssemblyBounds.bottom) &&
            (!this.appSize || oldBounds.right <= this.appSize.width &&
               oldBounds.bottom <= this.appSize.height))
         {
            this.onViewerRootResize();
         }

         this.notifyParentFrame();
      }
   }

   /**
    * feature #41209
    * if we're embedded notify the parent frame when the scrollHeight changes so they can resize
    * the iframe according to the size of the content
    */
   private notifyParentFrame(): void {
      if(window != window.parent &&
         this.viewerRoot != null &&
         this.viewerRoot.nativeElement != null)
      {
         this.debounceService.debounce(this.runtimeId + "_notify_parent_frame", () => {
            // check if the bottom tab bar is visible and if so add its height
            // to the scroll height to prevent any scrollbars on the iframe
            const pageTabBar = document.querySelector(".page-tab-bar");
            const pageTabBarHeight = !!pageTabBar ? pageTabBar.getBoundingClientRect().height : 0;

            const message = {
               scrollHeight: this.viewerRoot.nativeElement.scrollHeight + pageTabBarHeight
            };

            window.parent.postMessage(message, "*");
         }, 200);
      }
   }

   submitData() {
      this.submitClicked.next(true);
   }

   preventMouseInteractions(event: MouseEvent) {
      event.preventDefault();
      event.stopPropagation();
   }

   private processOpenComposerCommand(command: OpenComposerCommand): void {
      this.openComposer(command.vsId);
   }

   private openComposer(vsId: string, deployed: boolean = false): void {
      const composerUrl = "composer";
      // The asset ID may be partially URL encoded. Decode it and then re-encode it.
      let params: HttpParams = new HttpParams()
         .set("vsId", decodeURIComponent(vsId));

      if(deployed) {
         params = params.set("deployed", "true");
      }

      GuiTool.openBrowserTab(composerUrl, params);
   }

   draggableRestriction = (x: number, y: number, element: any) => {
      let draggableRestrictionRect: { left: number, top: number, right: number, bottom: number };
      const containerElement = this.viewerRoot.nativeElement;
      const containerRect = GuiTool.getElementRect(containerElement);
      const elementRect = GuiTool.getElementRect(element);
      let offsetX = 0;
      let offsetY = 0;

      containerElement.querySelectorAll(".vs-annotation__rectangle--selected")
         .forEach(assemblyElement => {
            const assemblyRect = GuiTool.getElementRect(assemblyElement);
            offsetX = Math.max(offsetX, elementRect.left - assemblyRect.left);
            offsetY = Math.max(offsetY, elementRect.top - assemblyRect.top);
         });

      draggableRestrictionRect = {
         top: containerRect.top + offsetY,
         left: containerRect.left + offsetX,
         bottom: containerRect.bottom,
         right: containerRect.right
      };

      if(this.scaleToScreen && elementRect) {
         draggableRestrictionRect.right -= elementRect.width;

         if(!this.fitToWidth) {
            draggableRestrictionRect.bottom -= elementRect.height;
         }
      }

      return draggableRestrictionRect;
   };

   get hasBottomPadding(): boolean {
      return !!this.allAssemblyBounds && ((this.balancePadding && this.fitToWidth) ||
         (this.balancePadding && !this.scaleToScreen));
   }

   get hasRightPadding(): boolean {
      return !!this.allAssemblyBounds && this.balancePadding && !this.scaleToScreen;
   }

   shareEmail(): void {
      const options: SlideOutOptions = {backdrop: "static", popup: true, size: "lg"};
      this.dialogService.open(this.shareEmailDialog, options).result.then(
         () => {
         },
         () => {
         }
      );
   }

   shareFacebook(): void {
      this.shareService.shareViewsheetOnFacebook(this.assetId);
   }

   shareHangouts(): void {
      const options: SlideOutOptions = {backdrop: "static", popup: true};
      this.dialogService.open(this.shareHangoutsDialog, options).result.then(
         () => {
         },
         () => {
         }
      );
   }

   shareLinkedin(): void {
      this.shareService.shareViewsheetOnLinkedIn(this.assetId);
   }

   shareSlack(): void {
      const options: SlideOutOptions = {backdrop: "static", popup: true};
      this.dialogService.open(this.shareSlackDialog, options).result.then(
         () => {
         },
         () => {
         }
      );
   }

   shareTwitter(): void {
      this.shareService.shareViewsheetOnTwitter(this.assetId, this.viewsheetName);
   }

   shareLink(): void {
      const options: SlideOutOptions = {backdrop: "static", popup: true};
      this.dialogService.open(this.shareLinkDialog, options).result.then(
         () => {
         },
         () => {
         }
      );
   }

   deleteBookmarkByCondition() {
      let options: NgbModalOptions = <NgbModalOptions> {
         backdrop: "static",
         windowClass: "remove-bookmarks-dialog",
         backdropClass: "remove-bookmarks-dialog-backdrop"
      };

      this.modalService.open(this.removeBookmarksDialog, options)
         .result.then((con: RemoveAnnotationsCondition) => {
            if(con != null) {
               this.deleteBookMarks(con);
            }
      });
   }

   changeMaxMode(maxMode: boolean) {
      this.maxMode = maxMode;
   }

   toggleDoubleCalendar(isDouble: boolean) {
      if(!isDouble) {
         this.showScroll = isDouble;
         return;
      }

      let rightObj: VSObjectModel = null;
      let rightPosition: number = 0;

      for(let vsObject of this.vsObjects) {
         if(rightObj == null) {
            rightObj = vsObject;
            rightPosition = vsObject.objectFormat.left + vsObject.objectFormat.width;
         }
         else if(rightPosition < vsObject.objectFormat.left + vsObject.objectFormat.width) {
            rightObj = vsObject;
         }
      }

      if(rightObj != null && rightObj.objectType == "VSCalendar") {
         this.showScroll = isDouble;
      }
   }

   // set scroll to top/left in max mode since maxmode div is at 0,0
   scroll(event: any) {
      if(!this.maxMode) {
         this._scrollLeft = event.target.scrollLeft;
         this._scrollTop = event.target.scrollTop;
      }

      this.updateScrollViewport();
      this.showHints();
   }

   private showHints() {
      if(this.mobileDevice) {
         this._showHints = true;
         setTimeout(() => {
            this._showHints = false;
         }, 500);
      }
   }

   get scrollLeft(): number {
      return this.maxMode ? 0 : this._scrollLeft;
   }

   get scrollTop(): number {
      return this.maxMode ? 0 : this._scrollTop;
   }

   openProfileDialog() {
      let objName: string =  this.runtimeId;

      const options: NgbModalOptions = {
         backdrop: "static",
         windowClass: "profiling-dialog"
      };

      let event = <ProfileTableDataEvent> {
         objectName: objName,
         sortValue: 0,
         sortCol: 0
      };

      let params = new HttpParams()
         .set("isViewsheet", "true")
         .set("name", objName)
         .set("timeZone", Intl.DateTimeFormat().resolvedOptions().timeZone);

      this.modelService.putModel(GET_PROFILE_TABLE_URL, event, params)
         .subscribe((data: any) => {
            let profilingDialog: ProfilingDialog = ComponentTool.showDialog(
               this.modalService, ProfilingDialog, () => {}, options);

            profilingDialog.tableData = data.body;
            profilingDialog.objName = objName;
            profilingDialog.isViewsheet = true;
         });
   }

   openViewsheetOptionDialog(): void {
      this.onOpenViewsheetOptionDialog.emit(this.getScaleSize());
   }

   hideProfilingBanner() {
      this.profilingVisible = false;
   }

   isPreviousPageVisible(): boolean {
      return !this.isPermissionForbidden("PageNavigation", "Previous");
   }

   isPreviousPageDisabled(): boolean {
      return !this.undoEnabled || this.maxMode;
   }

   isNextPageVisible(): boolean {
      return !this.isPermissionForbidden("PageNavigation", "Next");
   }

   isNextPageDisabled(): boolean {
      return !this.redoEnabled;
   }

   isEditVisible(): boolean {
      return !this.isPermissionForbidden("Edit") && !this.mobileDevice &&
         !this.preview && !this.linkView && this.editable && !this.fullScreen;
   }

   isRefreshViewsheetVisible(): boolean {
      return !this.isPermissionForbidden("Refresh");
   }

   isEmailVisible(): boolean {
      return !this.isPermissionForbidden("Email");
   }

   isSocialSharingVisible(): boolean {
      return !this.isPermissionForbidden("Social Sharing");
   }

   isShareEmailDisabled(): boolean {
      return !this.shareConfig.emailEnabled;
   }

   isShareFacebookDisabled(): boolean {
      return !this.shareConfig.facebookEnabled;
   }

   isShareHangoutsDisabled(): boolean {
      return !this.shareConfig.googleChatEnabled;
   }

   isShareLinkedInDisabled(): boolean {
      return !this.shareConfig.linkedinEnabled;
   }

   isShareSlackDisabled(): boolean {
      return !this.shareConfig.slackEnabled;
   }

   isShareTwitterDisabled(): boolean {
      return !this.shareConfig.twitterEnabled;
   }

   isShareLinkDisabled(): boolean {
      return !this.shareConfig.linkEnabled;
   }

   isScheduleVisible(): boolean {
      return !this.isPermissionForbidden("Schedule");
   }

   isPrintViewsheetVisible(): boolean {
      return !this.isPermissionForbidden("PrintVS", "Print");
   }

   isExportVisible(): boolean {
      return !this.isPermissionForbidden("ExportVS", "Export");
   }

   isImportExcelVisible(): boolean {
      return !this.isPermissionForbidden("ImportXLS", "Import");
   }

   isZoomVisible(): boolean {
      return !(this.isMobile() || this.isPermissionForbidden("Zoom"));
   }

   private isAnnotationButtonVisible(): boolean {
      return !this.snapshot && this.annotated && !this.isPermissionForbidden("Annotation");
   }

   isHideAnnotationsVisible(): boolean {
      return this.isAnnotationButtonVisible() && this.showAnnotations &&
         !this.isPermissionForbidden("Hide Annotations");
   }

   isShowAnnotationsVisible(): boolean {
      return this.isAnnotationButtonVisible() && !this.showAnnotations &&
         !this.isPermissionForbidden("Show Annotations");
   }

   bookmarksVisible(): boolean {
      if(this.snapshot || this.isPermissionForbidden("Bookmark")) {
         return false;
      }

      return !this.isPermissionForbidden("AllBookmark") ||
         !this.isPermissionForbidden("OpenBookmark") &&
         this.vsBookmarkList.length > 1;
   }

   isAddBookmarkDisabled(): boolean {
      return !this.securityEnabled || this.isPermissionForbidden("AllBookmark");
   }

   isShareToAllDisabled(): boolean {
      return !this.securityEnabled || this.isPermissionForbidden("ShareToAll");
   }

   isSetDefaultBookmarkVisible(bookmark: VSBookmarkInfoModel): boolean {
      return !bookmark.defaultBookmark;
   }

   isSetDefaultBookmarkDisabled(): boolean {
      return this.preview || !this.securityEnabled || this.isPermissionForbidden("AllBookmark");
   }

   isEditBookmarkVisible(bookmark: VSBookmarkInfoModel): boolean {
      return !this.isBookmarkHome(bookmark.name);
   }

   isEditBookmarkDisabled(bookmark: VSBookmarkInfoModel): boolean {
      return convertToKey(bookmark.owner) !== this.principal;
   }

   isToggleFullScreenVisible(): boolean {
      return !this.isPermissionForbidden("Full Screen") &&
         !this.preview && !this.linkView && !this.mobileDevice;
   }

   isCloseViewsheetVisible(): boolean {
      return (this.inPortal && !this.inDashboard || this.preview || this.linkView) &&
         !(this.isPermissionForbidden("Close") || this.fullScreen);
   }

   private setViewerToolbarDefinitions(): void {
      if(this.shareConfig == null || !this.isIframe) {
         return;
      }

      const toolbarVisible = !this.mobileToolbarVisible;

      this.viewerToolbarMessageService.refreshButtonDefinitions([
         {
            name: "_#(js:Previous Page)",
            visible: toolbarVisible && this.isPreviousPageVisible(),
            disabled: this.isPreviousPageDisabled(),
            action: () => this.previousPage()
         },
         {
            name: "_#(js:Next Page)",
            visible: toolbarVisible && this.isNextPageVisible(),
            disabled: false,
            action: () => this.nextPage()
         },
         {
            name: "_#(js:Edit)",
            visible: toolbarVisible && this.isEditVisible(),
            disabled: false,
            action: () => this.editViewsheet()
         },
         {
            name: "_#(js:Refresh Viewsheet)",
            visible: toolbarVisible && this.isRefreshViewsheetVisible(),
            disabled: false,
            action: () => this.refreshViewsheet()
         },
         {
            name: "_#(js:Email)",
            visible: toolbarVisible && this.isEmailVisible(),
            disabled: false,
            action: () => this.emailViewsheet()
         },
         {
            name: "_#(js:Share Email)",
            visible: toolbarVisible && this.isSocialSharingVisible(),
            disabled: this.isShareEmailDisabled(),
            action: () => this.shareEmail()
         },
         {
            name: "_#(js:Share Facebook)",
            visible: toolbarVisible && this.isSocialSharingVisible(),
            disabled: this.isShareFacebookDisabled(),
            action: () => this.shareFacebook()
         },
         {
            name: "_#(js:Share Hangouts)",
            visible: toolbarVisible && this.isSocialSharingVisible(),
            disabled: this.isShareHangoutsDisabled(),
            action: () => this.shareHangouts()
         },
         {
            name: "_#(js:Share LinkedIn)",
            visible: toolbarVisible && this.isSocialSharingVisible(),
            disabled: this.isShareLinkedInDisabled(),
            action: () => this.shareLinkedin()
         },
         {
            name: "_#(js:Share Slack)",
            visible: toolbarVisible && this.isSocialSharingVisible(),
            disabled: this.isShareSlackDisabled(),
            action: () => this.shareSlack()
         },
         {
            name: "_#(js:Share Twitter)",
            visible: toolbarVisible && this.isSocialSharingVisible(),
            disabled: this.isShareTwitterDisabled(),
            action: () => this.shareTwitter()
         },
         {
            name: "_#(js:Share Link)",
            visible: toolbarVisible && this.isSocialSharingVisible(),
            disabled: this.isShareLinkDisabled(),
            action: () => this.shareLink()
         },
         {
            name: "_#(js:Schedule)",
            visible: toolbarVisible && this.isScheduleVisible(),
            disabled: false,
            action: () => this.scheduleViewsheet()
         },
         {
            name: "_#(js:Print Viewsheet)",
            visible: toolbarVisible && this.isPrintViewsheetVisible(),
            disabled: false,
            action: () => this.printViewsheet()
         },
         {
            name: "_#(js:Export)",
            visible: toolbarVisible && this.isExportVisible(),
            disabled: false,
            action: () => this.exportViewsheet()
         },
         {
            name: "_#(js:Import Excel)",
            visible: toolbarVisible && this.isImportExcelVisible(),
            disabled: false,
            action: () => this.importExcel()
         },
         {
            name: "_#(js:Zoom In)",
            visible: toolbarVisible && this.isZoomVisible(),
            disabled: this.zoomInEnabled(),
            action: () => this.zoomLayout(ZoomOptions.ZOOM_IN)
         },
         {
            name: "_#(js:Zoom Out)",
            visible: toolbarVisible && this.isZoomVisible(),
            disabled: this.zoomOutEnabled(),
            action: () => this.zoomLayout(ZoomOptions.ZOOM_OUT)
         },
         {
            name: "_#(js:Zoom 40%)",
            visible: toolbarVisible && this.isZoomVisible(),
            disabled: false,
            action: () => this.zoomLayout(ZoomOptions.ZOOM_40)
         },
         {
            name: "_#(js:Zoom 60%)",
            visible: toolbarVisible && this.isZoomVisible(),
            disabled: false,
            action: () => this.zoomLayout(ZoomOptions.ZOOM_60)
         },
         {
            name: "_#(js:Zoom 100%)",
            visible: toolbarVisible && this.isZoomVisible(),
            disabled: false,
            action: () => this.zoomLayout(ZoomOptions.ZOOM_100)
         },
         {
            name: "_#(js:Zoom 140%)",
            visible: toolbarVisible && this.isZoomVisible(),
            disabled: false,
            action: () => this.zoomLayout(ZoomOptions.ZOOM_140)
         },
         {
            name: "_#(js:Zoom 160%)",
            visible: toolbarVisible && this.isZoomVisible(),
            disabled: false,
            action: () => this.zoomLayout(ZoomOptions.ZOOM_160)
         },
         {
            name: "_#(js:Show Annotations)",
            visible: toolbarVisible && this.isShowAnnotationsVisible(),
            disabled: false,
            action: () => this.toggleAnnotations()
         },
         {
            name: "_#(js:Hide Annotations)",
            visible: toolbarVisible && this.isHideAnnotationsVisible(),
            disabled: false,
            action: () => this.toggleAnnotations()
         },
         ...this.createBookmarkButtonDefs(),
         {
            name: "_#(js:Toggle Full Screen)",
            visible: toolbarVisible && this.isToggleFullScreenVisible(),
            disabled: false,
            action: () => this.toggleFullScreen()
         },
         {
            name: "_#(js:Close Viewsheet)",
            visible: toolbarVisible && this.isCloseViewsheetVisible(),
            disabled: false,
            action: () => this.closeViewsheet()
         },
      ]);
   }

   private createBookmarkButtonDefs(): ViewerToolbarButtonDefinition[] {
      const bookmarks: ViewerToolbarButtonDefinition[] = [];

      if(!this.bookmarksVisible()) {
         return bookmarks;
      }

      const toolbarVisible = !this.mobileToolbarVisible;

      bookmarks.push({
            name: "_#(js:Save as New Bookmark)",
            visible: toolbarVisible,
            disabled: this.isAddBookmarkDisabled(),
            action: () => this.addBookmark()
         },
         {
            name: "_#(js:Save as Current Bookmark)",
            visible: toolbarVisible,
            disabled: this.saveCurrentBookmarkDisabled(),
            action: () => this.saveBookmark()
         });

      for(let bookmark of this.vsBookmarkList) {
         bookmarks.push({
               name: `_#(js:Go To Bookmark): ${bookmark.name}`,
               visible: toolbarVisible && true,
               disabled: false,
               action: () => this.gotoBookmark(bookmark)
            },
            {
               name: `_#(js:Set Default Bookmark): ${bookmark.name}`,
               visible: toolbarVisible && this.isSetDefaultBookmarkVisible(bookmark),
               disabled: this.isSetDefaultBookmarkDisabled(),
               action: () => this.setDefaultBookmark(bookmark)
            },
            {
               name: `_#(js:Edit Bookmark): ${bookmark.name}`,
               visible: toolbarVisible && this.isEditBookmarkVisible(bookmark),
               disabled: this.isEditBookmarkDisabled(bookmark),
               action: () => this.editBookmark(bookmark)
            },
            {
               name: `_#(js:Remove Bookmark): ${bookmark.name}`,
               visible: toolbarVisible && this.isEditBookmarkVisible(bookmark),
               disabled: this.isEditBookmarkDisabled(bookmark),
               action: () => this.deleteBookmark(bookmark)
            });
      }

      return bookmarks;
   }

   getReloadMessage() {
      if(this.expired) {
         return "_#(js:viewer.expiration)";
      }

      if(this.transformFinished) {
         return "_#(js:viewer.expiration.renameTransformFinished)";
      }

      if(this.editBookmarkFinished) {
         return "_#(js:viewer.expiration.editBookmarkFinished)";
      }

      return "";
   }

   getViewerRootHeight() {
      return this.preview ? "calc(100% - 70px)" : null;
   }

   setMobileToolbarActions(): void {
      if(!this.mobileDevice) {
         return;
      }

      const actions: AssemblyAction[] = [
         {
            id: () => "mobile previous page)",
            label: () => "_#(js:Previous Page)",
            icon: () => "arrow-left-circle-outline-icon",
            visible: () => this.isPreviousPageVisible(),
            enabled: () => !this.isPreviousPageDisabled(),
            action: () => this.previousPage(),
         },
         {
            id: () => "mobile next page",
            label: () => "_#(js:Next Page)",
            icon: () => "arrow-right-circle-outline-icon",
            visible: () => this.isNextPageVisible(),
            enabled: () => !this.isNextPageDisabled(),
            action: () => this.nextPage(),
         },
         {
            id: () => "mobile refresh viewsheet",
            label: () => "_#(js:Refresh Viewsheet)",
            icon: () => "refresh-icon",
            visible: () => this.isRefreshViewsheetVisible(),
            enabled: () => true,
            action: () => this.refreshViewsheet(),
         },
         {
            id: () => "mobile email",
            label: () => "_#(js:Email)",
            icon: () => "email-icon",
            visible: () => this.isEmailVisible(),
            enabled: () => true,
            action: () => this.emailViewsheet(),
         },
         {
            id: () => "mobile social sharing",
            label: () => "_#(js:Social Sharing)",
            icon: () => "share-icon",
            visible: () => this.isSocialSharingVisible(),
            enabled: () => true,
            action: () => null,
         },
         {
            id: () => "mobile schedule",
            label: () => "_#(js:Schedule)",
            icon: () => "calendar-icon",
            visible: () => this.isScheduleVisible(),
            enabled: () => true,
            action: () => this.scheduleViewsheet(),
         },
         {
            id: () => "mobile print",
            label: () => "_#(js:Print)",
            icon: () => "printer-icon",
            visible: () => this.isPrintViewsheetVisible(),
            enabled: () => true,
            action: () => this.printViewsheet(),
         },
         {
            id: () => "mobile export",
            label: () => "_#(js:Export)",
            icon: () => "export-icon",
            visible: () => this.isExportVisible(),
            enabled: () => true,
            action: () => this.exportViewsheet(),
         },
         {
            id: () => "mobile import excel",
            label: () => "_#(js:Import Excel)",
            icon: () => "upload-icon",
            visible: () => this.isImportExcelVisible(),
            enabled: () => true,
            action: () => this.importExcel(),
         },
      ];

      if(this.mobileToolbarActions.length == 0) {
         this.mobileToolbarActions.push(new AssemblyActionGroup());
         this.mobileToolbarActions[0].actions = actions;
      }
   }

   get showingActions(): AssemblyActionGroup[] {
      const actions = ToolbarActionsHandler.getShowingActions(this.mobileToolbarActions,
         this.allowedActionsNum());
      ToolbarActionsHandler.copyActions(actions, this.showing);

      return this.showing;
   }

   get moreActions(): AssemblyActionGroup[] {
      const actions = ToolbarActionsHandler.getMoreActions(this.mobileToolbarActions,
         this.allowedActionsNum());
      ToolbarActionsHandler.copyActions(actions, this.more);

      return this.more;
   }

   allowedActionsNum(): number {
      let defaultButtons = 0;

      if(this.isShowAnnotationsVisible() || this.isHideAnnotationsVisible()) {
         defaultButtons++;
      }

      if(this.bookmarksVisible()) {
         defaultButtons++;
      }

      if(this.isCloseViewsheetVisible()) {
         defaultButtons++;
      }

      return Math.floor(window.innerWidth / ToolbarActionsHandler.MOBILE_BUTTON_WIDTH) - defaultButtons;
   }

   isPageControlVisible(): boolean {
      return this.selectedAssemblies && this.selectedAssemblies.length > 0 &&
         this.usePagingControl(this.selectedAssemblies[this.selectedAssemblies.length - 1]);
   }

   usePagingControl(selectedAssembly: number): boolean {
      if(this.vsObjects.length > selectedAssembly) {
         let current: VSObjectModel = this.vsObjects[selectedAssembly];
         return ["VSChart", "VSTable", "VSCrosstab", "VSCalcTable"].includes(current.objectType);
      }

      return false;
   }

   updateScrollTop(changed: number): void {
      this.pagingControlService.scrollTopChange(changed);
   }

   updateScrollLeft(changed: number): void {
      this.pagingControlService.scrollLeftChange(changed);
   }

   get pagingControlModel(): PagingControlModel {
      return this.pagingControlService.getPagingControlModel();
   }

   private checkExportStatus(): void {
      this.http.get("../export/check/" + Tool.byteEncode(this.runtimeId))
         .subscribe(
            (data: MessageCommand) => {
               if(data.type == "OK") {
                  this.exporting = false;
               }
               else {
                  setTimeout(() => this.checkExportStatus(), 1000);
               }
            },
            (err) => {
               this.exporting = false;
            });
   }

   private getOrgId(): string {
      if(this.assetId) {
         return createAssetEntry(this.assetId).organization;
      }

      return null;
   }

   // noinspection JSUnusedGlobalSymbols
   processEmbedErrorCommand(command: EmbedErrorCommand): void {
      this.onEmbedError.emit(command.message);
   }

   private loadingStateChanged(loading: boolean) {
      if(this.globalLoadingIndicator) {
         this.onLoadingStateChanged.emit(
            {name: this.assetId, loading: loading});
      }
   }

   private handleDataTipPopComponentChanges() {
      this.subscriptions.add(this.dataTipService.dataTipChanged.subscribe(() => {
         this.onDataTipPopComponentVisible.emit(this.isDataTipOrPopComponentVisible());
      }));

      this.subscriptions.add(this.popComponentService.popComponentChanged.subscribe(() => {
         this.onDataTipPopComponentVisible.emit(this.isDataTipOrPopComponentVisible());
      }));
   }

   private isDataTipOrPopComponentVisible(): boolean {
      return (!!this.dataTipService.dataTipName &&
            this.dataTipService.isDataTipVisible(this.dataTipService.dataTipName)) ||
         (!!this.popComponentService.getPopComponent() &&
            this.popComponentService.isPopComponentVisible(this.popComponentService.getPopComponent()));
   }

   public clearDataTipPopComponents(): void {
      this.dataTipService.hideDataTip(true);
      this.popComponentService.hidePopComponent();
   }

   private updateScrollViewport(): void {
      this.debounceService.debounce(this.runtimeId + "_scrollViewport", () => {
         this.zone.run(() => this.scrollViewport = this.getScrollViewport());
      }, 50, []);
   }

   private getScrollViewport(): ScrollViewportRect {
      if(!!this.viewerRoot?.nativeElement) {
         return {
            top: this.viewerRoot.nativeElement.scrollTop,
            left: this.viewerRoot.nativeElement.scrollLeft,
            width: this.viewerRoot.nativeElement.clientWidth,
            height: this.viewerRoot.nativeElement.clientHeight
         };
      }

      return { top: 0, left: 0, width: 0, height: 0 };
   }
}
